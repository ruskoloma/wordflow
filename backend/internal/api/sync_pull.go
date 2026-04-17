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
//   - REPEATABLE READ gives the three SELECTs one consistent snapshot.
//   - server_time comes from Postgres, not the app server clock.
//   - Empty ?since means time.Time{}, which is older than real rows.
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

	var serverTime time.Time
	if err := tx.QueryRow(r.Context(), "SELECT statement_timestamp()").Scan(&serverTime); err != nil {
		h.logger.Error("sync pull server time", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "db error")
		return
	}
	serverTime = serverTime.UTC()

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
