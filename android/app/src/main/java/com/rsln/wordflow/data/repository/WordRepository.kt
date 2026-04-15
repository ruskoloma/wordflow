package com.rsln.wordflow.data.repository

import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.local.entity.WordWithCollections
import com.rsln.wordflow.data.remote.BackendClient
import com.rsln.wordflow.data.remote.BackendException
import com.rsln.wordflow.data.remote.CreateWordRequest
import com.rsln.wordflow.data.remote.IsoDate
import com.rsln.wordflow.data.remote.UpdateWordRequest
import com.rsln.wordflow.data.remote.toEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Word repository. Reads go to the local Room cache (so widget,
 * notifications, and UI screens work offline). Writes go to the
 * backend first — on success they update Room, on failure they
 * throw a BackendException that ViewModels catch and turn into a
 * user-visible error.
 *
 * Progress updates (showCount / lastShownDate / isLearned) are the
 * one exception: they write to Room immediately with the
 * pendingProgressPush dirty flag set, and get pushed in batches by
 * SyncService.
 */
class WordRepository(
    private val wordDao: WordDao,
    private val backendClient: BackendClient
) {

    // ---------- Reads (local cache) ----------

    fun getAllWords(): Flow<List<WordEntity>> = wordDao.getAllWords()

    fun getAllWordsWithCollections(): Flow<List<WordWithCollections>> = wordDao.getAllWordsWithCollections()

    fun searchWords(query: String): Flow<List<WordEntity>> = wordDao.searchWords(query)

    fun getTotalCount(): Flow<Int> = wordDao.getTotalCount()

    fun getLearnedCount(): Flow<Int> = wordDao.getLearnedCount()

    fun getThisWeekCount(): Flow<Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return wordDao.getThisWeekCount(cal.timeInMillis)
    }

    suspend fun getWordById(id: Long): WordEntity? = wordDao.getWordById(id)

    suspend fun getWordWithCollections(id: Long): WordWithCollections? =
        wordDao.getWordWithCollections(id)

    suspend fun getWordByNormalized(word: String): WordEntity? =
        wordDao.getWordByNormalized(normalize(word))

    suspend fun getAllWordsList(): List<WordEntity> = wordDao.getAllWordsList()

    // ---------- Writes (backend-first) ----------

    /**
     * Create a new word. POSTs to the backend, then inserts the
     * server-confirmed row into Room. Returns the new local Long id.
     *
     * Throws [BackendException] on any failure (no local write
     * happens in that case, so the add-word UI can retry with the
     * same input).
     */
    suspend fun insertWord(word: WordEntity): Long {
        val request = CreateWordRequest(
            originalWord = word.originalWord,
            normalizedWord = word.normalizedWord,
            translation = word.translation,
            exampleUsage = word.exampleUsage,
            explanation = word.explanation,
            pronunciation = word.pronunciation,
            difficulty = word.difficulty,
            isLearned = word.isLearned
        )
        val dto = backendClient.createWord(request)
        return wordDao.insertWord(dto.toEntity())
    }

    /**
     * Update a word. Requires the row to already have a remoteId
     * (it always should, since we only ever insert via the backend).
     * PATCHes the server, then updates Room with the server's
     * authoritative values.
     */
    suspend fun updateWord(word: WordEntity) {
        val remoteId = word.remoteId
            ?: throw IllegalStateException("updateWord called on un-synced row id=${word.id}")
        val request = UpdateWordRequest(
            originalWord = word.originalWord,
            translation = word.translation,
            exampleUsage = word.exampleUsage,
            explanation = word.explanation,
            pronunciation = word.pronunciation,
            difficulty = word.difficulty,
            isLearned = word.isLearned
        )
        val dto = backendClient.updateWord(remoteId, request)
        // Preserve the local Long id and pendingProgressPush flag;
        // everything else comes from the server.
        wordDao.updateWord(
            word.copy(
                originalWord = dto.originalWord,
                normalizedWord = dto.normalizedWord,
                translation = dto.translation,
                exampleUsage = dto.exampleUsage,
                explanation = dto.explanation,
                pronunciation = dto.pronunciation,
                difficulty = dto.difficulty,
                isLearned = dto.isLearned,
                updatedAt = IsoDate.parseOrNow(dto.updatedAt)
            )
        )
    }

    suspend fun deleteWord(word: WordEntity) {
        word.remoteId?.let { backendClient.deleteWord(it) }
        wordDao.deleteWord(word)
    }

    suspend fun deleteWordById(id: Long) {
        val word = wordDao.getWordById(id) ?: return
        word.remoteId?.let { backendClient.deleteWord(it) }
        wordDao.deleteWordById(id)
    }

    // ---------- Progress (local, dirty-flagged, batch-pushed) ----------

    /**
     * Mark a word as learned/unlearned. Writes to Room immediately
     * with pendingProgressPush=true so SyncService flushes it on
     * the next refresh. Does NOT hit the backend synchronously —
     * progress updates are the one "queue and forget" path.
     */
    suspend fun setLearned(id: Long, learned: Boolean) =
        wordDao.setLearnedWithPending(id, learned)

    /** Same pattern: local + dirty flag, flushed in batch later. */
    suspend fun incrementShowCount(id: Long) =
        wordDao.markShownWithPending(id)

    suspend fun getNextWidgetWord(lastWordId: Long): WordEntity? {
        return wordDao.getNextWordAfter(lastWordId)
            ?: wordDao.getFirstWord()
    }

    suspend fun deleteOrphanedWords() {
        val orphans = wordDao.getOrphanedWordIds()
        if (orphans.isNotEmpty()) {
            wordDao.deleteWordsByIds(orphans)
        }
    }

    companion object {
        fun normalize(word: String): String = word.trim().lowercase()
    }
}
