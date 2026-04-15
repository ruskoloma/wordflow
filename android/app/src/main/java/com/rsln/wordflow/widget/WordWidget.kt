package com.rsln.wordflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rsln.wordflow.MainActivity
import com.rsln.wordflow.R
import com.rsln.wordflow.WordFlowApp
import kotlinx.coroutines.runBlocking

private const val ACTION_NEXT_WORD = "com.rsln.wordflow.NEXT_WORD"

private fun getWidgetPrefs(context: Context) =
    context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

private fun getLastWordId(context: Context): Long =
    getWidgetPrefs(context).getLong("last_word_id", 0L)

private fun setLastWordId(context: Context, id: Long) {
    getWidgetPrefs(context).edit().putLong("last_word_id", id).commit()
}

class WordWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT_WORD) {
            advanceWord(context)
            // Update all widget instances
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidgetReceiver::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

    private fun advanceWord(context: Context) {
        val app = context.applicationContext as? WordFlowApp ?: return
        val currentId = getLastWordId(context)

        runBlocking {
            val nextWord = app.container.wordRepository.getNextWidgetWord(currentId)
            if (nextWord != null) {
                setLastWordId(context, nextWord.id)
                app.container.wordRepository.incrementShowCount(nextWord.id)
            }
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as? WordFlowApp ?: return
            val currentId = getLastWordId(context)

            val entity = runBlocking {
                val word = if (currentId > 0L) {
                    app.container.wordRepository.getWordById(currentId)
                } else null

                word ?: run {
                    val first = app.container.wordRepository.getNextWidgetWord(0L)
                    if (first != null) setLastWordId(context, first.id)
                    first
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_word)

            views.setTextViewText(R.id.widget_word, entity?.originalWord ?: "WordFlow")
            views.setTextViewText(R.id.widget_translation, entity?.translation ?: "Add words to get started")

            // Left side — open app on Add screen
            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "add_word")
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_left, openPending)

            // Right side — next word
            val nextIntent = Intent(context, WordWidgetReceiver::class.java).apply {
                action = ACTION_NEXT_WORD
            }
            val nextPending = PendingIntent.getBroadcast(
                context, 1, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_right, nextPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
