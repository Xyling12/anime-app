package com.anipulse.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anipulse.app.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * Фоновая загрузка серии под управлением WorkManager Foreground Service,
 * чтобы система не обрывала скачивание при сворачивании приложения.
 */
class EpisodeDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadEntryPoint {
        fun downloadManager(): DownloadManager
    }

    override suspend fun doWork(): Result {
        val animeId = inputData.getLong(KEY_ANIME_ID, -1L)
        val episode = inputData.getInt(KEY_EPISODE, -1)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val dubTitle = inputData.getString(KEY_DUB_TITLE).orEmpty()
        val streamUrl = inputData.getString(KEY_STREAM_URL).orEmpty()
        val posterId = inputData.getString(KEY_POSTER_ID)
        val image = inputData.getString(KEY_IMAGE)
        if (animeId <= 0 || episode <= 0 || streamUrl.isBlank()) return Result.failure()

        ensureChannel(applicationContext)
        val manager = EntryPointAccessors
            .fromApplication(applicationContext, DownloadEntryPoint::class.java)
            .downloadManager()

        var lastNotifiedPercent = -1
        runCatching { setForeground(createForegroundInfo(animeId, episode, title, 0)) }

        return try {
            manager.runWorkerDownload(
                animeId = animeId,
                episode = episode,
                title = title,
                dubTitle = dubTitle,
                streamUrl = streamUrl,
                posterId = posterId,
                image = image,
            ) { percent ->
                if (percent != lastNotifiedPercent && (percent % 5 == 0 || percent == 100)) {
                    lastNotifiedPercent = percent
                    runCatching { setForeground(createForegroundInfo(animeId, episode, title, percent)) }
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun createForegroundInfo(animeId: Long, episode: Int, title: String, percent: Int): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val label = if (title.isNotBlank()) "$title · Серия $episode" else "Серия $episode"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Скачивание: $label")
            .setContentText("Загружено $percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .addAction(0, "Отмена", cancelIntent)
            .build()

        val notifId = notificationId(animeId, episode)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads_pulse"
        const val KEY_ANIME_ID = "anime_id"
        const val KEY_EPISODE = "episode"
        const val KEY_TITLE = "title"
        const val KEY_DUB_TITLE = "dub_title"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_POSTER_ID = "poster_id"
        const val KEY_IMAGE = "image"

        fun notificationId(animeId: Long, episode: Int): Int =
            70_000 + (((animeId * 1000L + episode) and 0x7FFFL).toInt())

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Загрузки серий",
                NotificationManager.IMPORTANCE_LOW,
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }

        fun hasNotificationPermission(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
