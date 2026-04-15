// Package main is the entry point for the WordFlow backend HTTP server.
//
// Responsibilities of main:
//  1. Build the slog JSON logger and set it as the default.
//  2. Load + validate environment config.
//  3. Open the pgx connection pool and verify it's reachable.
//  4. Build the chi router with the pool + logger injected.
//  5. Start the HTTP server and wait for either a fatal error or SIGTERM.
//  6. On SIGTERM, run http.Server.Shutdown with a deadline so in-flight
//     requests get a chance to finish before the process dies.
//
// Everything other than wiring lives under internal/ — main should stay
// boring and readable top-to-bottom.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/rsln-ua/wordflow-backend/internal/ai"
	"github.com/rsln-ua/wordflow-backend/internal/api"
	"github.com/rsln-ua/wordflow-backend/internal/auth"
	"github.com/rsln-ua/wordflow-backend/internal/config"
	"github.com/rsln-ua/wordflow-backend/internal/db"
)

func main() {
	// A slog JSON handler writes one log line = one JSON object to stdout.
	// Railway's log viewer renders these as structured events automatically.
	// We set the app-wide default so any package that grabs slog.Default()
	// gets the same handler.
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	slog.SetDefault(logger)

	cfg, err := config.Load()
	if err != nil {
		logger.Error("config load failed", "err", err)
		os.Exit(1)
	}
	logger.Info("config loaded", "env", cfg.Env, "port", cfg.Port)

	// signal.NotifyContext returns a ctx that's cancelled on SIGINT/SIGTERM.
	// This is the idiomatic Go pattern for shutdown signaling — no manual
	// channels, no select loops in main, the context does the talking.
	rootCtx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(rootCtx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("db pool creation failed", "err", err)
		os.Exit(1)
	}
	defer pool.Close()

	// Fail-fast ping: if the DB is unreachable at boot, we'd rather the
	// container restart immediately than start serving requests that will
	// all 503. Use a short timeout so we don't hang forever on a bad URL.
	{
		pingCtx, cancel := context.WithTimeout(rootCtx, 5*time.Second)
		defer cancel()
		if err := pool.Ping(pingCtx); err != nil {
			logger.Error("startup db ping failed", "err", err)
			os.Exit(1)
		}
	}
	logger.Info("db connected")

	// Configure the Clerk SDK. This is a package-level side effect
	// inside clerk-sdk-go; we do it once at startup so all subsequent
	// jwt.Verify calls have the right credentials.
	authCfg := auth.Config{
		SecretKey:       cfg.ClerkSecretKey,
		AuthorizedParty: cfg.ClerkAuthorizedParty,
	}
	if err := auth.Init(authCfg, logger); err != nil {
		logger.Error("clerk auth init failed", "err", err)
		os.Exit(1)
	}
	// Tell the sign-in / sign-up handlers whether Clerk is set up
	// so they can fail fast with a 503 instead of exploding inside
	// the Clerk SDK on an empty secret key.
	api.SetClerkConfigured(cfg.ClerkSecretKey != "")

	// Build the Clerk Frontend API client for the passwordless
	// email-code flow. Only useful when CLERK_PUBLISHABLE_KEY is set
	// (the Frontend API authenticates with the publishable key, not
	// the secret key); skip the construction otherwise so the auth
	// handlers fall through to the 503 path.
	if cfg.ClerkPublishableKey != "" {
		cf, cfErr := auth.NewClerkFrontend(cfg.ClerkPublishableKey, "")
		if cfErr != nil {
			logger.Error("clerk frontend init failed", "err", cfErr)
			os.Exit(1)
		}
		logger.Info("clerk frontend initialized", "base_url", cf.BaseURL())
		api.SetClerkFrontend(cf)
	} else {
		logger.Warn("CLERK_PUBLISHABLE_KEY not set; /v1/auth/email/* will return 503")
	}

	// AI: typed OpenRouter client + per-user rate limiter.
	// Boots successfully even with an empty API key; the handler
	// returns 503 ai_not_configured in that case.
	aiClient := ai.NewClient(ai.Config{
		APIKey:  cfg.OpenrouterAPIKey,
		Model:   cfg.OpenrouterModel,
		Timeout: 45 * time.Second,
	})
	if aiClient.Configured() {
		logger.Info("ai client initialized", "model", cfg.OpenrouterModel)
	} else {
		logger.Warn("OPENROUTER_API_KEY not set; AI endpoints will return 503")
	}
	aiLimiter := ai.NewPerUserLimiter(30, 5)
	aiLimiter.Start(rootCtx, 10*time.Minute, logger)

	srv := &http.Server{
		Addr: ":" + cfg.Port,
		Handler: api.NewRouter(api.Deps{
			Pool:      pool,
			Logger:    logger,
			Auth:      authCfg,
			AI:        aiClient,
			AILimiter: aiLimiter,
		}),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		// Generate-collection can take >60s for 100-word batches,
		// so WriteTimeout needs headroom. 150s leaves room before
		// Railway's upstream timeout (usually 300s).
		WriteTimeout: 150 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	// Run the server in a goroutine so main can block on either
	// rootCtx.Done() (shutdown signal) or serverErr (fatal server error),
	// whichever happens first.
	serverErr := make(chan error, 1)
	go func() {
		logger.Info("http server listening", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
		close(serverErr)
	}()

	select {
	case err := <-serverErr:
		logger.Error("http server crashed", "err", err)
		os.Exit(1)
	case <-rootCtx.Done():
		logger.Info("shutdown signal received")
	}

	// Graceful shutdown: stop accepting new connections, wait up to 25s
	// for in-flight requests to finish, then return. Railway sends SIGTERM
	// ~30s before SIGKILL, so 25s leaves 5s of headroom for process exit.
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "err", err)
		os.Exit(1)
	}
	logger.Info("server stopped cleanly")
}
