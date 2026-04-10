package com.rsln.wordflow.ui.screens.collectiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.entity.CollectionWithWords
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.remote.SyncManager
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CollectionDetailViewModel(
    private val collectionId: Long,
    private val collectionRepository: CollectionRepository,
    private val wordRepository: WordRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val collectionWithWords = collectionRepository.getCollectionWithWords(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog

    private val _showAddWordDialog = MutableStateFlow(false)
    val showAddWordDialog: StateFlow<Boolean> = _showAddWordDialog

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val allWords = wordRepository.getAllWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun showDelete() { _showDeleteDialog.value = true }
    fun hideDelete() { _showDeleteDialog.value = false }

    fun deleteCollection() {
        viewModelScope.launch {
            collectionRepository.deleteCollection(collectionId)
            _deleted.value = true
            syncManager.notifyLocalChange()
        }
    }

    fun showAddWord() { _showAddWordDialog.value = true }
    fun hideAddWord() { _showAddWordDialog.value = false }

    fun addWordToCollection(wordId: Long) {
        viewModelScope.launch {
            collectionRepository.addWordToCollection(wordId, collectionId)
            syncManager.notifyLocalChange()
        }
    }

    fun removeWordFromCollection(wordId: Long) {
        viewModelScope.launch {
            collectionRepository.removeWordFromCollection(wordId, collectionId)
            syncManager.notifyLocalChange()
        }
    }

    fun toggleActive(active: Boolean) {
        viewModelScope.launch {
            collectionRepository.setActive(collectionId, active)
            syncManager.notifyLocalChange()
        }
    }

    class Factory(
        private val collectionId: Long,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CollectionDetailViewModel(
                collectionId, container.collectionRepository, container.wordRepository, container.syncManager
            ) as T
        }
    }
}
