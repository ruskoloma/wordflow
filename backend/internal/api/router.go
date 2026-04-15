// Package api wires the HTTP router and all the handlers.
//
// Organization:
//   - router.go      — chi router assembly, middleware chain, route table
//   - middleware.go  — cross-cutting concerns (slog logging, max body, etc.)
//   - health.go      — /health (unauthenticated liveness probe)
//   - me.go          — /v1/me (authenticated, returns the caller's user id)
//   - (later) words.go, collections.go, sync_pull.go, ai.go
//
// A `handlers` struct holds all the dependencies every handler might need
// (db pool, logger, later: config, clerk verifier). Methods on this struct
// are the actual HTTP handlers. This is a pragmatic alternative to passing
// a giant argument list or abusing package-level globals.
package api

import (
	"log/slog"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/rsln-ua/wordflow-backend/internal/ai"
	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

// Deps groups everything the router needs at construction time. Grows as
// we add subsystems (OpenRouter client, etc.). Passing a struct lets us
// extend without touching every call site.
type Deps struct {
	Pool      *pgxpool.Pool
	Logger    *slog.Logger
	Auth      auth.Config
	AI        *ai.Client
	AILimiter *ai.PerUserLimiter
}

// handlers bundles the dependencies shared by all HTTP handlers.
// Passed around as a receiver so each endpoint is a method on *handlers.
type handlers struct {
	pool      *pgxpool.Pool
	logger    *slog.Logger
	ai        *ai.Client
	aiLimiter *ai.PerUserLimiter
}

// NewRouter builds the chi router with the full middleware chain and
// registers every route.
//
// Route layout:
//   - /health           unauthenticated (liveness + DB ping)
//   - /v1/*             under the Clerk auth middleware
//   - /v1/me            returns the authenticated caller's Clerk user id
//   - (phase 6+) /v1/words, /v1/collections, /v1/sync, /v1/ai
func NewRouter(d Deps) *chi.Mux {
	h := &handlers{
		pool:      d.Pool,
		logger:    d.Logger,
		ai:        d.AI,
		aiLimiter: d.AILimiter,
	}

	r := chi.NewRouter()

	// Middleware order matters. Each one wraps the next, so the
	// outermost (first Use) runs first on the request and last on the
	// response.
	r.Use(middleware.RequestID) // attach X-Request-Id for traceability
	r.Use(middleware.RealIP)    // trust X-Forwarded-For when behind a proxy
	r.Use(middleware.Recoverer) // panic → 500 instead of crashing the process
	r.Use(slogAccessLog(d.Logger))

	// Public routes (no auth).
	r.Get("/health", h.health)

	// Auth endpoints — public, because they ISSUE auth. The Android
	// login screen calls these before it has a JWT to send.
	// Passwordless flow: /email/start sends a code; /email/verify
	// exchanges the code for a session JWT.
	r.Post("/v1/auth/email/start", h.authEmailStart)
	r.Post("/v1/auth/email/verify", h.authEmailVerify)

	// Authenticated routes. Everything under /v1 requires a valid Clerk
	// JWT. chi's Route+Use pattern scopes middleware to a subtree
	// cleanly without splitting main.go into two routers.
	r.Route("/v1", func(r chi.Router) {
		r.Use(auth.Middleware(d.Auth, d.Logger))

		r.Get("/me", h.me)

		// Sync pull: delta fetch for the client's local cache.
		r.Get("/sync/pull", h.syncPull)

		// Words CRUD + progress batch. The batch endpoint is under
		// /v1/words/progress/batch so it doesn't collide with
		// /v1/words/{id} chi URL parameter matching — chi uses
		// longest-match routing, but putting the literal path first
		// is clearer.
		r.Route("/words", func(r chi.Router) {
			r.Post("/", h.createWord)
			r.Patch("/progress/batch", h.batchProgress)
			r.Patch("/{id}", h.updateWord)
			r.Delete("/{id}", h.deleteWord)
		})

		// Collections CRUD. Links are nested under the collection
		// so the URL reads left-to-right as "this collection, this
		// word inside it".
		r.Route("/collections", func(r chi.Router) {
			r.Post("/", h.createCollection)
			r.Patch("/{id}", h.updateCollection)
			r.Delete("/{id}", h.deleteCollection)

			r.Route("/{cid}/words/{wid}", func(r chi.Router) {
				r.Post("/", h.linkWordCollection)
				r.Delete("/", h.unlinkWordCollection)
			})
		})

		// AI proxy — typed endpoints on top of OpenRouter, per-user
		// rate-limited. Prompts live server-side (internal/ai).
		r.Route("/ai", func(r chi.Router) {
			r.Post("/translate", h.aiTranslate)
			r.Post("/generate-collection", h.aiGenerateCollection)
		})
	})

	return r
}
