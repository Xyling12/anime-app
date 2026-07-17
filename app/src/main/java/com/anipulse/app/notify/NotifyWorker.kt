package com.anipulse.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anipulse.app.MainActivity
import com.anipulse.app.R
import com.anipulse.app.data.Api
import com.anipulse.app.data.db.AppDatabase
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Фоновые пуши без Google-сервисов: раз в ~30 мин опрашивает шлюз.
 * Каналы: dm (ЛС), mentions (@упоминания), social (друзья), episodes (новые серии «Моё»).
 * Уважает настройку chat_notify_mode: off — упоминания не показываем.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("anipulse_settings", Context.MODE_PRIVATE)
        ensureChannels(ctx)
        val canPost = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canPost) return Result.success()

        runCatching { checkServerNotifications(ctx, prefs) }
        runCatching { checkChatAll(ctx, prefs) }
        runCatching { checkNewEpisodes(ctx, prefs) }
        return Result.success()
    }

    // --- Режим «Все»: пуш о любом новом сообщении общего чата (кроме своих) ---
    private fun checkChatAll(ctx: Context, prefs: android.content.SharedPreferences) {
        if (prefs.getString("chat_notify_mode", "mentions") != "all") return
        if (prefs.getString("auth_token", null) == null) return
        val myNick = prefs.getString("auth_nick", null)
        val lastId = prefs.getLong("last_chat_id", -1)
        val arr = JSONArray(httpGet("${Api.GATEWAY}chat?after=${if (lastId < 0) 0 else lastId}", null) ?: return)
        var maxId = if (lastId < 0) 0 else lastId
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val id = m.optLong("id")
            if (id > maxId) maxId = id
            // первый запуск (lastId==-1) — только запоминаем позицию, не спамим историей
            if (lastId >= 0 && id > lastId && m.optString("nick") != myNick) {
                notify(ctx, "chat", id.toInt(), "Чат: ${m.optString("nick")}", m.optString("text"))
            }
        }
        prefs.edit().putLong("last_chat_id", maxId).apply()
    }

    // --- Серверные уведомления: упоминания, ЛС, друзья ---
    private fun checkServerNotifications(ctx: Context, prefs: android.content.SharedPreferences) {
        val token = prefs.getString("auth_token", null) ?: return
        val mode = prefs.getString("chat_notify_mode", "mentions") ?: "mentions"
        val lastId = prefs.getLong("last_notif_id", 0)
        val arr = JSONArray(httpGet("${Api.GATEWAY}notifications?after=$lastId", token) ?: return)
        var maxId = lastId
        for (i in 0 until arr.length()) {
            val n = arr.getJSONObject(i)
            val id = n.optLong("id")
            if (id > maxId) maxId = id
            val from = n.optString("from")
            val text = n.optString("text")
            when (n.optString("type")) {
                "mention" -> if (mode != "off") notify(ctx, "mentions", id.toInt(), "@$from упомянул(а) вас", text)
                "dm" -> notify(ctx, "dm", id.toInt(), "Сообщение от $from", text)
                "friend_request" -> notify(ctx, "social", id.toInt(), "Заявка в друзья", "$from хочет добавить вас в друзья")
                "friend_accept" -> notify(ctx, "social", id.toInt(), "Новый друг", "$from: $text")
            }
        }
        if (maxId > lastId) prefs.edit().putLong("last_notif_id", maxId).apply()
    }

    // --- Новые серии тайтлов из «Моё» ---
    private suspend fun checkNewEpisodes(ctx: Context, prefs: android.content.SharedPreferences) {
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "anipulse.db").build()
        try {
            val favorites = db.favoriteDao().allOnce().take(30)
            if (favorites.isEmpty()) return
            val state = JSONObject(prefs.getString("episodes_state", "{}") ?: "{}")
            var changed = false
            for (f in favorites) {
                val body = httpGet("${Api.SHIKIMORI}api/animes/${f.animeId}", null) ?: continue
                val o = runCatching { JSONObject(body) }.getOrNull() ?: continue
                val aired = o.optInt("episodes_aired", 0)
                val status = o.optString("status")
                val key = f.animeId.toString()
                val prev = state.optInt(key, -1)
                if (prev in 0 until aired && status == "ongoing") {
                    val title = o.optString("russian").ifBlank { f.title }
                    notify(ctx, "episodes", f.animeId.toInt(), "Вышла серия $aired", title)
                }
                if (prev != aired) { state.put(key, aired); changed = true }
            }
            if (changed) prefs.edit().putString("episodes_state", state.toString()).apply()
        } finally {
            db.close()
        }
    }

    private fun httpGet(url: String, token: String?): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun notify(ctx: Context, channel: String, id: Int, title: String, text: String) {
        val intent = Intent(ctx, MainActivity::class.java)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(channel.hashCode() + id, n)
    }

    companion object {
        /** Каналы уведомлений (создание идемпотентно). */
        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            listOf(
                NotificationChannel("dm", "Личные сообщения", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("mentions", "@Упоминания", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("social", "Друзья", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("episodes", "Новые серии", NotificationManager.IMPORTANCE_DEFAULT),
                // «Все» — намеренно тихий канал (много сообщений), звук явно выключен.
                NotificationChannel("chat", "Общий чат (режим «Все»)", NotificationManager.IMPORTANCE_LOW),
            ).forEach { ch ->
                if (ch.id == "chat") ch.setSound(null, null) else ch.setSound(defaultSound, soundAttrs)
                nm.createNotificationChannel(ch)
            }
        }
    }
}
