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

    /**
     * Через `dagger.Lazy`, а не напрямую: обычное field-инжектирование построило бы
     * SettingsStore прямо здесь, на главном потоке, — ровно то, чего мы избегаем.
     */
    @javax.inject.Inject lateinit var settingsStore: dagger.Lazy<com.anipulse.app.data.SettingsStore>

    override fun onCreate() {
        super.onCreate()
        // Прогрев зашифрованных настроек в фоне.
        //
        // SettingsStore создаёт EncryptedSharedPreferences, а первый запуск — это генерация
        // мастер-ключа в Android Keystore: сотни миллисекунд, иногда секунды. Читают его
        // MainActivity и RootMenuViewModel (nick/avatar/theme прямо в инициализаторах полей),
        // то есть на главном потоке до первого кадра — на холодном старте это давало ANR.
        // Синглтон Hilt, поэтому построенный здесь экземпляр переиспользуется; если UI успеет
        // попросить его раньше, он дождётся той же блокировки, но обычно ключ уже готов.
        Thread({ runCatching { settingsStore.get() } }, "settings-warmup").start()
        NotifyWorker.ensureChannels(this)
        val notifyConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        // Фоновая проверка откладывается без сети и при низком заряде.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "anipulse-notify",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NotifyWorker>(30, TimeUnit.MINUTES)
                .setConstraints(notifyConstraints)
                .build(),
        )
    }

}
