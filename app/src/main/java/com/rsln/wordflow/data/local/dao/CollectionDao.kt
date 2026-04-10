package com.rsln.wordflow.data.local.dao

import androidx.room.*
import com.rsln.wordflow.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCollectionByName(name: String): CollectionEntity?

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :id")
    fun getCollectionWithWords(id: Long): Flow<CollectionWithWords?>

    @Transaction
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollectionsWithWords(): Flow<List<CollectionWithWords>>

    @Query("SELECT COUNT(*) FROM collections WHERE isActive = 1")
    fun getActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionById(id: Long)

    @Query("UPDATE collections SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: WordCollectionCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: WordCollectionCrossRef)

    @Query("DELETE FROM word_collection_cross_ref WHERE wordId = :wordId AND collectionId = :collectionId")
    suspend fun removeCrossRef(wordId: Long, collectionId: Long)

    @Query("SELECT collectionId FROM word_collection_cross_ref WHERE wordId = :wordId")
    suspend fun getCollectionIdsForWord(wordId: Long): List<Long>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    suspend fun getAllCollectionsList(): List<CollectionEntity>

    @Query("SELECT * FROM word_collection_cross_ref")
    suspend fun getAllCrossRefs(): List<WordCollectionCrossRef>
}
