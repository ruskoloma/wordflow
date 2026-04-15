package com.rsln.wordflow.data.repository

import com.rsln.wordflow.data.local.dao.CollectionDao
import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.*
import com.rsln.wordflow.data.remote.BackendClient
import com.rsln.wordflow.data.remote.CreateCollectionRequest
import com.rsln.wordflow.data.remote.IsoDate
import com.rsln.wordflow.data.remote.UpdateCollectionRequest
import com.rsln.wordflow.data.remote.toEntity
import kotlinx.coroutines.flow.Flow

/**
 * Collection repository. Same backend-first write discipline as
 * WordRepository: POSTs/PATCHes/DELETEs propagate through the Go
 * backend, local Room writes happen after the server confirms.
 */
class CollectionRepository(
    private val collectionDao: CollectionDao,
    private val wordDao: WordDao,
    private val backendClient: BackendClient
) {

    // ---------- Reads ----------

    fun getAllCollections(): Flow<List<CollectionEntity>> = collectionDao.getAllCollections()

    fun getActiveCollections(): Flow<List<CollectionEntity>> = collectionDao.getActiveCollections()

    fun getAllCollectionsWithWords(): Flow<List<CollectionWithWords>> =
        collectionDao.getAllCollectionsWithWords()

    fun getCollectionWithWords(id: Long): Flow<CollectionWithWords?> =
        collectionDao.getCollectionWithWords(id)

    fun getActiveCount(): Flow<Int> = collectionDao.getActiveCount()

    suspend fun getCollectionById(id: Long): CollectionEntity? = collectionDao.getCollectionById(id)

    suspend fun getCollectionByName(name: String): CollectionEntity? =
        collectionDao.getCollectionByName(name)

    suspend fun getCollectionIdsForWord(wordId: Long): List<Long> =
        collectionDao.getCollectionIdsForWord(wordId)

    suspend fun getAllCollectionsList(): List<CollectionEntity> =
        collectionDao.getAllCollectionsList()

    suspend fun getAllCrossRefs(): List<WordCollectionCrossRef> =
        collectionDao.getAllCrossRefs()

    // ---------- Writes (backend-first) ----------

    suspend fun insertCollection(collection: CollectionEntity): Long {
        val dto = backendClient.createCollection(
            CreateCollectionRequest(name = collection.name, isActive = collection.isActive)
        )
        return collectionDao.insertCollection(dto.toEntity())
    }

    suspend fun updateCollection(collection: CollectionEntity) {
        val remoteId = collection.remoteId
            ?: throw IllegalStateException("updateCollection on un-synced row id=${collection.id}")
        val dto = backendClient.updateCollection(
            remoteId,
            UpdateCollectionRequest(name = collection.name, isActive = collection.isActive)
        )
        collectionDao.updateCollection(
            collection.copy(
                name = dto.name,
                isActive = dto.isActive,
                updatedAt = IsoDate.parseOrNow(dto.updatedAt)
            )
        )
    }

    suspend fun deleteCollection(id: Long) {
        val collection = collectionDao.getCollectionById(id) ?: return
        collection.remoteId?.let { backendClient.deleteCollection(it) }
        collectionDao.deleteCollectionById(id)
        // Sweep any words that are now orphaned (i.e. in no
        // collection). This was already the existing behaviour in
        // the offline-only version; kept for parity even though the
        // server also tombstones links on its side.
        val orphans = wordDao.getOrphanedWordIds()
        if (orphans.isNotEmpty()) {
            wordDao.deleteWordsByIds(orphans)
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val collection = collectionDao.getCollectionById(id) ?: return
        updateCollection(collection.copy(isActive = active))
    }

    // ---------- Links (M2M) ----------

    /**
     * Link a word to a collection. POSTs the link to the backend
     * (which needs the server-side UUIDs), then inserts the local
     * cross-ref. If either the word or collection doesn't have a
     * remoteId, we fall back to a local-only cross-ref — shouldn't
     * happen in normal flow but keeps the M2M consistent if the
     * user got here via an odd code path.
     */
    suspend fun addWordToCollection(wordId: Long, collectionId: Long) {
        val word = wordDao.getWordById(wordId)
        val collection = collectionDao.getCollectionById(collectionId)
        val wordRemote = word?.remoteId
        val collRemote = collection?.remoteId
        if (wordRemote != null && collRemote != null) {
            try {
                backendClient.linkWord(collRemote, wordRemote)
            } catch (e: com.rsln.wordflow.data.remote.BackendException.Conflict) {
                // Link already existed server-side — fine, we still
                // want the local row to reflect reality.
            }
        }
        collectionDao.insertCrossRef(WordCollectionCrossRef(wordId, collectionId))
    }

    suspend fun removeWordFromCollection(wordId: Long, collectionId: Long) {
        val word = wordDao.getWordById(wordId)
        val collection = collectionDao.getCollectionById(collectionId)
        val wordRemote = word?.remoteId
        val collRemote = collection?.remoteId
        if (wordRemote != null && collRemote != null) {
            backendClient.unlinkWord(collRemote, wordRemote)
        }
        collectionDao.removeCrossRef(wordId, collectionId)
    }
}
