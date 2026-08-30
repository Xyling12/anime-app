package com.anipulse.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.anipulse.app.MainActivity
import com.anipulse.app.R
import com.anipulse.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AniPulseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val items = db.progressDao().continueWatching().firstOrNull()
                val latest = items?.firstOrNull()

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_continue_watching)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        if (latest != null) {
                            putExtra("extra_anime_id", latest.animeId)
                            putExtra("extra_episode", latest.episode)
                        }
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        widgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    if (latest != null) {
                        views.setTextViewText(R.id.widget_title, latest.title.ifBlank { "Аниме #${latest.animeId}" })
                        views.setTextViewText(R.id.widget_episode, "Серия ${latest.episode} • Продолжить просмотр")
                        views.setTextViewText(R.id.widget_btn_play, "▶ Смотреть серию ${latest.episode}")
                    } else {
                        views.setTextViewText(R.id.widget_title, "AniPulse")
                        views.setTextViewText(R.id.widget_episode, "Откройте каталог для выбора аниме")
                        views.setTextViewText(R.id.widget_btn_play, "▶ Открыть каталог")
                    }

                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_btn_play, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                // Widget fallback
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, AniPulseWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, AniPulseWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
