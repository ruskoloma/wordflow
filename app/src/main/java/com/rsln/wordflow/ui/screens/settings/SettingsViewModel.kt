package com.rsln.wordflow.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.remote.DynamoDbService
import com.rsln.wordflow.data.remote.OpenRouterService
import com.rsln.wordflow.data.remote.SyncManager
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.net.Uri

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val openRouterService: OpenRouterService,
    private val dynamoDbService: DynamoDbService,
    private val syncManager: SyncManager,
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    val apiKey = settingsDataStore.apiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val aiModel = settingsDataStore.aiModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "google/gemini-2.0-flash-001")
    val wordsPerWeek = settingsDataStore.wordsPerWeek.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
    val notificationsPerDay = settingsDataStore.notificationsPerDay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val activeStartHour = settingsDataStore.activeStartHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)
    val activeEndHour = settingsDataStore.activeEndHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22)
    val widgetRefreshSeconds = settingsDataStore.widgetRefreshSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 35)
    val nickname = settingsDataStore.nickname.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val awsAccessKey = settingsDataStore.awsAccessKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val awsSecretKey = settingsDataStore.awsSecretKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val awsRegion = settingsDataStore.awsRegion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "us-east-1")
    val cloudSyncEnabled = settingsDataStore.cloudSyncEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun setApiKey(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.API_KEY, value) }
    fun setAiModel(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.AI_MODEL, value) }
    fun setWordsPerWeek(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.WORDS_PER_WEEK, value) }
    fun setNotificationsPerDay(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.NOTIFICATIONS_PER_DAY, value) }
    fun setActiveStartHour(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.ACTIVE_START_HOUR, value) }
    fun setActiveEndHour(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.ACTIVE_END_HOUR, value) }
    fun setWidgetRefreshSeconds(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.WIDGET_REFRESH_SECONDS, value) }
    fun setNickname(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.NICKNAME, value) }
    fun setAwsAccessKey(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.AWS_ACCESS_KEY, value) }
    fun setAwsSecretKey(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.AWS_SECRET_KEY, value) }
    fun setAwsRegion(value: String) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.AWS_REGION, value) }
    fun setCloudSyncEnabled(value: Boolean) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.CLOUD_SYNC_ENABLED, value) }

    fun testAiConnection() {
        _isTesting.value = true
        _testResult.value = null
        viewModelScope.launch {
            val result = openRouterService.testConnection()
            _testResult.value = result.fold(
                onSuccess = { it },
                onFailure = { "Error: ${it.message}" }
            )
            _isTesting.value = false
        }
    }

    fun pushToCloud() {
        _isSyncing.value = true
        viewModelScope.launch {
            val result = syncManager.pushNow()
            _syncMessage.value = result.fold(
                onSuccess = { "Pushed to cloud" },
                onFailure = { "Push failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun pullFromCloud() {
        _isSyncing.value = true
        viewModelScope.launch {
            val result = syncManager.pullNow()
            _syncMessage.value = result.fold(
                onSuccess = { it },
                onFailure = { "Pull failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val words = wordRepository.getAllWordsList()
                val output = StringBuilder()
                output.appendLine("word,translation,examples,difficulty,learned")
                words.forEach { w ->
                    val examples = w.exampleUsage.replace("\"", "\"\"")
                    output.appendLine("\"${w.originalWord}\",\"${w.translation}\",\"$examples\",${w.difficulty},${w.isLearned}")
                }
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(output.toString().toByteArray())
                }
                _syncMessage.value = "Exported ${words.size} words"
            } catch (e: Exception) {
                _syncMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                var count = 0
                val lines = mutableListOf<String>()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    reader.readLine() // skip header
                    reader.forEachLine { lines.add(it) }
                }
                for (line in lines) {
                    val parts = parseCsvLine(line)
                    if (parts.size >= 2) {
                        val word = parts[0]
                        val translation = parts[1]
                        val examples = parts.getOrElse(2) { "" }
                        val difficulty = parts.getOrElse(3) { "5" }.toIntOrNull() ?: 5
                        val learned = parts.getOrElse(4) { "false" }.toBooleanStrictOrNull() ?: false

                        val normalized = WordRepository.normalize(word)
                        val existing = wordRepository.getWordByNormalized(normalized)
                        if (existing == null) {
                            wordRepository.insertWord(WordEntity(
                                originalWord = word,
                                normalizedWord = normalized,
                                translation = translation,
                                exampleUsage = examples,
                                difficulty = difficulty,
                                isLearned = learned
                            ))
                            count++
                        }
                    }
                }
                _syncMessage.value = "Imported $count new words"
            } catch (e: Exception) {
                _syncMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    fun clearTestResult() { _testResult.value = null }
    fun clearSyncMessage() { _syncMessage.value = null }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                container.settingsDataStore,
                container.openRouterService,
                container.dynamoDbService,
                container.syncManager,
                container.wordRepository,
                container.collectionRepository
            ) as T
        }
    }
}
