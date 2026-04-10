package com.rsln.wordflow.data.repository

import com.rsln.wordflow.data.local.dao.WordDao
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.local.entity.WordWithCollections
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class WordRepository(private val wordDao: WordDao) {

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

    suspend fun getWordWithCollections(id: Long): WordWithCollections? = wordDao.getWordWithCollections(id)

    suspend fun getWordByNormalized(word: String): WordEntity? =
        wordDao.getWordByNormalized(normalize(word))

    suspend fun insertWord(word: WordEntity): Long = wordDao.insertWord(word)

    suspend fun updateWord(word: WordEntity) = wordDao.updateWord(word)

    suspend fun deleteWord(word: WordEntity) = wordDao.deleteWord(word)

    suspend fun deleteWordById(id: Long) = wordDao.deleteWordById(id)

    suspend fun setLearned(id: Long, learned: Boolean) = wordDao.setLearned(id, learned)

    suspend fun incrementShowCount(id: Long) = wordDao.incrementShowCount(id)

    suspend fun getNextWidgetWord(lastWordId: Long): WordEntity? {
        // Simple round-robin: pick the word with the next ID after lastWordId,
        // wrap to the first word if we're at the end
        return wordDao.getNextWordAfter(lastWordId)
            ?: wordDao.getFirstWord()
    }

    suspend fun getAllWordsList(): List<WordEntity> = wordDao.getAllWordsList()

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
