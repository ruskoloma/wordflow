-- 0001_init.down.sql — roll back 0001_init.up.sql.
--
-- Drops the three tables in FK-dependency order (cross-ref first, then
-- words + collections). Indexes are dropped automatically with the tables.
-- We intentionally do NOT drop the pgcrypto extension: other migrations
-- or out-of-band schemas may rely on it, and the IF NOT EXISTS on the up
-- side makes re-creating it cheap if someone really wants it gone.

DROP TABLE IF EXISTS word_collections;
DROP TABLE IF EXISTS collections;
DROP TABLE IF EXISTS words;
