package com.rsln.wordflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.rsln.wordflow.di.AppContainer

class WordFlowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()

        // Pull cloud data on launch (offline-safe — silently fails without network)
        container.syncManager.pullOnLaunch()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Word Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Vocabulary word reminders"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "wordflow_reminders"
    }
}
