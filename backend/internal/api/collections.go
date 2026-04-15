package api

import (
	"errors"
	"net/http"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
	"github.com/rsln-ua/wordflow-backend/internal/db/sqlc"
)

// ---------- Request body shapes -------------------------------------

type createCollectionRequest struct {
	ID       *uuid.UUID `json:"id,omitempty"`
	Name     string     `json:"name"`
	IsActive *bool      `json:"is_active,omitempty"`
}

type updateCollectionRequest struct {
	Name     *string `json:"name,omitempty"`
	IsActive *bool   `json:"is_active,omitempty"`
}

// ---------- POST /v1/collections ------------------------------------

func (h *handlers) createCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	var body createCollectionRequest
	if !decodeJSON(w, r, &body) {
		return
	}

	if body.Name == "" {
		writeError(w, http.StatusBadRequest, "missing_fields", "name is required")
		return
	}

	id := uuid.New()
	if body.ID != nil {
		id = *body.ID
	}
	isActive := true
	if body.IsActive != nil {
		isActive = *body.IsActive
	}

	q := sqlc.New(h.pool)
	col, err := q.CreateCollection(r.Context(), sqlc.CreateCollectionParams{
		ID:       id,
		UserID:   userID,
		Name:     body.Name,
		IsActive: isActive,
	})
	if err != nil {
		if isUniqueViolation(err) {
			existing, lookupErr := q.FindLiveCollectionByName(r.Context(), sqlc.FindLiveCollectionByNameParams{
				UserID: userID,
				Name:   body.Name,
			})
			if lookupErr != nil {
				h.logger.Error("collection 409 lookup failed", "err", lookupErr)
				writeError(w, http.StatusInternalServerError, "internal", "lookup failed after conflict")
				return
			}
			writeConflict(w, "collection_exists", existing.ID)
			return
		}
		h.logger.Error("create collection", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusCreated, col)
}

// ---------- PATCH /v1/collections/{id} ------------------------------

func (h *handlers) updateCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	id, ok := parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	var body updateCollectionRequest
	if !decodeJSON(w, r, &body) {
		return
	}

	q := sqlc.New(h.pool)
	col, err := q.UpdateCollection(r.Context(), sqlc.UpdateCollectionParams{
		ID:       id,
		UserID:   userID,
		Name:     body.Name,
		IsActive: body.IsActive,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		writeError(w, http.StatusNotFound, "not_found", "collection not found")
		return
	}
	if err != nil {
		if isUniqueViolation(err) {
			// Rename collided with another live collection.
			existing, lookupErr := q.FindLiveCollectionByName(r.Context(), sqlc.FindLiveCollectionByNameParams{
				UserID: userID,
				Name:   *body.Name, // non-nil because a rename triggered the violation
			})
			if lookupErr != nil {
				h.logger.Error("collection rename 409 lookup failed", "err", lookupErr)
				writeError(w, http.StatusInternalServerError, "internal", "lookup failed after conflict")
				return
			}
			writeConflict(w, "collection_exists", existing.ID)
			return
		}
		h.logger.Error("update collection", "err", err, "collection_id", id)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusOK, col)
}

// ---------- DELETE /v1/collections/{id} -----------------------------

func (h *handlers) deleteCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	id, ok := parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	// Same pattern as deleteWord: tombstone the collection and every
	// live link that points at it, atomically.
	if err := h.inTx(r.Context(), func(q *sqlc.Queries) error {
		if err := q.SoftDeleteCollection(r.Context(), sqlc.SoftDeleteCollectionParams{
			ID:     id,
			UserID: userID,
		}); err != nil {
			return err
		}
		return q.SoftDeleteLinksByCollection(r.Context(), sqlc.SoftDeleteLinksByCollectionParams{
			UserID:       userID,
			CollectionID: id,
		})
	}); err != nil {
		h.logger.Error("delete collection tx", "err", err, "collection_id", id)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
