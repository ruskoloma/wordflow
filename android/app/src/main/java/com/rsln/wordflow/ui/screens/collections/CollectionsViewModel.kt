package com.rsln.wordflow.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.CollectionWithWords
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CollectionsViewModel(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    val collectionsWithWords = collectionRepository.getAllCollectionsWithWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _deleteTarget = MutableStateFlow<CollectionWithWords?>(null)
    val deleteTarget: StateFlow<CollectionWithWords?> = _deleteTarget

    fun showCreate() { _showCreateDialog.value = true }
    fun hideCreate() { _showCreateDialog.value = false }

    fun createCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = collectionRepository.getCollectionByName(name)
            if (existing == null) {
                collectionRepository.insertCollection(CollectionEntity(name = name.trim()))
                }
            _showCreateDialog.value = false
        }
    }

    fun toggleActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            collectionRepository.setActive(id, active)
        }
    }

    fun confirmDelete(collection: CollectionWithWords) { _deleteTarget.value = collection }
    fun cancelDelete() { _deleteTarget.value = null }

    fun deleteCollection() {
        val target = _deleteTarget.value ?: return
        viewModelScope.launch {
            collectionRepository.deleteCollection(target.collection.id)
            _deleteTarget.value = null
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CollectionsViewModel(container.collectionRepository) as T
        }
    }
}
