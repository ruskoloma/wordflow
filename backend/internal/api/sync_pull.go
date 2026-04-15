package api

import (
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
	"github.com/rsln-ua/wordflow-backend/internal/db/sqlc"
)

// syncPullResponse is the shape of GET /v1/sync/pull. Everything the
// Android cache needs to catch up since its last pull:
//
//   - server_time is the cursor the client should send as ?since on its
//     next pull. Captured server-side BEFORE we start reading rows so
//     concurrent writes can't slip past the window.
//
//   - words / collections / word_collections are the rows whose
//     updated_at is strictly greater than the incoming ?since.
//     Tombstones (deleted_at IS NOT NULL) are included so the client
//     can drop them from its local cache.
//
// Order is stable (ORDER BY updated_at ASC on each list) so a client
// that applies them in order can process them as an event log.
type syncPullResponse struct {
	ServerTime      time.Time             `json:"server_time"`
	Words           []sqlc.Word           `json:"words"`
	Collections     []sqlc.Collection     `json:"collections"`
	WordCollections []sqlc.WordCollection `json:"word_collections"`
}

// syncPull is GET /v1/sync/pull?since=<RFC3339>.
//
// Correctness-sensitive details:
//
//   - We open a READ ONLY + REPEATABLE READ transaction. This gives us a
//     consistent snapshot of the database across the three SELECTs:
//     if a concurrent write on another device commits between our
//     SELECT words and SELECT collections, our snapshot stays on the
//     pre-commit view, so we don't return a partial world where a link
//     references a word we haven't listed.
//
//   - serverTime is captured IMMEDIATELY after BeginTx (i.e. as close
//     as possible to the transaction's snapshot time). We return it as
//     the new cursor; on the next pull, the client sends it back as
//     ?since. Any row committed AFTER this moment will have an
//     updated_at > serverTime and be picked up by that next pull.
//
//   - The default "since" when the param is absent or empty is the zero
//     time.Time, which in Postgres compares as `'0001-01-01 00:00:00+00'`
//     — smaller than any real updated_at, so every live row comes back.
//     That's the initial-full-sync path.
func (h *handlers) syncPull(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	// Parse the ?since cursor. Empty / absent = full sync.
	var since time.Time
	if s := r.URL.Query().Get("since"); s != "" {
		t, err := time.Parse(time.RFC3339Nano, s)
		if err != nil {
			// Fall back to RFC3339 (seconds precision) for clients
			// that don't include sub-second fractions.
			t, err = time.Parse(time.RFC3339, s)
			if err != nil {
				writeError(w, http.StatusBadRequest, "invalid_since",
					"?since must be an RFC3339 timestamp, e.g. 2026-04-14T12:34:56Z")
				return
			}
		}
		since = t
	}

	tx, err := h.pool.BeginTx(r.Context(), pgx.TxOptions{
		IsoLevel:   pgx.RepeatableRead,
		AccessMode: pgx.ReadOnly,
	})
	if err != nil {
		h.logger.Error("sync pull begin tx", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}
	// Rollback is a no-op after a successful read-only Commit;
	// deferring it is just insurance against early returns.
	defer func() { _ = tx.Rollback(r.Context()) }()

	// Capture the cursor as close to the tx snapshot as we can. This
	// happens BEFORE any reads, so any row committed after this
	// instant will be picked up on the next pull (its updated_at will
	// be strictly greater than this serverTime).
	serverTime := time.Now().UTC()

	q := sqlc.New(h.pool).WithTx(tx)

	words, err := q.ListWordsUpdatedSince(r.Context(), sqlc.ListWordsUpdatedSinceParams{
		UserID:    userID,
		UpdatedAt: since,
	})
	if err != nil {
		h.logger.Error("sync pull list words", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	collections, err := q.ListCollectionsUpdatedSince(r.Context(), sqlc.ListCollectionsUpdatedSinceParams{
		UserID:    userID,
		UpdatedAt: since,
	})
	if err != nil {
		h.logger.Error("sync pull list collections", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	wcs, err := q.ListWordCollectionsUpdatedSince(r.Context(), sqlc.ListWordCollectionsUpdatedSinceParams{
		UserID:    userID,
		UpdatedAt: since,
	})
	if err != nil {
		h.logger.Error("sync pull list word_collections", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	if err := tx.Commit(r.Context()); err != nil {
		h.logger.Error("sync pull commit", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}

	writeJSON(w, http.StatusOK, syncPullResponse{
		ServerTime:      serverTime,
		Words:           words,
		Collections:     collections,
		WordCollections: wcs,
	})
}
