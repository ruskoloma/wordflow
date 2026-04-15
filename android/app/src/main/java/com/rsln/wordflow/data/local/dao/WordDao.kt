package com.rsln.wordflow.data.local.dao

import androidx.room.*
import com.rsln.wordflow.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM words ORDER BY addedDate DESC")
    fun getAllWords(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE normalizedWord = :normalized LIMIT 1")
    suspend fun getWordByNormalized(normalized: String): WordEntity?

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordWithCollections(id: Long): WordWithCollections?

    @Transaction
    @Query("SELECT * FROM words ORDER BY addedDate DESC")
    fun getAllWordsWithCollections(): Flow<List<WordWithCollections>>

    @Query("SELECT * FROM words WHERE originalWord LIKE '%' || :query || '%' OR translation LIKE '%' || :query || '%' ORDER BY addedDate DESC")
    fun searchWords(query: String): Flow<List<WordEntity>>

    @Query("SELECT COUNT(*) FROM words")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isLearned = 1")
    fun getLearnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE addedDate >= :weekStart")
    fun getThisWeekCount(weekStart: Long): Flow<Int>

    // Round-robin: pick word with next higher ID, wrap to lowest if at end
    @Query("SELECT * FROM words WHERE id > :lastId ORDER BY id ASC LIMIT 1")
    suspend fun getNextWordAfter(lastId: Long): WordEntity?

    @Query("SELECT * FROM words ORDER BY id ASC LIMIT 1")
    suspend fun getFirstWord(): WordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity): Long

    @Update
    suspend fun updateWord(word: WordEntity)

    @Delete
    suspend fun deleteWord(word: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteWordById(id: Long)

    @Query("UPDATE words SET showCount = showCount + 1, lastShownDate = :timestamp WHERE id = :id")
    suspend fun incrementShowCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE words SET isLearned = :learned WHERE id = :id")
    suspend fun setLearned(id: Long, learned: Boolean)

    @Query("SELECT * FROM words ORDER BY addedDate DESC")
    suspend fun getAllWordsList(): List<WordEntity>

    @Query("""
        SELECT w.id FROM words w
        LEFT JOIN word_collection_cross_ref cr ON w.id = cr.wordId
        WHERE cr.wordId IS NULL
    """)
    suspend fun getOrphanedWordIds(): List<Long>

    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteWordsByIds(ids: List<Long>)

    // ---------- Phase 11: sync metadata helpers --------------------
    // These are used by the BackendClient / SyncService in Phase 13.
    // The repositories still call the legacy methods above for now.

    @Query("SELECT * FROM words WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): WordEntity?

    @Query("UPDATE words SET remoteId = :remoteId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM words WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    // Snapshot of all rows with offline-only progress updates that
    // need to be flushed to the backend. Does NOT filter by remoteId
    // because a word without a remoteId wouldn't have progress
    // either — but we include it for safety (server ignores unknown ids).
    @Query("SELECT * FROM words WHERE pendingProgressPush = 1")
    suspend fun getWordsWithPendingProgress(): List<WordEntity>

    @Query("UPDATE words SET pendingProgressPush = 0 WHERE id IN (:ids)")
    suspend fun clearPendingProgressForIds(ids: List<Long>)

    @Query("""
        UPDATE words
        SET showCount = showCount + 1,
            lastShownDate = :timestamp,
            pendingProgressPush = 1
        WHERE id = :id
    """)
    suspend fun markShownWithPending(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE words SET isLearned = :learned, pendingProgressPush = 1 WHERE id = :id")
    suspend fun setLearnedWithPending(id: Long, learned: Boolean)

    // Wipe everything for the authenticated user — called on sign-out
    // to prevent one account's data leaking into another.
    @Query("DELETE FROM words")
    suspend fun deleteAllWords()
}
