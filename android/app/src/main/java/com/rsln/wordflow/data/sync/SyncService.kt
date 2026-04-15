package com.rsln.wordflow.data.sync

import com.rsln.wordflow.data.auth.AuthManager
import com.rsln.wordflow.data.local.dao.CollectionDao
import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.WordCollectionCrossRef
import com.rsln.wordflow.data.remote.BackendClient
import com.rsln.wordflow.data.remote.BackendException
import com.rsln.wordflow.data.remote.IsoDate
import com.rsln.wordflow.data.remote.ProgressUpdate
import com.rsln.wordflow.data.remote.toEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nuke-and-rebuild cache refresh + batch progress flusher.
 *
 * [refresh] pulls the complete server state (no incremental cursor
 * for now — fine for a personal-scale vocab list) and replaces the
 * local Room tables. Before the pull, it flushes any pending
 * progress updates so the user's local increments don't get erased.
 *
 * The design tradeoff: simpler code + guaranteed consistency vs.
 * reinserting every row on each refresh. For <1000 words this is
 * trivially fast. Incremental delta sync can replace this later if
 * we ever need it.
 */
class SyncService(
    private val backendClient: BackendClient,
    private val wordDao: WordDao,
    private val collectionDao: CollectionDao,
    private val authManager: AuthManager
) {
    // Serialize refresh / flush calls so two concurrent sync buttons
    // can't interleave their writes.
    private val mutex = Mutex()

    /**
     * Result of a refresh attempt. Captured in a sealed class so the
     * UI can show different states (loading, success, auth failure,
     * generic network error) without string-matching.
     */
    sealed class RefreshResult {
        data class Success(
            val words: Int,
            val collections: Int,
            val links: Int,
            val pushedProgress: Int
        ) : RefreshResult()
        data object Unauthenticated : RefreshResult()
        data class Failure(val message: String) : RefreshResult()
    }

    /**
     * Full sync:
     *   1. Flush any pending progress updates via batch PATCH
     *   2. GET /v1/sync/pull with no cursor (full snapshot)
     *   3. Replace Room's words/collections/word_collections with
     *      the server's view, in one transaction-like sequence
     *
     * Not an actual Room @Transaction because the pull happens over
     * the network between step 1 and 2 — we can't hold a DB
     * transaction open across that. Room's own write methods are
     * already transactional.
     */
    suspend fun refresh(): RefreshResult = mutex.withLock {
        if (!authManager.isSignedIn()) return@withLock RefreshResult.Unauthenticated

        val pushed = try {
            flushPendingProgress()
        } catch (e: BackendException.Unauthorized) {
            return@withLock RefreshResult.Unauthenticated
        } catch (e: BackendException) {
            // Progress push failed but we can still try the pull.
            // The rows stay dirty and we'll retry next refresh.
            0
        }

        val pull = try {
            backendClient.syncPull()
        } catch (e: BackendException.Unauthorized) {
            return@withLock RefreshResult.Unauthenticated
        } catch (e: BackendException) {
            return@withLock RefreshResult.Failure(e.message ?: "sync failed")
        }

        // Replace all local rows. Order matters for FKs: cross-refs
        // first (to drop stale foreign refs), then collections,
        // then words — but since our cross-ref's FKs are cascade,
        // the order is more about cleanliness than correctness.
        collectionDao.deleteAllCrossRefs()
        wordDao.deleteAllWords()
        collectionDao.deleteAllCollections()

        // Skip tombstones (server puts deleted_at on removed rows so
        // incremental clients can react, but we're doing a full
        // snapshot so we just don't insert them).
        val liveWords = pull.words.filter { it.deletedAt == null }
        val liveCollections = pull.collections.filter { it.deletedAt == null }
        val liveLinks = pull.wordCollections.filter { it.deletedAt == null }

        // Insert words and collections, capturing the new local
        // Long ids keyed by the server UUID so we can translate
        // link rows below.
        val wordIdByRemote = mutableMapOf<String, Long>()
        for (dto in liveWords) {
            val localId = wordDao.insertWord(dto.toEntity())
            wordIdByRemote[dto.id] = localId
        }

        val collectionIdByRemote = mutableMapOf<String, Long>()
        for (dto in liveCollections) {
            val localId = collectionDao.insertCollection(dto.toEntity())
            collectionIdByRemote[dto.id] = localId
        }

        var insertedLinks = 0
        for (link in liveLinks) {
            val wid = wordIdByRemote[link.wordId] ?: continue
            val cid = collectionIdByRemote[link.collectionId] ?: continue
            collectionDao.insertCrossRef(WordCollectionCrossRef(wid, cid))
            insertedLinks++
        }

        RefreshResult.Success(
            words = liveWords.size,
            collections = liveCollections.size,
            links = insertedLinks,
            pushedProgress = pushed
        )
    }

    /**
     * Drain rows with pendingProgressPush=1 by sending them to
     * PATCH /v1/words/progress/batch, then clear the flag for the
     * rows the server accepted. Returns how many rows were pushed.
     */
    suspend fun flushPendingProgress(): Int {
        val dirty = wordDao.getWordsWithPendingProgress()
        if (dirty.isEmpty()) return 0

        // Only rows with a server id can be flushed. Locally-only
        // rows would 404 — shouldn't happen in our flow but we
        // filter defensively.
        val withRemote = dirty.filter { it.remoteId != null }
        if (withRemote.isEmpty()) return 0

        val updates = withRemote.map {
            ProgressUpdate(
                id = it.remoteId!!,
                showCount = it.showCount,
                lastShownDate = if (it.lastShownDate > 0) IsoDate.format(it.lastShownDate) else null,
                isLearned = it.isLearned
            )
        }

        backendClient.batchProgress(updates)

        // Only clear the dirty flag for the rows we actually pushed.
        val localIds = withRemote.map { it.id }
        wordDao.clearPendingProgressForIds(localIds)

        return localIds.size
    }

    /**
     * Called on sign-out to wipe every cached row. Next sign-in
     * will rebuild from a fresh sync pull.
     */
    suspend fun clearLocalCache() = mutex.withLock {
        collectionDao.deleteAllCrossRefs()
        wordDao.deleteAllWords()
        collectionDao.deleteAllCollections()
    }
}
