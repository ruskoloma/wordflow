package api

import (
	"log/slog"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/rsln-ua/wordflow-backend/internal/ai"
	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

type Deps struct {
	Pool      *pgxpool.Pool
	Logger    *slog.Logger
	Auth      auth.Config
	AI        *ai.Client
	AILimiter *ai.PerUserLimiter
}

type handlers struct {
	pool      *pgxpool.Pool
	logger    *slog.Logger
	ai        *ai.Client
	aiLimiter *ai.PerUserLimiter
}

func NewRouter(d Deps) *chi.Mux {
	h := &handlers{
		pool:      d.Pool,
		logger:    d.Logger,
		ai:        d.AI,
		aiLimiter: d.AILimiter,
	}

	r := chi.NewRouter()

	r.Use(middleware.RequestID) // attach X-Request-Id for traceability
	r.Use(middleware.RealIP)    // trust X-Forwarded-For when behind a proxy
	r.Use(middleware.Recoverer) // panic → 500 instead of crashing the process
	r.Use(slogAccessLog(d.Logger))

	r.Get("/health", h.health)

	r.Post("/v1/auth/email/start", h.authEmailStart)
	r.Post("/v1/auth/email/verify", h.authEmailVerify)

	r.Route("/v1", func(r chi.Router) {
		r.Use(auth.Middleware(d.Auth, d.Logger))

		r.Get("/me", h.me)

		r.Get("/sync/pull", h.syncPull)

		r.Route("/words", func(r chi.Router) {
			r.Post("/", h.createWord)
			r.Patch("/progress/batch", h.batchProgress)
			r.Patch("/{id}", h.updateWord)
			r.Delete("/{id}", h.deleteWord)
		})

		r.Route("/collections", func(r chi.Router) {
			r.Post("/", h.createCollection)
			r.Patch("/{id}", h.updateCollection)
			r.Delete("/{id}", h.deleteCollection)

			r.Route("/{cid}/words/{wid}", func(r chi.Router) {
				r.Post("/", h.linkWordCollection)
				r.Delete("/", h.unlinkWordCollection)
			})
		})

		r.Route("/ai", func(r chi.Router) {
			r.Post("/translate", h.aiTranslate)
			r.Post("/generate-collection", h.aiGenerateCollection)
		})
	})

	return r
}
