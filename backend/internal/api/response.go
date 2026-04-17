package api

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

const maxRequestBodyBytes = 1 << 20 // 1 MB

type errorResponse struct {
	Error      string     `json:"error"`
	Message    string     `json:"message,omitempty"`
	ExistingID *uuid.UUID `json:"existing_id,omitempty"`
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, errorResponse{Error: code, Message: message})
}

func writeConflict(w http.ResponseWriter, code string, existingID uuid.UUID) {
	writeJSON(w, http.StatusConflict, errorResponse{
		Error:      code,
		ExistingID: &existingID,
	})
}

func decodeJSON(w http.ResponseWriter, r *http.Request, v any) bool {
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

	var trailing struct{}
	if err := dec.Decode(&trailing); !errors.Is(err, io.EOF) {
		writeError(w, http.StatusBadRequest, "invalid_body", "multiple JSON values in body")
		return false
	}
	return true
}

func parseUUIDParam(w http.ResponseWriter, r *http.Request, name string) (uuid.UUID, bool) {
	raw := chi.URLParam(r, name)
	id, err := uuid.Parse(raw)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_id", fmt.Sprintf("%s must be a UUID", name))
		return uuid.Nil, false
	}
	return id, true
}
