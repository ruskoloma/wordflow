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

	rootCtx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	pool, err := db.NewPool(rootCtx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("db pool creation failed", "err", err)
		os.Exit(1)
	}
	defer pool.Close()

	{
		pingCtx, cancel := context.WithTimeout(rootCtx, 5*time.Second)
		defer cancel()
		if err := pool.Ping(pingCtx); err != nil {
			logger.Error("startup db ping failed", "err", err)
			os.Exit(1)
		}
	}
	logger.Info("db connected")

	authCfg := auth.Config{
		SecretKey:       cfg.ClerkSecretKey,
		AuthorizedParty: cfg.ClerkAuthorizedParty,
		AppJWTSecret:    cfg.AppJWTSecret,
	}
	if err := auth.Init(authCfg, logger); err != nil {
		logger.Error("clerk auth init failed", "err", err)
		os.Exit(1)
	}
	api.SetClerkConfigured(cfg.ClerkSecretKey != "")

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
		// Generation can be slow for 100-word batches.
		WriteTimeout: 150 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

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

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "err", err)
		os.Exit(1)
	}
	logger.Info("server stopped cleanly")
}
