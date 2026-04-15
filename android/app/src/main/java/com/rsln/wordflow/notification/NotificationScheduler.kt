package com.rsln.wordflow.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.rsln.wordflow.MainActivity
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationScheduler {
    companion object {
        private const val WORK_TAG = "wordflow_notification"

        fun schedule(
            context: Context,
            notificationsPerDay: Int,
            startHour: Int,
            endHour: Int
        ) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)

            if (notificationsPerDay <= 0) return

            val totalActiveMinutes = if (endHour > startHour)
                (endHour - startHour) * 60
            else
                (24 - startHour + endHour) * 60

            val intervalMinutes = (totalActiveMinutes / notificationsPerDay).toLong()
                .coerceAtLeast(15)

            val request = PeriodicWorkRequestBuilder<WordNotificationWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .addTag(WORK_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }
    }
}

class WordNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WordFlowApp ?: return Result.success()
        val settings = app.container.settingsDataStore

        val startHour = settings.activeStartHour.first()
        val endHour = settings.activeEndHour.first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val isInActiveWindow = if (endHour > startHour) {
            currentHour in startHour until endHour
        } else {
            currentHour >= startHour || currentHour < endHour
        }

        if (!isInActiveWindow) return Result.success()

        val lastWordId = settings.lastWidgetWordId.first()
        val word = app.container.wordRepository.getNextWidgetWord(lastWordId)
            ?: return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, WordFlowApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(word.originalWord)
            .setContentText(word.translation)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(
            word.id.toInt(),
            notification
        )

        app.container.wordRepository.incrementShowCount(word.id)

        return Result.success()
    }
}
