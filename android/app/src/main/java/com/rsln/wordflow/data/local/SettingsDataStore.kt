package com.rsln.wordflow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wordflow_settings")

/**
 * App preferences. API_KEY and AI_MODEL were removed when AI calls
 * moved behind the Go backend (Phase 15) — the server owns those
 * now. Cloud-sync prefs were removed in Phase 10 with the old
 * DynamoDB flow.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        val WORDS_PER_WEEK = intPreferencesKey("words_per_week")
        val NOTIFICATIONS_PER_DAY = intPreferencesKey("notifications_per_day")
        val ACTIVE_START_HOUR = intPreferencesKey("active_start_hour")
        val ACTIVE_END_HOUR = intPreferencesKey("active_end_hour")
        val WIDGET_REFRESH_SECONDS = intPreferencesKey("widget_refresh_seconds")
        val PRACTICE_ALL = booleanPreferencesKey("practice_all")
        val LAST_WIDGET_WORD_ID = longPreferencesKey("last_widget_word_id")
        val ADD_WORD_DRAFT = stringPreferencesKey("add_word_draft")
    }

    val wordsPerWeek: Flow<Int> = context.dataStore.data.map { it[WORDS_PER_WEEK] ?: 20 }
    val notificationsPerDay: Flow<Int> = context.dataStore.data.map { it[NOTIFICATIONS_PER_DAY] ?: 3 }
    val activeStartHour: Flow<Int> = context.dataStore.data.map { it[ACTIVE_START_HOUR] ?: 9 }
    val activeEndHour: Flow<Int> = context.dataStore.data.map { it[ACTIVE_END_HOUR] ?: 22 }
    val widgetRefreshSeconds: Flow<Int> = context.dataStore.data.map { it[WIDGET_REFRESH_SECONDS] ?: 35 }
    val practiceAll: Flow<Boolean> = context.dataStore.data.map { it[PRACTICE_ALL] ?: false }
    val lastWidgetWordId: Flow<Long> = context.dataStore.data.map { it[LAST_WIDGET_WORD_ID] ?: 0L }
    val addWordDraft: Flow<String> = context.dataStore.data.map { it[ADD_WORD_DRAFT] ?: "" }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun <T> remove(key: Preferences.Key<T>) {
        context.dataStore.edit { it.remove(key) }
    }
}
