package com.rsln.wordflow.data.repository

import com.rsln.wordflow.data.local.dao.CollectionDao
import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.*
import kotlinx.coroutines.flow.Flow

class CollectionRepository(
    private val collectionDao: CollectionDao,
    private val wordDao: WordDao
) {

    fun getAllCollections(): Flow<List<CollectionEntity>> = collectionDao.getAllCollections()

    fun getActiveCollections(): Flow<List<CollectionEntity>> = collectionDao.getActiveCollections()

    fun getAllCollectionsWithWords(): Flow<List<CollectionWithWords>> = collectionDao.getAllCollectionsWithWords()

    fun getCollectionWithWords(id: Long): Flow<CollectionWithWords?> = collectionDao.getCollectionWithWords(id)

    fun getActiveCount(): Flow<Int> = collectionDao.getActiveCount()

    suspend fun getCollectionById(id: Long): CollectionEntity? = collectionDao.getCollectionById(id)

    suspend fun getCollectionByName(name: String): CollectionEntity? = collectionDao.getCollectionByName(name)

    suspend fun insertCollection(collection: CollectionEntity): Long = collectionDao.insertCollection(collection)

    suspend fun updateCollection(collection: CollectionEntity) = collectionDao.updateCollection(collection)

    suspend fun deleteCollection(id: Long) {
        collectionDao.deleteCollectionById(id)
        wordDao.let { dao ->
            val orphans = dao.getOrphanedWordIds()
            if (orphans.isNotEmpty()) {
                dao.deleteWordsByIds(orphans)
            }
        }
    }

    suspend fun setActive(id: Long, active: Boolean) = collectionDao.setActive(id, active)

    suspend fun addWordToCollection(wordId: Long, collectionId: Long) {
        collectionDao.insertCrossRef(WordCollectionCrossRef(wordId, collectionId))
    }

    suspend fun removeWordFromCollection(wordId: Long, collectionId: Long) {
        collectionDao.removeCrossRef(wordId, collectionId)
    }

    suspend fun getCollectionIdsForWord(wordId: Long): List<Long> =
        collectionDao.getCollectionIdsForWord(wordId)

    suspend fun getAllCollectionsList(): List<CollectionEntity> = collectionDao.getAllCollectionsList()

    suspend fun getAllCrossRefs(): List<WordCollectionCrossRef> = collectionDao.getAllCrossRefs()
}
