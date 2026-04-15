package api

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgconn"
)

// Request body size cap. Anything bigger is almost certainly a bug or an
// attack — 1 MB is more than enough for any legitimate WordFlow payload.
const maxRequestBodyBytes = 1 << 20 // 1 MB

// errorResponse is the shape of every non-2xx response body. Clients can
// always `if r.status >= 400 { parse errorResponse }` — one decoder path,
// no special cases. Code is a stable machine-readable tag; Message is a
// human-readable blurb.
type errorResponse struct {
	Error   string `json:"error"`
	Message string `json:"message,omitempty"`
	// ExistingID is set on 409 conflict responses from create endpoints
	// when the natural key (normalized_word / name) is already taken.
	// Clients use it to reconcile the local id with the server's id.
	ExistingID *uuid.UUID `json:"existing_id,omitempty"`
}

// writeJSON is a tiny helper so handlers don't each repeat the
// Content-Type / WriteHeader / Encode dance. Encoding errors are
// intentionally ignored: by the time Encode fails, we've already sent
// the status code and headers, so there's nothing useful we can do.
// The slog access middleware will still show the response size.
func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// writeError is writeJSON's sibling for error responses. Always renders
// an errorResponse struct so clients see a stable shape.
func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, errorResponse{Error: code, Message: message})
}

// writeConflict is a specialised writeError for 409 natural-key
// collisions that also attaches the existing server-side id so clients
// can reconcile their local state.
func writeConflict(w http.ResponseWriter, code string, existingID uuid.UUID) {
	writeJSON(w, http.StatusConflict, errorResponse{
		Error:      code,
		ExistingID: &existingID,
	})
}

// decodeJSON reads the request body into v, enforcing a max size and
// rejecting unknown fields. Returns (err, false) if something went wrong
// and the caller should bail (having already written a 400). Returns
// (nil, true) on success.
//
// Why DisallowUnknownFields: catches client typos ("is_learnd") at the
// server boundary instead of silently ignoring them and then quietly
// not applying the update.
func decodeJSON(w http.ResponseWriter, r *http.Request, v any) bool {
	// MaxBytesReader wraps the body so Read returns an error if the
	// client sends more than the limit. Prevents unbounded allocation.
	r.Body = http.MaxBytesReader(w, r.Body, maxRequestBodyBytes)

	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		var maxErr *http.MaxBytesError
		switch {
		case errors.As(err, &maxErr):
			writeError(w, http.StatusRequestEntityTooLarge, "body_too_large",
				fmt.Sprintf("request body exceeds %d bytes", maxRequestBodyBytes))
		case errors.Is(err, io.EOF):
			writeError(w, http.StatusBadRequest, "empty_body", "request body is empty")
		default:
			writeError(w, http.StatusBadRequest, "invalid_body", err.Error())
		}
		return false
	}

	// Reject any trailing data after the main JSON object. A valid
	// request has exactly one top-level value.
	if dec.More() {
		writeError(w, http.StatusBadRequest, "invalid_body", "multiple JSON values in body")
		return false
	}
	return true
}

// parseUUIDParam pulls a chi URL parameter, parses it as a UUID, and on
// failure writes a 400 before returning ok=false. Saves every handler
// from repeating the same 4-line dance.
func parseUUIDParam(w http.ResponseWriter, r *http.Request, name string) (uuid.UUID, bool) {
	raw := chi.URLParam(r, name)
	id, err := uuid.Parse(raw)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_id", fmt.Sprintf("%s must be a UUID", name))
		return uuid.Nil, false
	}
	return id, true
}

// isUniqueViolation returns true if err is a PostgreSQL unique constraint
// violation (SQLSTATE 23505). Used to map natural-key collisions on INSERT
// to 409 Conflict responses. Unwraps errors so it still matches when
// wrapped by fmt.Errorf / errors.Join.
func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23505"
	}
	return false
}
