package com.rsln.wordflow.ui.screens.addword

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.remote.AiModel
import com.rsln.wordflow.data.remote.AiTranslationResult
import com.rsln.wordflow.data.remote.OpenRouterService
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DraftStatus { IDLE, LOADING, READY, EXISTING, SAVED, ERROR }

data class WordDraft(
    val input: String,
    val status: DraftStatus = DraftStatus.IDLE,
    val translation: String = "",
    val examples: String = "",
    val pronunciation: String = "",
    val explanation: String = "",
    val difficulty: Int = 5,
    val suggestion: String = "",
    val suggestionTranslation: String = "",
    val matchedCollections: List<String> = emptyList(),
    val suggestedCollections: List<String> = emptyList(),
    val selectedCollectionIds: MutableList<Long> = mutableListOf(),
    val newCollectionNames: MutableList<String> = mutableListOf(),
    val existingWordId: Long? = null,
    val savedWordId: Long? = null,
    val errorMessage: String = ""
)

class AddWordViewModel(
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository,
    private val openRouterService: OpenRouterService,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    val drafts = mutableStateListOf<WordDraft>()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _selectedAiModel = MutableStateFlow(AiModel.DEFAULT_MODEL_ID)
    val selectedAiModel: StateFlow<String> = _selectedAiModel

    // Pre-applied collection name for all drafts (from generate flow)
    private val _preAppliedCollection = MutableStateFlow<String?>(null)
    val preAppliedCollection: StateFlow<String?> = _preAppliedCollection

    val allCollections = collectionRepository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsDataStore.addWordDraft.first().let { saved ->
                if (saved.isNotBlank()) _inputText.value = saved
            }
        }
        viewModelScope.launch {
            val lastModel = settingsDataStore.lastAiModel.first()
            val defaultModel = settingsDataStore.defaultAiModel.first()
            _selectedAiModel.value = AiModel.normalize(lastModel.ifBlank { defaultModel })
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
        viewModelScope.launch {
            settingsDataStore.set(SettingsDataStore.ADD_WORD_DRAFT, text)
        }
    }

    fun selectAiModel(model: String) {
        val normalized = AiModel.normalize(model)
        _selectedAiModel.value = normalized
        viewModelScope.launch {
            settingsDataStore.set(SettingsDataStore.LAST_AI_MODEL, normalized)
        }
    }

    fun translate() {
        val lines = _inputText.value.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return

        drafts.clear()
        _preAppliedCollection.value = null
        lines.forEach { line ->
            drafts.add(WordDraft(input = line, status = DraftStatus.LOADING))
        }

        _isTranslating.value = true

        viewModelScope.launch {
            val model = AiModel.normalize(_selectedAiModel.value)
            settingsDataStore.set(SettingsDataStore.LAST_AI_MODEL, model)
            val existingCollections = collectionRepository.getAllCollectionsList().map { it.name }

            drafts.forEachIndexed { index, draft ->
                val normalized = WordRepository.normalize(draft.input)
                val existing = wordRepository.getWordByNormalized(normalized)

                if (existing != null) {
                    val collectionIds = collectionRepository.getCollectionIdsForWord(existing.id)
                    drafts[index] = draft.copy(
                        status = DraftStatus.EXISTING,
                        translation = existing.translation,
                        examples = existing.exampleUsage,
                        pronunciation = existing.pronunciation,
                        explanation = existing.explanation,
                        difficulty = existing.difficulty,
                        existingWordId = existing.id,
                        savedWordId = existing.id,
                        selectedCollectionIds = collectionIds.toMutableList()
                    )
                } else {
                    val result = openRouterService.translateWord(draft.input, existingCollections, model)
                    result.fold(
                        onSuccess = { ai ->
                            val matchedIds = mutableListOf<Long>()
                            ai.matchedCollections.forEach { name ->
                                collectionRepository.getCollectionByName(name)?.let {
                                    matchedIds.add(it.id)
                                }
                            }
                            drafts[index] = draft.copy(
                                status = DraftStatus.READY,
                                translation = ai.translation,
                                examples = ai.examples.joinToString("\n"),
                                pronunciation = ai.pronunciation,
                                explanation = ai.reason,
                                difficulty = ai.difficulty,
                                suggestion = ai.suggestion,
                                suggestionTranslation = ai.suggestionTranslation,
                                matchedCollections = ai.matchedCollections,
                                suggestedCollections = ai.suggestedCollections,
                                selectedCollectionIds = matchedIds
                            )
                        },
                        onFailure = { error ->
                            drafts[index] = draft.copy(
                                status = DraftStatus.ERROR,
                                errorMessage = error.message ?: "Translation failed"
                            )
                        }
                    )
                }
            }
            _isTranslating.value = false
        }
    }

    /**
     * Load pre-generated drafts from an AI collection generation.
     * Called from navigation when coming from "Generate Collection" flow.
     */
    fun loadGeneratedDrafts(collectionName: String, words: List<GeneratedWord>) {
        drafts.clear()
        _inputText.value = ""
        _preAppliedCollection.value = collectionName

        words.forEach { gw ->
            drafts.add(
                WordDraft(
                    input = gw.word,
                    status = DraftStatus.READY,
                    translation = gw.translation,
                    examples = gw.examples,
                    pronunciation = gw.pronunciation,
                    explanation = gw.reason,
                    difficulty = gw.difficulty,
                    newCollectionNames = mutableListOf(collectionName)
                )
            )
        }
    }

    fun updateDraft(index: Int, draft: WordDraft) {
        if (index in drafts.indices) {
            drafts[index] = draft
        }
    }

    fun removeDraft(index: Int) {
        if (index in drafts.indices) {
            drafts.removeAt(index)
        }
    }

    fun toggleCollectionForDraft(index: Int, collectionId: Long) {
        if (index !in drafts.indices) return
        val draft = drafts[index]
        val ids = draft.selectedCollectionIds.toMutableList()
        if (collectionId in ids) ids.remove(collectionId) else ids.add(collectionId)
        drafts[index] = draft.copy(selectedCollectionIds = ids)
    }

    fun addNewCollectionToDraft(index: Int, name: String) {
        if (index !in drafts.indices) return
        val draft = drafts[index]
        if (name.isBlank() || name in draft.newCollectionNames) return
        val names = draft.newCollectionNames.toMutableList()
        names.add(name)
        drafts[index] = draft.copy(newCollectionNames = names)
    }

    fun removeNewCollectionFromDraft(index: Int, name: String) {
        if (index !in drafts.indices) return
        val draft = drafts[index]
        val names = draft.newCollectionNames.toMutableList()
        names.remove(name)
        drafts[index] = draft.copy(newCollectionNames = names)
    }

    fun saveAll() {
        viewModelScope.launch {
            var savedCount = 0
            drafts.forEachIndexed { index, draft ->
                if (draft.status == DraftStatus.READY || draft.status == DraftStatus.EXISTING || draft.status == DraftStatus.SAVED) {
                    if (draft.translation.isBlank()) return@forEachIndexed

                    try {
                        val wordId = if (draft.existingWordId != null) {
                            val existing = wordRepository.getWordById(draft.existingWordId)
                            if (existing != null) {
                                wordRepository.updateWord(existing.copy(
                                    translation = draft.translation,
                                    exampleUsage = draft.examples,
                                    pronunciation = draft.pronunciation,
                                    explanation = draft.explanation,
                                    difficulty = draft.difficulty
                                ))
                            }
                            draft.existingWordId
                        } else if (draft.savedWordId != null) {
                            val existing = wordRepository.getWordById(draft.savedWordId)
                            if (existing != null) {
                                wordRepository.updateWord(existing.copy(
                                    translation = draft.translation,
                                    exampleUsage = draft.examples,
                                    pronunciation = draft.pronunciation,
                                    explanation = draft.explanation,
                                    difficulty = draft.difficulty
                                ))
                            }
                            draft.savedWordId
                        } else {
                            wordRepository.insertWord(
                                WordEntity(
                                    originalWord = draft.input,
                                    normalizedWord = WordRepository.normalize(draft.input),
                                    translation = draft.translation,
                                    exampleUsage = draft.examples,
                                    pronunciation = draft.pronunciation,
                                    explanation = draft.explanation,
                                    difficulty = draft.difficulty
                                )
                            )
                        }

                        // Assign existing collections
                        draft.selectedCollectionIds.forEach { collId ->
                            collectionRepository.addWordToCollection(wordId, collId)
                        }

                        // Create new collections and link
                        draft.newCollectionNames.forEach { name ->
                            var collection = collectionRepository.getCollectionByName(name)
                            if (collection == null) {
                                val id = collectionRepository.insertCollection(
                                    CollectionEntity(name = name)
                                )
                                collection = collectionRepository.getCollectionById(id)
                            }
                            collection?.let {
                                collectionRepository.addWordToCollection(wordId, it.id)
                            }
                        }

                        drafts[index] = draft.copy(status = DraftStatus.SAVED, savedWordId = wordId)
                        savedCount++
                    } catch (e: Exception) {
                        drafts[index] = draft.copy(
                            status = DraftStatus.ERROR,
                            errorMessage = e.message ?: "Save failed"
                        )
                    }
                }
            }
            if (savedCount > 0) {
                _message.value = "Saved $savedCount word${if (savedCount > 1) "s" else ""}"
                _inputText.value = ""
                _preAppliedCollection.value = null
                settingsDataStore.remove(SettingsDataStore.ADD_WORD_DRAFT)
                drafts.clear()
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun clearDrafts() {
        drafts.clear()
        _inputText.value = ""
        _preAppliedCollection.value = null
        viewModelScope.launch {
            settingsDataStore.remove(SettingsDataStore.ADD_WORD_DRAFT)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddWordViewModel(
                container.wordRepository,
                container.collectionRepository,
                container.openRouterService,
                container.settingsDataStore
            ) as T
        }
    }
}

data class GeneratedWord(
    val word: String,
    val translation: String,
    val examples: String,
    val pronunciation: String,
    val reason: String,
    val difficulty: Int
)
