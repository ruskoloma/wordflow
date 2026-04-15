package api

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5/middleware"
)

// slogAccessLog emits one structured log line per HTTP request at Info
// level. chi ships its own middleware.Logger, but it writes to the stdlib
// `log` package — plain text, not JSON. For Railway's log viewer we want
// JSON with consistent keys, which slog.NewJSONHandler gives us.
//
// Pattern is standard: wrap the ResponseWriter to capture status + bytes,
// run the downstream handler, then log on the way out. Use defer so the
// log still fires if a handler panics (Recoverer will turn the panic into
// a 500 first).
func slogAccessLog(logger *slog.Logger) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			start := time.Now()

			defer func() {
				logger.Info("http",
					"method", r.Method,
					"path", r.URL.Path,
					"status", ww.Status(),
					"bytes", ww.BytesWritten(),
					"duration_ms", time.Since(start).Milliseconds(),
					"req_id", middleware.GetReqID(r.Context()),
					"remote", r.RemoteAddr,
				)
			}()

			next.ServeHTTP(ww, r)
		})
	}
}
