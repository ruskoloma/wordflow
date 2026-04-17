package api

import (
	"context"

	"github.com/rsln-ua/wordflow-backend/internal/db/sqlc"
)

func (h *handlers) inTx(ctx context.Context, fn func(*sqlc.Queries) error) error {
	tx, err := h.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	q := sqlc.New(h.pool).WithTx(tx)
	if err := fn(q); err != nil {
		return err
	}
	return tx.Commit(ctx)
}
