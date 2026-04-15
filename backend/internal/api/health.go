package api

import (
	"context"
	"net/http"
	"time"
)

// healthResponse is what /health returns on the happy path.
// db_ms is the observed ping round-trip in milliseconds — a cheap
// indicator that the pool is connected and the DB is responsive.
type healthResponse struct {
	Status string `json:"status"`
	DBMs   int64  `json:"db_ms"`
}

// health checks the database by running a short-timeout Ping against the
// pool. A successful ping returns 200 with the latency; a failure returns
// 503 so load balancers can mark us degraded without thinking we crashed.
//
// We use a fresh context with timeout (not r.Context directly) because we
// want health probes to be bounded even if the client dawdles, and to
// keep a slow DB from tying up a request slot indefinitely.
func (h *handlers) health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	start := time.Now()
	if err := h.pool.Ping(ctx); err != nil {
		h.logger.Warn("health ping failed", "err", err)
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{
			"status": "degraded",
			"error":  "db unreachable",
		})
		return
	}

	writeJSON(w, http.StatusOK, healthResponse{
		Status: "ok",
		DBMs:   time.Since(start).Milliseconds(),
	})
}
