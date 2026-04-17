-- Queries against the `words` table.
--
-- A few conventions used across this file:
--
--   * Every query that touches a user's data takes user_id as the first
--     parameter and filters on it. This is multi-tenant isolation at the
--     query layer — no row ever leaks between users, even if an upstream
--     handler bug forgets a check.
--
--   * `updated_at = statement_timestamp()` is set on every update so
--     sync cursors use statement time instead of transaction start time.
--
--   * "Live" means `deleted_at IS NULL`. Queries named *Live operate only
--     on non-tombstoned rows; plain queries (used by sync pull) return
--     tombstones too so clients can drop deleted rows from their cache.
--
--   * PATCH semantics use `COALESCE(sqlc.narg('x'), x)` — if the named
--     arg is NULL, keep the existing value; otherwise replace. This is
--     the idiomatic sqlc pattern for partial updates.

-- name: ListWordsUpdatedSince :many
-- Delta pull for /v1/sync/pull. Returns rows (including tombstones) that
-- have been touched since the given timestamp. Ordering by updated_at
-- makes the stream easy for the client to apply in order.
SELECT *
FROM words
WHERE user_id = $1 AND updated_at > $2
ORDER BY updated_at ASC;

-- name: FindWordByID :one
-- Used by PATCH/DELETE handlers for 404 checks. Includes tombstones so we
-- can distinguish "not yours" from "already deleted" if we ever want to.
SELECT *
FROM words
WHERE id = $1 AND user_id = $2;

-- name: FindLiveWordByNormalized :one
-- 409 collision check for POST /v1/words. Returns the existing live row
-- so the handler can include existing_id in the error body.
SELECT *
FROM words
WHERE user_id = $1 AND normalized_word = $2 AND deleted_at IS NULL;

-- name: CreateWord :one
-- INSERT with all fields explicit. The client may provide id (idempotent
-- retry / migration path); handlers should generate one if missing.
-- added_date defaults to now() if the client doesn't supply it.
INSERT INTO words (
    id,
    user_id,
    original_word,
    normalized_word,
    translation,
    example_usage,
    explanation,
    pronunciation,
    added_date,
    difficulty,
    is_learned
) VALUES (
    $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11
)
RETURNING *;

-- name: UpdateWord :one
-- PATCH endpoint. Null args mean "don't touch." updated_at is always
-- bumped because a PATCH is a mutation by definition.
UPDATE words
SET
    original_word  = COALESCE(sqlc.narg('original_word')::text,        original_word),
    normalized_word = CASE
        WHEN sqlc.narg('original_word')::text IS NULL THEN normalized_word
        ELSE lower(trim(sqlc.narg('original_word')::text))
    END,
    translation    = COALESCE(sqlc.narg('translation')::text,          translation),
    example_usage  = COALESCE(sqlc.narg('example_usage')::text,        example_usage),
    explanation    = COALESCE(sqlc.narg('explanation')::text,          explanation),
    pronunciation  = COALESCE(sqlc.narg('pronunciation')::text,        pronunciation),
    difficulty     = COALESCE(sqlc.narg('difficulty')::smallint,       difficulty),
    is_learned     = COALESCE(sqlc.narg('is_learned')::boolean,        is_learned),
    updated_at     = statement_timestamp()
WHERE id = sqlc.arg('id')
  AND user_id = sqlc.arg('user_id')
  AND deleted_at IS NULL
RETURNING *;

-- name: SoftDeleteWord :exec
-- DELETE endpoint. Sets deleted_at (the tombstone) and bumps updated_at
-- so the next pull propagates the delete to other devices.
UPDATE words
SET deleted_at = statement_timestamp(),
    updated_at = statement_timestamp()
WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL;

-- name: UpdateWordProgress :execrows
-- PATCH /v1/words/progress/batch, called once per id inside a single Go
-- transaction. All three progress fields are always supplied — the client
-- flushes a snapshot of its local state, not a delta.
UPDATE words
SET show_count      = sqlc.arg('show_count'),
    last_shown_date = sqlc.arg('last_shown_date'),
    is_learned      = sqlc.arg('is_learned'),
    updated_at      = statement_timestamp()
WHERE id = sqlc.arg('id')
  AND user_id = sqlc.arg('user_id')
  AND deleted_at IS NULL;
