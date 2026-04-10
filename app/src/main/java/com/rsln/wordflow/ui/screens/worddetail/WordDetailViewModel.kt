package com.rsln.wordflow.ui.screens.worddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.local.entity.WordWithCollections
import com.rsln.wordflow.data.remote.SyncManager
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WordDetailViewModel(
    private val wordId: Long,
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _word = MutableStateFlow<WordWithCollections?>(null)
    val word: StateFlow<WordWithCollections?> = _word

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _editTranslation = MutableStateFlow("")
    val editTranslation: StateFlow<String> = _editTranslation

    private val _editExamples = MutableStateFlow("")
    val editExamples: StateFlow<String> = _editExamples

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog

    private val _showCollectionDialog = MutableStateFlow(false)
    val showCollectionDialog: StateFlow<Boolean> = _showCollectionDialog

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val allCollections = collectionRepository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { loadWord() }

    fun loadWord() {
        viewModelScope.launch {
            _word.value = wordRepository.getWordWithCollections(wordId)
            _word.value?.let {
                _editTranslation.value = it.word.translation
                _editExamples.value = it.word.exampleUsage
            }
        }
    }

    fun startEdit() { _isEditing.value = true }
    fun cancelEdit() {
        _isEditing.value = false
        _word.value?.let {
            _editTranslation.value = it.word.translation
            _editExamples.value = it.word.exampleUsage
        }
    }

    fun updateTranslation(text: String) { _editTranslation.value = text }
    fun updateExamples(text: String) { _editExamples.value = text }

    fun saveEdit() {
        viewModelScope.launch {
            _word.value?.let { wc ->
                wordRepository.updateWord(wc.word.copy(
                    translation = _editTranslation.value,
                    exampleUsage = _editExamples.value
                ))
                _isEditing.value = false
                loadWord()
                syncManager.notifyLocalChange()
            }
        }
    }

    fun toggleLearned() {
        viewModelScope.launch {
            _word.value?.let { wc ->
                wordRepository.setLearned(wc.word.id, !wc.word.isLearned)
                loadWord()
                syncManager.notifyLocalChange()
            }
        }
    }

    fun showDelete() { _showDeleteDialog.value = true }
    fun hideDelete() { _showDeleteDialog.value = false }

    fun deleteWord() {
        viewModelScope.launch {
            wordRepository.deleteWordById(wordId)
            _deleted.value = true
            syncManager.notifyLocalChange()
        }
    }

    fun showCollections() { _showCollectionDialog.value = true }
    fun hideCollections() { _showCollectionDialog.value = false }

    fun addToCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.addWordToCollection(wordId, collectionId)
            loadWord()
            syncManager.notifyLocalChange()
        }
    }

    fun removeFromCollection(collectionId: Long) {
        viewModelScope.launch {
            collectionRepository.removeWordFromCollection(wordId, collectionId)
            loadWord()
            syncManager.notifyLocalChange()
        }
    }

    class Factory(
        private val wordId: Long,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WordDetailViewModel(
                wordId, container.wordRepository, container.collectionRepository, container.syncManager
            ) as T
        }
    }
}
