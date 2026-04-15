package com.rsln.wordflow.ui.screens.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class WordsViewModel(
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val words = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) wordRepository.getAllWords()
            else wordRepository.searchWords(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount = wordRepository.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val learnedCount = wordRepository.getLearnedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _deleteTarget = MutableStateFlow<WordEntity?>(null)
    val deleteTarget: StateFlow<WordEntity?> = _deleteTarget

    fun updateSearch(query: String) { _searchQuery.value = query }

    fun confirmDelete(word: WordEntity) { _deleteTarget.value = word }
    fun cancelDelete() { _deleteTarget.value = null }

    fun deleteWord() {
        val target = _deleteTarget.value ?: return
        viewModelScope.launch {
            wordRepository.deleteWord(target)
            _deleteTarget.value = null
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WordsViewModel(container.wordRepository) as T
        }
    }
}
