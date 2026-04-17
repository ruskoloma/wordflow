-- Queries against the `collections` table.
-- Same conventions as words.sql — see its comment block.

-- name: ListCollectionsUpdatedSince :many
SELECT *
FROM collections
WHERE user_id = $1 AND updated_at > $2
ORDER BY updated_at ASC;

-- name: FindCollectionByID :one
SELECT *
FROM collections
WHERE id = $1 AND user_id = $2;

-- name: FindLiveCollectionByName :one
SELECT *
FROM collections
WHERE user_id = $1 AND name = $2 AND deleted_at IS NULL;

-- name: CreateCollection :one
INSERT INTO collections (
    id,
    user_id,
    name,
    is_active
) VALUES (
    $1, $2, $3, $4
)
RETURNING *;

-- name: UpdateCollection :one
UPDATE collections
SET
    name       = COALESCE(sqlc.narg('name')::text,       name),
    is_active  = COALESCE(sqlc.narg('is_active')::bool,  is_active),
    updated_at = statement_timestamp()
WHERE id = sqlc.arg('id')
  AND user_id = sqlc.arg('user_id')
  AND deleted_at IS NULL
RETURNING *;

-- name: SoftDeleteCollection :exec
-- Soft-delete a collection. The cascade to word_collections is handled
-- separately (SoftDeleteLinksByCollection in word_collections.sql) in
-- the same transaction from the handler, so both rows are visible to the
-- next sync pull.
UPDATE collections
SET deleted_at = statement_timestamp(),
    updated_at = statement_timestamp()
WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL;
