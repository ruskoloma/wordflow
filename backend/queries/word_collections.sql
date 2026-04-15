-- Queries against the `word_collections` M2M join table.
--
-- Each row has its own UUID PK so we can tombstone and re-create the
-- same (word_id, collection_id) pair without the old row blocking the
-- new one. The partial unique index `word_collections_pair_live` enforces
-- that only one *live* link per pair can exist at a time.

-- name: ListWordCollectionsUpdatedSince :many
SELECT *
FROM word_collections
WHERE user_id = $1 AND updated_at > $2
ORDER BY updated_at ASC;

-- name: FindLiveWordCollection :one
-- 409 check for POST /v1/collections/{cid}/words/{wid}.
SELECT *
FROM word_collections
WHERE user_id = $1
  AND word_id = $2
  AND collection_id = $3
  AND deleted_at IS NULL;

-- name: CreateWordCollection :one
INSERT INTO word_collections (
    id,
    user_id,
    word_id,
    collection_id
) VALUES (
    $1, $2, $3, $4
)
RETURNING *;

-- name: SoftDeleteWordCollection :exec
-- DELETE /v1/collections/{cid}/words/{wid}.
UPDATE word_collections
SET deleted_at = now(),
    updated_at = now()
WHERE user_id = $1
  AND word_id = $2
  AND collection_id = $3
  AND deleted_at IS NULL;

-- name: SoftDeleteLinksByWord :exec
-- Called from the DELETE /v1/words/{id} handler in the same transaction
-- as SoftDeleteWord. All live links pointing at a deleted word become
-- tombstones with a bumped updated_at so other devices drop them.
UPDATE word_collections
SET deleted_at = now(),
    updated_at = now()
WHERE user_id = $1 AND word_id = $2 AND deleted_at IS NULL;

-- name: SoftDeleteLinksByCollection :exec
-- Same pattern, from the DELETE /v1/collections/{id} handler.
UPDATE word_collections
SET deleted_at = now(),
    updated_at = now()
WHERE user_id = $1 AND collection_id = $2 AND deleted_at IS NULL;
