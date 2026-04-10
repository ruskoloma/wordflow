package com.rsln.wordflow.data.remote

import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.local.entity.CollectionEntity
import com.rsln.wordflow.data.local.entity.WordCollectionCrossRef
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first sync manager.
 * - All writes go to local Room DB first (instant, works offline).
 * - After each local write, a debounced push is scheduled.
 * - On app launch, pull from cloud and merge.
 * - Merge strategy: union of local + remote, newest wins per-item.
 */
class SyncManager(
    private val wordRepository: WordRepository,
    private val collectionRepository: CollectionRepository,
    private val dynamoDbService: DynamoDbService,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private var pushJob: Job? = null
    private val PUSH_DEBOUNCE_MS = 3000L

    enum class SyncStatus { Idle, Pushing, Pulling, Error }

    private fun hasNickname(): Boolean {
        // Quick blocking check — only called from IO dispatcher
        return runBlocking { settingsDataStore.nickname.first().isNotBlank() }
    }

    /**
     * Called on app launch. Pulls remote data and merges into local DB.
     */
    fun pullOnLaunch() {
        scope.launch {
            val nickname = settingsDataStore.nickname.first()
            if (nickname.isBlank()) return@launch

            _syncStatus.value = SyncStatus.Pulling
            try {
                val result = dynamoDbService.pullData()
                result.fold(
                    onSuccess = { cloud -> mergeCloudIntoLocal(cloud) },
                    onFailure = { /* silent fail — offline is fine */ }
                )
            } catch (_: Exception) { }
            _syncStatus.value = SyncStatus.Idle
        }
    }

    /**
     * Schedule a debounced push after a local change.
     */
    fun notifyLocalChange() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushToCloud()
        }
    }

    /**
     * Force an immediate push.
     */
    suspend fun pushNow(): Result<Unit> {
        pushJob?.cancel()
        return pushToCloud()
    }

    /**
     * Force an immediate pull + merge.
     */
    suspend fun pullNow(): Result<String> {
        val nickname = settingsDataStore.nickname.first()
        if (nickname.isBlank()) return Result.failure(Exception("No nickname set"))

        _syncStatus.value = SyncStatus.Pulling
        return try {
            val result = dynamoDbService.pullData()
            result.fold(
                onSuccess = { cloud ->
                    val counts = mergeCloudIntoLocal(cloud)
                    _syncStatus.value = SyncStatus.Idle
                    Result.success("Pulled ${counts.first} words, ${counts.second} collections")
                },
                onFailure = {
                    _syncStatus.value = SyncStatus.Idle
                    Result.failure(it)
                }
            )
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Idle
            Result.failure(e)
        }
    }

    private suspend fun pushToCloud(): Result<Unit> = mutex.withLock {
        val nickname = settingsDataStore.nickname.first()
        if (nickname.isBlank()) return Result.success(Unit)

        _syncStatus.value = SyncStatus.Pushing
        return try {
            val words = wordRepository.getAllWordsList()
            val collections = collectionRepository.getAllCollectionsList()
            val crossRefs = collectionRepository.getAllCrossRefs()

            val result = dynamoDbService.pushData(words, collections, crossRefs)
            _syncStatus.value = SyncStatus.Idle
            result
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Idle
            Result.failure(e)
        }
    }

    /**
     * Merge remote data into local DB.
     * - New remote words (by normalizedWord) are inserted.
     * - Existing words are updated if remote is newer (by addedDate).
     * - New remote collections (by name) are inserted.
     * - Cross-refs are union-merged.
     * Returns (wordsAdded, collectionsAdded).
     */
    private suspend fun mergeCloudIntoLocal(cloud: DynamoDbService.CloudData): Pair<Int, Int> = mutex.withLock {
        // Restore API key from cloud if local is empty
        if (cloud.apiKey.isNotBlank()) {
            val localKey = settingsDataStore.apiKey.first()
            if (localKey.isBlank()) {
                settingsDataStore.set(SettingsDataStore.API_KEY, cloud.apiKey)
            }
        }

        var wordsAdded = 0
        var collectionsAdded = 0

        // Build a map of local words by normalizedWord for fast lookup
        val localWords = wordRepository.getAllWordsList().associateBy { it.normalizedWord }
        // Map remote word IDs to new local word IDs
        val wordIdMap = mutableMapOf<Long, Long>()

        for (remoteWord in cloud.words) {
            val localWord = localWords[remoteWord.normalizedWord]
            if (localWord == null) {
                // New word — insert with id=0 to get a new auto-generated ID
                val newId = wordRepository.insertWord(remoteWord.copy(id = 0))
                wordIdMap[remoteWord.id] = newId
                wordsAdded++
            } else {
                // Existing — update if remote is newer
                wordIdMap[remoteWord.id] = localWord.id
                if (remoteWord.addedDate > localWord.addedDate) {
                    wordRepository.updateWord(localWord.copy(
                        translation = remoteWord.translation,
                        exampleUsage = remoteWord.exampleUsage,
                        explanation = remoteWord.explanation,
                        pronunciation = remoteWord.pronunciation,
                        difficulty = remoteWord.difficulty,
                        isLearned = remoteWord.isLearned,
                        showCount = maxOf(localWord.showCount, remoteWord.showCount),
                        lastShownDate = maxOf(localWord.lastShownDate, remoteWord.lastShownDate)
                    ))
                }
            }
        }

        // Collections — merge by name
        val localCollections = collectionRepository.getAllCollectionsList().associateBy { it.name.lowercase().trim() }
        val collectionIdMap = mutableMapOf<Long, Long>()

        for (remoteCol in cloud.collections) {
            val key = remoteCol.name.lowercase().trim()
            val localCol = localCollections[key]
            if (localCol == null) {
                val newId = collectionRepository.insertCollection(remoteCol.copy(id = 0))
                collectionIdMap[remoteCol.id] = newId
                collectionsAdded++
            } else {
                collectionIdMap[remoteCol.id] = localCol.id
            }
        }

        // Cross-refs — union merge with ID mapping
        for (ref in cloud.crossRefs) {
            val localWordId = wordIdMap[ref.wordId] ?: continue
            val localColId = collectionIdMap[ref.collectionId] ?: continue
            collectionRepository.addWordToCollection(localWordId, localColId)
        }

        Pair(wordsAdded, collectionsAdded)
    }
}
