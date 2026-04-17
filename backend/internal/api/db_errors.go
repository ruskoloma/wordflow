package api

import (
	"errors"

	"github.com/jackc/pgx/v5/pgconn"
)

const (
	pgUniqueViolation     = "23505"
	pgForeignKeyViolation = "23503"
)

func isUniqueViolation(err error) bool {
	return pgErrorCode(err) == pgUniqueViolation
}

func isForeignKeyViolation(err error) bool {
	return pgErrorCode(err) == pgForeignKeyViolation
}

func pgErrorCode(err error) string {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code
	}
	return ""
}
