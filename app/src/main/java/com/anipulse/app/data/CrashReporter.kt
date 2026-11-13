package com.anipulse.app.data

import android.content.Context
import android.os.Build
import com.anipulse.app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class CrashRecord(
    val timestampIso: String,
    val versionName: String,
    val versionCode: Int,
    val device: String,
    val osVersion: String,
    val abis: String,
    val threadName: String,
    val summary: String,
    val reportText: String,
)

/**
 * Локальный перехват необработанных исключений. Сохраняет очищенный от токенов
 * стектрейс в приватное хранилище приложения, чтобы при следующем запуске
 * предложить пользователю отправить отчёт через /alapi/bugreport.
 */
object CrashReporter {

    private const val CRASH_FILE_NAME = "last_crash.json"
    internal const val MAX_REPORT_CHARS = 1900

    private val installed = AtomicBoolean(false)
    private val json = Json { ignoreUnknownKeys = true }

    private val bearerRegex = Regex("""(?i)Bearer\s+[A-Za-z0-9._\-]+""")
    private val jwtRegex = Regex("""eyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+""")
    private val secretParamRegex = Regex("""(?i)(token|code|password|pass|secret|auth)=([^\s&]+)""")

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        val targetFile = File(appContext.filesDir, CRASH_FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val record = buildRecord(
                    threadName = thread.name,
                    throwable = throwable,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    device = Build.MODEL ?: "unknown",
                    osVersion = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}",
                    abis = Build.SUPPORTED_ABIS.joinToString(","),
                )
                saveToFile(targetFile, record)
            } catch (_: Throwable) {
                // Внутри обработчика падения нельзя бросать новое исключение.
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun readPendingCrash(context: Context): CrashRecord? =
        readFromFile(File(context.filesDir, CRASH_FILE_NAME))

    fun clearPendingCrash(context: Context) {
        clearFile(File(context.filesDir, CRASH_FILE_NAME))
    }

    internal fun buildRecord(
        threadName: String,
        throwable: Throwable,
        versionName: String,
        versionCode: Int,
        device: String,
        osVersion: String,
        abis: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): CrashRecord {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val cleanTrace = redactSensitive(sw.toString()).trim()
        val firstLine = redactSensitive(
            "${throwable::class.java.simpleName}: ${throwable.message.orEmpty()}",
        ).trim().take(180)
        val iso = formatUtcIso(timestampMs)
        val header = buildString {
            appendLine("[CRASH] AniPulse v$versionName ($versionCode)")
            appendLine("Время: $iso | ABI: $abis")
            appendLine("Поток: $threadName")
        }
        val availableForTrace = (MAX_REPORT_CHARS - header.length - 4).coerceAtLeast(200)
        val body = if (cleanTrace.length > availableForTrace) {
            cleanTrace.take(availableForTrace) + "\n..."
        } else {
            cleanTrace
        }
        val reportText = (header + body).take(MAX_REPORT_CHARS)
        return CrashRecord(
            timestampIso = iso,
            versionName = versionName,
            versionCode = versionCode,
            device = device.take(80),
            osVersion = osVersion.take(40),
            abis = abis.take(80),
            threadName = threadName.take(64),
            summary = firstLine,
            reportText = reportText,
        )
    }

    internal fun redactSensitive(raw: String): String = raw
        .replace(bearerRegex, "Bearer [REDACTED]")
        .replace(jwtRegex, "[JWT]")
        .replace(secretParamRegex) { m -> "${m.groupValues[1]}=[REDACTED]" }

    internal fun saveToFile(file: File, record: CrashRecord) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(record), Charsets.UTF_8)
    }

    internal fun readFromFile(file: File): CrashRecord? = runCatching {
        if (!file.exists() || file.length() <= 0L) return null
        json.decodeFromString<CrashRecord>(file.readText(Charsets.UTF_8))
    }.getOrNull()

    internal fun clearFile(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    private fun formatUtcIso(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(timestampMs))
    }
}
