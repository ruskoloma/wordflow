// Package db sets up the application's PostgreSQL connection pool.
//
// We use pgx v5 directly (not database/sql) because:
//   - Native Postgres type support: UUID, TIMESTAMPTZ, JSONB, arrays
//     come back as real Go types, not []byte or strings to parse.
//   - Better performance: pgx's binary protocol skips the string-based
//     wire format that database/sql uses through lib/pq.
//   - First-class context support throughout.
//
// pgxpool is pgx's built-in connection pool. It replaces database/sql's
// *sql.DB as the handle you pass around to everything that needs queries.
package db

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// NewPool opens a connection pool against databaseURL and configures it
// for this app's workload.
//
// Sizing rationale:
//   - MaxConns 10: DigitalOcean managed Postgres plans cap total connections
//     pretty aggressively (the smallest plan is ~22). With multiple Railway
//     replicas we want room to scale; 10 is conservative enough to leave
//     headroom and still absorb normal bursts for a single-person app.
//   - MinConns 2: keep a warm baseline so the first few requests after a
//     deploy don't each pay a handshake cost.
//   - HealthCheckPeriod 30s: pgxpool runs a no-op query on idle conns at
//     this interval. Catches dead TCP connections (e.g. managed-PG node
//     restart) before a user request hits them and errors.
//
// The AfterConnect hook runs on every brand-new connection. We force UTC
// so every timestamp the pool returns is in a known timezone regardless
// of the server's default. This is the single most important thing to
// get right for a sync-cursor-based app.
func NewPool(ctx context.Context, databaseURL string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("parse database url: %w", err)
	}

	cfg.MaxConns = 10
	cfg.MinConns = 2
	cfg.HealthCheckPeriod = 30 * time.Second
	cfg.MaxConnLifetime = time.Hour
	cfg.MaxConnIdleTime = 15 * time.Minute

	cfg.AfterConnect = func(ctx context.Context, conn *pgx.Conn) error {
		if _, err := conn.Exec(ctx, "SET TIME ZONE 'UTC'"); err != nil {
			return fmt.Errorf("set UTC timezone: %w", err)
		}
		return nil
	}

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	return pool, nil
}
