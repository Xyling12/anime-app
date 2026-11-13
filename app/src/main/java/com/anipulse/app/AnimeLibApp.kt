package com.anipulse.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anipulse.app.notify.NotifyWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AnimeLibApp : Application(), coil.ImageLoaderFactory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppHttpEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    /**
     * Общий загрузчик картинок поверх единого OkHttpClient-пула: до 2 повторов при сетевом сбое/5xx.
     * Собственный DiskCache Coil (96 МБ) избавляет от дублирования кэша картинок в http_cache.
     */
    override fun newImageLoader(): coil.ImageLoader {
        val baseClient = EntryPointAccessors
            .fromApplication(this, AppHttpEntryPoint::class.java)
            .okHttpClient()
        return coil.ImageLoader.Builder(this)
            .okHttpClient {
                baseClient.newBuilder()
                    .cache(null)
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
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024L * 1024L)
                    .build()
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Thread({
            // Фоновая работа не должна пережить проверку подписи: воркер ходит на сервер
            // с токеном раз в 30 минут, и в перепакованной сборке он продолжал бы качать
            // данные аккаунта даже после того, как экран закрылся. MainActivity показывает
            // пользователю причину, а планирование гасится здесь — до первого запуска.
            if (!com.anipulse.app.data.AppIntegrity.isSignatureValid(this)) {
                runCatching { WorkManager.getInstance(this).cancelUniqueWork("anipulse-notify") }
                return@Thread
            }
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
