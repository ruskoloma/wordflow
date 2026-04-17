package api

import (
	"errors"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
	"github.com/rsln-ua/wordflow-backend/internal/db/sqlc"
)

// ---------- Request body shapes -------------------------------------

// createWordRequest is the POST /v1/words body. ID is optional — if
// present, the server uses it verbatim (idempotent retry + one-time
// migration from the existing offline-only Android DB); if absent, the
// server generates one.
//
// Difficulty, AddedDate, and IsLearned use pointers so we can
// distinguish "client omitted this, use default" from "client sent 0".
// Required fields are plain strings — empty string means the client
// really sent an empty string.
type createWordRequest struct {
	ID             *uuid.UUID `json:"id,omitempty"`
	OriginalWord   string     `json:"original_word"`
	NormalizedWord string     `json:"normalized_word"`
	Translation    string     `json:"translation"`
	ExampleUsage   string     `json:"example_usage,omitempty"`
	Explanation    string     `json:"explanation,omitempty"`
	Pronunciation  string     `json:"pronunciation,omitempty"`
	AddedDate      *time.Time `json:"added_date,omitempty"`
	Difficulty     *int16     `json:"difficulty,omitempty"`
	IsLearned      *bool      `json:"is_learned,omitempty"`
}

// updateWordRequest is PATCH /v1/words/{id}. Every field is optional
// and absent/null means "don't touch". sqlc's UpdateWord takes the same
// *T types, so we pass them through directly.
type updateWordRequest struct {
	OriginalWord  *string `json:"original_word,omitempty"`
	Translation   *string `json:"translation,omitempty"`
	ExampleUsage  *string `json:"example_usage,omitempty"`
	Explanation   *string `json:"explanation,omitempty"`
	Pronunciation *string `json:"pronunciation,omitempty"`
	Difficulty    *int16  `json:"difficulty,omitempty"`
	IsLearned     *bool   `json:"is_learned,omitempty"`
}

// progressBatchRequest is PATCH /v1/words/progress/batch. It flushes
// learning-progress updates that the Android client accumulated while
// offline. Server applies every update in a single transaction.
type progressBatchRequest struct {
	Updates []progressUpdate `json:"updates"`
}

type progressUpdate struct {
	ID            uuid.UUID  `json:"id"`
	ShowCount     int32      `json:"show_count"`
	LastShownDate *time.Time `json:"last_shown_date"`
	IsLearned     bool       `json:"is_learned"`
}

// progressBatchResponse reports how many updates were applied.
// `applied` may be less than len(updates) if some rows were deleted or
// never existed — we silently skip those rather than failing the batch.
type progressBatchResponse struct {
	Applied    int       `json:"applied"`
	ServerTime time.Time `json:"server_time"`
}

// ---------- POST /v1/words ------------------------------------------

func (h *handlers) createWord(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	var body createWordRequest
	if !decodeJSON(w, r, &body) {
		return
	}

	body.OriginalWord = trimRequiredString(body.OriginalWord)
	body.NormalizedWord = normalizeWord(body.NormalizedWord)
	body.Translation = trimRequiredString(body.Translation)
	body.ExampleUsage = trimRequiredString(body.ExampleUsage)
	body.Explanation = trimRequiredString(body.Explanation)
	body.Pronunciation = trimRequiredString(body.Pronunciation)
	if body.OriginalWord == "" || body.NormalizedWord == "" || body.Translation == "" {
		writeError(w, http.StatusBadRequest, "missing_fields",
			"original_word, normalized_word, and translation are required")
		return
	}
	if body.Difficulty != nil && !validDifficulty(*body.Difficulty) {
		writeError(w, http.StatusBadRequest, "invalid_difficulty", "difficulty must be between 1 and 10")
		return
	}
	if body.ID != nil && *body.ID == uuid.Nil {
		writeError(w, http.StatusBadRequest, "invalid_id", "id must be a UUID")
		return
	}

	id := uuid.New()
	if body.ID != nil {
		id = *body.ID
	}
	addedDate := time.Now().UTC()
	if body.AddedDate != nil {
		addedDate = body.AddedDate.UTC()
	}
	var difficulty int16 = 5
	if body.Difficulty != nil {
		difficulty = *body.Difficulty
	}
	var isLearned bool
	if body.IsLearned != nil {
		isLearned = *body.IsLearned
	}

	q := sqlc.New(h.pool)
	word, err := q.CreateWord(r.Context(), sqlc.CreateWordParams{
		ID:             id,
		UserID:         userID,
		OriginalWord:   body.OriginalWord,
		NormalizedWord: body.NormalizedWord,
		Translation:    body.Translation,
		ExampleUsage:   body.ExampleUsage,
		Explanation:    body.Explanation,
		Pronunciation:  body.Pronunciation,
		AddedDate:      addedDate,
		Difficulty:     difficulty,
		IsLearned:      isLearned,
	})
	if err != nil {
		if isUniqueViolation(err) {
			h.writeCreateWordConflict(w, r, q, userID, body.NormalizedWord, id)
			return
		}
		h.logger.Error("create word", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusCreated, word)
}

// ---------- PATCH /v1/words/{id} ------------------------------------

func (h *handlers) updateWord(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	id, ok := parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	var body updateWordRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	trimOptionalString(body.OriginalWord)
	trimOptionalString(body.Translation)
	trimOptionalString(body.ExampleUsage)
	trimOptionalString(body.Explanation)
	trimOptionalString(body.Pronunciation)
	if body.OriginalWord != nil && *body.OriginalWord == "" {
		writeError(w, http.StatusBadRequest, "invalid_fields", "original_word cannot be empty")
		return
	}
	if body.Translation != nil && *body.Translation == "" {
		writeError(w, http.StatusBadRequest, "invalid_fields", "translation cannot be empty")
		return
	}
	if body.Difficulty != nil && !validDifficulty(*body.Difficulty) {
		writeError(w, http.StatusBadRequest, "invalid_difficulty", "difficulty must be between 1 and 10")
		return
	}

	q := sqlc.New(h.pool)
	word, err := q.UpdateWord(r.Context(), sqlc.UpdateWordParams{
		ID:            id,
		UserID:        userID,
		OriginalWord:  body.OriginalWord,
		Translation:   body.Translation,
		ExampleUsage:  body.ExampleUsage,
		Explanation:   body.Explanation,
		Pronunciation: body.Pronunciation,
		Difficulty:    body.Difficulty,
		IsLearned:     body.IsLearned,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		writeError(w, http.StatusNotFound, "not_found", "word not found")
		return
	}
	if err != nil {
		if isUniqueViolation(err) {
			if body.OriginalWord == nil {
				h.logger.Error("word unique violation without original_word", "err", err, "word_id", id)
				writeError(w, http.StatusInternalServerError, "internal", "db error")
				return
			}
			h.writeWordExistsConflict(w, r, q, userID, normalizeWord(*body.OriginalWord))
			return
		}
		h.logger.Error("update word", "err", err, "word_id", id)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusOK, word)
}

// ---------- DELETE /v1/words/{id} -----------------------------------

func (h *handlers) deleteWord(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	id, ok := parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	// Soft-delete the word AND any live links pointing at it, in a
	// single tx. This keeps the client's cache consistent on the next
	// pull — if we only tombstoned the word, links would dangle until
	// the ON DELETE CASCADE eventually caught up (or never, since we
	// never hard-delete).
	if err := h.inTx(r.Context(), func(q *sqlc.Queries) error {
		if err := q.SoftDeleteWord(r.Context(), sqlc.SoftDeleteWordParams{
			ID:     id,
			UserID: userID,
		}); err != nil {
			return err
		}
		return q.SoftDeleteLinksByWord(r.Context(), sqlc.SoftDeleteLinksByWordParams{
			UserID: userID,
			WordID: id,
		})
	}); err != nil {
		h.logger.Error("delete word tx", "err", err, "word_id", id)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	// 204 is idempotent: if the word was already deleted (or never
	// existed), we still return success. Matches offline-first client
	// retry semantics — clients that retry a stale delete don't care
	// whether the server had already applied it.
	w.WriteHeader(http.StatusNoContent)
}

// ---------- PATCH /v1/words/progress/batch --------------------------

func (h *handlers) batchProgress(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	var body progressBatchRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	for _, u := range body.Updates {
		if u.ID == uuid.Nil {
			writeError(w, http.StatusBadRequest, "invalid_id", "update id must be a UUID")
			return
		}
		if u.ShowCount < 0 {
			writeError(w, http.StatusBadRequest, "invalid_progress", "show_count cannot be negative")
			return
		}
	}

	if len(body.Updates) == 0 {
		writeJSON(w, http.StatusOK, progressBatchResponse{
			Applied:    0,
			ServerTime: time.Now().UTC(),
		})
		return
	}

	// All-or-nothing batch: one tx wraps every update. If the tx
	// commits, client clears its pendingProgressPush flags; if it
	// errors, client keeps them and retries next time.
	applied := 0
	if err := h.inTx(r.Context(), func(q *sqlc.Queries) error {
		for _, u := range body.Updates {
			rows, err := q.UpdateWordProgress(r.Context(), sqlc.UpdateWordProgressParams{
				ID:            u.ID,
				UserID:        userID,
				ShowCount:     u.ShowCount,
				LastShownDate: u.LastShownDate,
				IsLearned:     u.IsLearned,
			})
			if err != nil {
				return err
			}
			applied += int(rows)
		}
		return nil
	}); err != nil {
		h.logger.Error("batch progress tx", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusOK, progressBatchResponse{
		Applied:    applied,
		ServerTime: time.Now().UTC(),
	})
}

func (h *handlers) writeCreateWordConflict(
	w http.ResponseWriter,
	r *http.Request,
	q *sqlc.Queries,
	userID string,
	normalizedWord string,
	id uuid.UUID,
) {
	existing, err := q.FindLiveWordByNormalized(r.Context(), sqlc.FindLiveWordByNormalizedParams{
		UserID:         userID,
		NormalizedWord: normalizedWord,
	})
	if err == nil {
		writeConflict(w, "word_exists", existing.ID)
		return
	}
	if errors.Is(err, pgx.ErrNoRows) {
		writeConflict(w, "word_id_exists", id)
		return
	}
	h.logger.Error("word conflict lookup failed", "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "lookup failed after conflict")
}

func (h *handlers) writeWordExistsConflict(
	w http.ResponseWriter,
	r *http.Request,
	q *sqlc.Queries,
	userID string,
	normalizedWord string,
) {
	existing, err := q.FindLiveWordByNormalized(r.Context(), sqlc.FindLiveWordByNormalizedParams{
		UserID:         userID,
		NormalizedWord: normalizedWord,
	})
	if err == nil {
		writeConflict(w, "word_exists", existing.ID)
		return
	}
	h.logger.Error("word conflict lookup failed", "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "lookup failed after conflict")
}
