package com.anipulse.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anipulse.app.MainActivity
import com.anipulse.app.R
import com.anipulse.app.data.Api
import com.anipulse.app.data.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Фоновые уведомления о новых сериях подписанных онгоингов из «Моё» (~раз в 30 мин).
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotifyEntryPoint {
        fun settingsStore(): SettingsStore
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(ctx, NotifyEntryPoint::class.java)
        val settings = entryPoint.settingsStore()
        val http = entryPoint.okHttpClient().newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        val prefs = ctx.getSharedPreferences("anipulse_notify_state", Context.MODE_PRIVATE)
        ensureChannels(ctx)
        val canPost = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canPost) return@withContext Result.success()

        runCatching { checkNewEpisodes(ctx, settings, http, prefs) }
        Result.success()
    }

    // --- Новые серии тайтлов из «Моё» ---
    private suspend fun checkNewEpisodes(
        ctx: Context,
        settings: SettingsStore,
        http: OkHttpClient,
        prefs: android.content.SharedPreferences,
    ) {
        val subscribed = settings.episodeNotifyIds.take(10)
        if (subscribed.isEmpty()) return
        val state = JSONObject(prefs.getString("episodes_state", "{}") ?: "{}")
        var changed = false

        val fetched = subscribed.chunked(3).flatMap { chunk ->
            coroutineScope {
                chunk.map { animeId ->
                    async {
                        val body = httpGet(http, "${Api.SHIKIMORI}api/animes/$animeId") ?: return@async null
                        animeId to runCatching { JSONObject(body) }.getOrNull()
                    }
                }.awaitAll()
            }
        }

        for (pair in fetched) {
            val (animeId, obj) = pair ?: continue
            if (obj == null) continue
            val aired = obj.optInt("episodes_aired", 0)
            val status = obj.optString("status")
            val prev = state.optInt(animeId, -1)
            if (prev in 0 until aired && status == "ongoing") {
                val title = obj.optString("russian").ifBlank { obj.optString("name") }
                notify(ctx, "episodes", animeId.toInt(), "Вышла серия $aired", title)
            }
            if (prev != aired) {
                state.put(animeId, aired)
                changed = true
            }
        }
        if (changed) prefs.edit().putString("episodes_state", state.toString()).apply()
    }

    private fun httpGet(client: OkHttpClient, url: String): String? = runCatching {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()

    private fun notify(ctx: Context, channel: String, id: Int, title: String, text: String) {
        val channelId = if (channel == "chat") channel else "${channel}_pulse"
        val intent = Intent(ctx, MainActivity::class.java)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Новое уведомление AniPulse")
                    .setContentText("Откройте приложение, чтобы прочитать")
                    .build()
            )
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(channel.hashCode() + id, n)
    }

    companion object {
        /** Каналы уведомлений (создание идемпотентно). */
        fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pulseSound = android.net.Uri.parse("android.resource://${ctx.packageName}/${R.raw.anipulse_pulse}")
            listOf(
                NotificationChannel("episodes_pulse", "Новые серии", NotificationManager.IMPORTANCE_DEFAULT),
            ).forEach { ch ->
                ch.setSound(pulseSound, soundAttrs)
                nm.createNotificationChannel(ch)
            }
            listOf("dm_pulse", "mentions_pulse", "social_pulse", "chat").forEach(nm::deleteNotificationChannel)
        }
    }
}
