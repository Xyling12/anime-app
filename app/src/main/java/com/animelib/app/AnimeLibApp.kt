package com.animelib.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.animelib.app.notify.NotifyWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AnimeLibApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotifyWorker.ensureChannels(this)
        // Пуши без Google-сервисов: периодический опрос шлюза (минимум WorkManager — 15 мин; ставим 30).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "anipulse-notify",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotifyWorker>(30, TimeUnit.MINUTES).build(),
        )
        // Разовая проверка сразу при запуске приложения (пуши догоняют мгновенно).
        WorkManager.getInstance(this).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<NotifyWorker>().build(),
        )
    }
}
