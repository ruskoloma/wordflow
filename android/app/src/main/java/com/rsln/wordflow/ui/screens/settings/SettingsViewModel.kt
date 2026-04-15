package com.rsln.wordflow.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.auth.AuthManager
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.data.sync.SyncService
import com.rsln.wordflow.di.AppContainer
import com.rsln.wordflow.updater.AppUpdater
import com.rsln.wordflow.updater.ReleaseInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Settings VM — notification schedule, widget prefs, CSV
 * import/export, app updates, and the sign-out control.
 *
 * OpenRouter API key, model picker, "Sync now" and "Test backend"
 * buttons are all gone — the backend owns the model, sync runs
 * automatically on app launch / sign-in, and the dev testConnection
 * path isn't worth surfacing once the login flow is proving
 * connectivity end-to-end.
 */
class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository,
    private val authManager: AuthManager,
    private val syncService: SyncService
) : ViewModel() {

    val wordsPerWeek = settingsDataStore.wordsPerWeek.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
    val notificationsPerDay = settingsDataStore.notificationsPerDay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val activeStartHour = settingsDataStore.activeStartHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)
    val activeEndHour = settingsDataStore.activeEndHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22)
    val widgetRefreshSeconds = settingsDataStore.widgetRefreshSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 35)

    val currentEmail: StateFlow<String?> = authManager.currentEmail

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    fun setWordsPerWeek(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.WORDS_PER_WEEK, value) }
    fun setNotificationsPerDay(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.NOTIFICATIONS_PER_DAY, value) }
    fun setActiveStartHour(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.ACTIVE_START_HOUR, value) }
    fun setActiveEndHour(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.ACTIVE_END_HOUR, value) }
    fun setWidgetRefreshSeconds(value: Int) = viewModelScope.launch { settingsDataStore.set(SettingsDataStore.WIDGET_REFRESH_SECONDS, value) }

    // ---------- Auth ----------

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            syncService.clearLocalCache()
            _syncMessage.value = "Signed out"
        }
    }

    // ---------- CSV import / export ----------

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
                            try {
                                wordRepository.insertWord(
                                    WordEntity(
                                        originalWord = word,
                                        normalizedWord = normalized,
                                        translation = translation,
                                        exampleUsage = examples,
                                        difficulty = difficulty,
                                        isLearned = learned
                                    )
                                )
                                count++
                            } catch (_: Exception) {
                                // Skip rows the backend rejects; keep importing the rest.
                            }
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

    // --- App Update ---
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data class Available(val release: ReleaseInfo, val currentVersion: String) : UpdateState()
        data object UpToDate : UpdateState()
        data object Downloading : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    fun checkForUpdate(context: Context) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val updater = AppUpdater(context)
            val result = updater.checkForUpdate()
            result.fold(
                onSuccess = { release ->
                    if (release == null) {
                        _updateState.value = UpdateState.UpToDate
                    } else {
                        val current = updater.getCurrentVersion()
                        if (updater.isNewerVersion(release.versionName, current)) {
                            _updateState.value = UpdateState.Available(release, current)
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    }
                },
                onFailure = {
                    _updateState.value = UpdateState.Error(it.message ?: "Check failed")
                }
            )
        }
    }

    fun downloadUpdate(context: Context, release: ReleaseInfo) {
        val url = release.apkUrl ?: return
        _updateState.value = UpdateState.Downloading
        val updater = AppUpdater(context)
        updater.downloadAndInstall(url, release.versionName)
    }

    fun dismissUpdate() { _updateState.value = UpdateState.Idle }

    fun getCurrentVersion(context: Context): String = AppUpdater(context).getCurrentVersion()

    fun clearSyncMessage() { _syncMessage.value = null }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                container.settingsDataStore,
                container.wordRepository,
                container.collectionRepository,
                container.authManager,
                container.syncService
            ) as T
        }
    }
}
