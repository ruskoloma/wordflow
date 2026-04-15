-- 0001_init.up.sql — initial WordFlow schema.
--
-- Design notes baked into this migration:
--
--   * UUID primary keys everywhere, no auto-increment. The client can
--     generate its own UUIDs on POST so offline-create-then-push and
--     retry-after-error work idempotently. We still load pgcrypto so
--     the server can fall back to gen_random_uuid() if a client omits
--     the id field.
--
--   * Every row is scoped by user_id TEXT, which holds the Clerk JWT's
--     `sub` claim (e.g. "user_2abc..."). We intentionally don't have a
--     users table — Clerk owns identity.
--
--   * Soft deletes via deleted_at TIMESTAMPTZ. The normal DELETE path is
--     an UPDATE that sets deleted_at + updated_at. Tombstones are still
--     visible in /v1/sync/pull so client caches can drop them.
--
--   * updated_at is the pull cursor. The application sets it to now() on
--     every write (insert, update, or soft-delete). /v1/sync/pull filters
--     by WHERE updated_at > $since, so a single timestamp column handles
--     both normal edits and deletes.
--
--   * Partial unique indexes on the natural keys (user_id, normalized_word)
--     and (user_id, name) let us tombstone a row and later re-create one
--     with the same natural key. The partial predicate excludes deleted
--     rows from uniqueness.
--
--   * word_collections has its own UUID id (not a composite PK) for the
--     same reason — re-linking a word-to-collection pair after deletion
--     requires that the old tombstoned row not block the new one.
--
--   * ON DELETE CASCADE on the FKs is a safety net only. The normal flow
--     soft-deletes; cascades only fire if something physically DELETEs a
--     row (admin cleanup or a future TTL job).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- words --------------------------------------------------------------
CREATE TABLE words (
    id               UUID        PRIMARY KEY,
    user_id          TEXT        NOT NULL,
    original_word    TEXT        NOT NULL,
    normalized_word  TEXT        NOT NULL,
    translation      TEXT        NOT NULL,
    example_usage    TEXT        NOT NULL DEFAULT '',
    explanation      TEXT        NOT NULL DEFAULT '',
    pronunciation    TEXT        NOT NULL DEFAULT '',
    added_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_shown_date  TIMESTAMPTZ,
    show_count       INTEGER     NOT NULL DEFAULT 0,
    difficulty       SMALLINT    NOT NULL DEFAULT 5,
    is_learned       BOOLEAN     NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX words_user_norm_live
    ON words (user_id, normalized_word)
    WHERE deleted_at IS NULL;

CREATE INDEX words_user_updated
    ON words (user_id, updated_at);

-- collections --------------------------------------------------------
CREATE TABLE collections (
    id          UUID        PRIMARY KEY,
    user_id     TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX collections_user_name_live
    ON collections (user_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX collections_user_updated
    ON collections (user_id, updated_at);

-- word_collections (M2M) ---------------------------------------------
CREATE TABLE word_collections (
    id             UUID        PRIMARY KEY,
    user_id        TEXT        NOT NULL,
    word_id        UUID        NOT NULL REFERENCES words(id)       ON DELETE CASCADE,
    collection_id  UUID        NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX word_collections_pair_live
    ON word_collections (user_id, word_id, collection_id)
    WHERE deleted_at IS NULL;

CREATE INDEX word_collections_user_updated
    ON word_collections (user_id, updated_at);

CREATE INDEX word_collections_collection
    ON word_collections (collection_id);
