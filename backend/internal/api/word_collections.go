package api

import (
	"errors"
	"net/http"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
	"github.com/rsln-ua/wordflow-backend/internal/db/sqlc"
)

// ---------- POST /v1/collections/{cid}/words/{wid} ------------------

func (h *handlers) linkWordCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	collectionID, ok := parseUUIDParam(w, r, "cid")
	if !ok {
		return
	}
	wordID, ok := parseUUIDParam(w, r, "wid")
	if !ok {
		return
	}

	q := sqlc.New(h.pool)
	link, err := q.CreateWordCollection(r.Context(), sqlc.CreateWordCollectionParams{
		ID:           uuid.New(),
		UserID:       userID,
		WordID:       wordID,
		CollectionID: collectionID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "not_found", "word or collection not found")
			return
		}
		if isUniqueViolation(err) {
			existing, lookupErr := q.FindLiveWordCollection(r.Context(), sqlc.FindLiveWordCollectionParams{
				UserID:       userID,
				WordID:       wordID,
				CollectionID: collectionID,
			})
			if lookupErr != nil {
				h.logger.Error("link 409 lookup failed", "err", lookupErr)
				writeError(w, http.StatusInternalServerError, "internal", "lookup failed after conflict")
				return
			}
			writeConflict(w, "link_exists", existing.ID)
			return
		}
		if isForeignKeyViolation(err) {
			writeError(w, http.StatusNotFound, "not_found", "word or collection not found")
			return
		}
		h.logger.Error("create link", "err", err, "word_id", wordID, "collection_id", collectionID)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusCreated, link)
}

// ---------- DELETE /v1/collections/{cid}/words/{wid} ----------------

func (h *handlers) unlinkWordCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	collectionID, ok := parseUUIDParam(w, r, "cid")
	if !ok {
		return
	}
	wordID, ok := parseUUIDParam(w, r, "wid")
	if !ok {
		return
	}

	q := sqlc.New(h.pool)
	if err := q.SoftDeleteWordCollection(r.Context(), sqlc.SoftDeleteWordCollectionParams{
		UserID:       userID,
		WordID:       wordID,
		CollectionID: collectionID,
	}); err != nil {
		h.logger.Error("delete link", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	// 204 regardless of whether the link existed — idempotent.
	w.WriteHeader(http.StatusNoContent)
}
