package com.anipulse.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anipulse.app.notify.NotifyWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AnimeLibApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()
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
        sendPendingCrash()
    }

    /**
     * Автоотчёты о крэшах без Google-сервисов: стек сохраняется в prefs в момент
     * падения, при СЛЕДУЮЩЕМ запуске уходит на /alapi/bugreport (владельцу придёт
     * письмо, как от ручной кнопки «Сообщить о баге»). Во время самого краша сеть
     * ненадёжна, поэтому отправка отложенная.
     */
    private fun installCrashReporter() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                getSharedPreferences("anipulse_crash", MODE_PRIVATE).edit()
                    .putString("last_crash", android.util.Log.getStackTraceString(e).take(1800))
                    .commit() // именно commit: процесс сейчас умрёт
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun sendPendingCrash() {
        val prefs = getSharedPreferences("anipulse_crash", MODE_PRIVATE)
        val stack = prefs.getString("last_crash", null) ?: return
        Thread {
            runCatching {
                val body = org.json.JSONObject()
                    .put("text", "AUTO-CRASH v" + BuildConfig.VERSION_NAME + "\n" + stack)
                    .put("device", android.os.Build.MODEL)
                    .put("osVersion", "Android " + android.os.Build.VERSION.RELEASE)
                    .toString()
                val conn = java.net.URL(com.anipulse.app.data.Api.GATEWAY + "bugreport")
                    .openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode in 200..299) prefs.edit().remove("last_crash").apply()
                conn.disconnect()
            }
        }.start()
    }
}
