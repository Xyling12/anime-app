package com.anipulse.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
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
                        var res = chain.proceed(chain.request())
                        var tries = 0
                        while (!res.isSuccessful && tries < 2) {
                            res.close(); tries++
                            res = chain.proceed(chain.request())
                        }
                        res
                    }
                    .build()
            }
            .crossfade(false) // без анимации появления — по фидбеку владельца
            .build()

    override fun onCreate() {
        super.onCreate()
        NotifyWorker.ensureChannels(this)
        // Пуши без Google-сервисов: периодический опрос шлюза (15 мин — минимум WorkManager).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "anipulse-notify",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES).build(),
        )
        // Разовая проверка сразу при запуске приложения (пуши догоняют мгновенно).
        WorkManager.getInstance(this).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<NotifyWorker>().build(),
        )
    }

}
