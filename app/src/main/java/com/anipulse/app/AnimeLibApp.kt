package com.anipulse.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anipulse.app.notify.NotifyWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AnimeLibApp : Application(), coil.ImageLoaderFactory {

    /**
     * Общий загрузчик картинок: до 2 повторов при сетевом сбое/5xx.
     * Лечит «постер то есть, то нет» — разовые обрывы на мобильной сети
     * раньше оставляли пустую карточку до пересоздания элемента списка.
     */
    override fun newImageLoader(): coil.ImageLoader =
        coil.ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        var tries = 0
                        var lastError: java.io.IOException? = null
                        var res: okhttp3.Response? = null
                        while (tries <= 2) {
                            try {
                                res = chain.proceed(chain.request())
                                if (res.isSuccessful || tries == 2) break
                                res.close()
                            } catch (e: java.io.IOException) {
                                lastError = e
                            }
                            tries++
                        }
                        res ?: throw (lastError ?: java.io.IOException("Image load failed"))
                    }
                    .build()
            }
            .crossfade(false)
            .build()

    override fun onCreate() {
        super.onCreate()
        Thread({
            NotifyWorker.ensureChannels(this)
            val notifyConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "anipulse-notify",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NotifyWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(notifyConstraints)
                    .build(),
            )
        }, "anipulse-init").start()
    }

}
