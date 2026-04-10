package com.rsln.wordflow.di

import android.content.Context
import com.rsln.wordflow.data.local.AppDatabase
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.remote.DynamoDbService
import com.rsln.wordflow.data.remote.OpenRouterService
import com.rsln.wordflow.data.remote.SyncManager
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository

import com.rsln.wordflow.ui.screens.addword.GeneratedWord

class AppContainer(context: Context) {
    // Shared state for passing generated words from Collections -> Add screen
    var pendingGeneratedWords: List<GeneratedWord>? = null
    var pendingCollectionName: String? = null

    private val database = AppDatabase.getInstance(context)
    val settingsDataStore = SettingsDataStore(context)

    val wordRepository = WordRepository(database.wordDao())
    val collectionRepository = CollectionRepository(database.collectionDao(), database.wordDao())

    val openRouterService = OpenRouterService(settingsDataStore)
    val dynamoDbService = DynamoDbService(settingsDataStore)

    val syncManager = SyncManager(
        wordRepository, collectionRepository, dynamoDbService, settingsDataStore
    )
}
