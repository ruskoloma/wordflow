package com.rsln.wordflow.ui.screens.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LearningViewModel(
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val totalCount = wordRepository.getTotalCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val learnedCount = wordRepository.getLearnedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val weekCount = wordRepository.getThisWeekCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val activeCollectionCount = collectionRepository.getActiveCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val practiceAll = settingsDataStore.practiceAll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recentWords = wordRepository.getAllWords()
        .map { it.take(15) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCollections = collectionRepository.getActiveCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePracticeAll(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.set(SettingsDataStore.PRACTICE_ALL, enabled)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LearningViewModel(
                container.wordRepository, container.collectionRepository, container.settingsDataStore
            ) as T
        }
    }
}
