package com.rsln.wordflow.di

import android.content.Context
import com.rsln.wordflow.data.auth.AuthManager
import com.rsln.wordflow.data.local.AppDatabase
import com.rsln.wordflow.data.local.SettingsDataStore
import com.rsln.wordflow.data.remote.BackendClient
import com.rsln.wordflow.data.remote.OpenRouterService
import com.rsln.wordflow.data.repository.CollectionRepository
import com.rsln.wordflow.data.repository.WordRepository
import com.rsln.wordflow.data.sync.SyncService
import com.rsln.wordflow.ui.screens.addword.GeneratedWord

/**
 * Lightweight service locator. Everything stateful in the app hangs
 * off this one object — ViewModels get it via their Factory, Compose
 * screens get it from WordFlowApp.
 *
 * Construction order matters: AuthManager → BackendClient → repositories
 * → SyncService. The repositories need the BackendClient to do online
 * writes, and the BackendClient needs the AuthManager to inject the
 * Bearer header.
 */
class AppContainer(context: Context) {
    // Shared state for passing generated words from Collections -> Add screen
    var pendingGeneratedWords: List<GeneratedWord>? = null
    var pendingCollectionName: String? = null

    private val database = AppDatabase.getInstance(context)
    val settingsDataStore = SettingsDataStore(context)

    val authManager = AuthManager(context)
    val backendClient = BackendClient(authManager)

    val wordRepository = WordRepository(database.wordDao(), backendClient)
    val collectionRepository = CollectionRepository(database.collectionDao(), database.wordDao(), backendClient)

    val openRouterService = OpenRouterService(backendClient, authManager)

    val syncService = SyncService(
        backendClient,
        database.wordDao(),
        database.collectionDao(),
        authManager
    )
}
