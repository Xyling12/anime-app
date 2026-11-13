package com.anipulse.app.data.download

import android.content.Context
import android.os.storage.StorageManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Serializable
data class DownloadedItem(
    val animeId: Long,
    val episode: Int,
    val title: String,
    val dubTitle: String,
    val fileName: String,
    val sizeBytes: Long,
    val posterId: String? = null,
    val image: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val animeId: Long, val episode: Int, val progressPercent: Int) : DownloadState()
    data class Completed(val animeId: Long, val episode: Int) : DownloadState()
    data class Error(val animeId: Long, val episode: Int, val message: String) : DownloadState()
}

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("anipulse_downloads", Context.MODE_PRIVATE)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Итог каждой загрузки — разовым событием. Из [downloadState] его брать нельзя:
     * StateFlow отдаёт последнее значение новому подписчику, и экран тайтла,
     * открытый позже, показал бы чужую или давно случившуюся ошибку.
     */
    private val _events = MutableSharedFlow<DownloadState>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadState> = _events.asSharedFlow()

    private val _downloadedList = MutableStateFlow<List<DownloadedItem>>(loadFromPrefs())
    val downloadedList: StateFlow<List<DownloadedItem>> = _downloadedList.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inProgress = Collections.synchronizedSet(mutableSetOf<Pair<Long, Int>>())
    private val listLock = Any()
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    init {
        // Версии до этого исправления сохраняли плейлист вместо видео. Такие «загрузки»
        // не играют и не дают скачать серию заново — убираем их.
        scope.launch { pruneBroken() }
    }

    private fun loadFromPrefs(): List<DownloadedItem> {
        val raw = prefs.getString("items_json", "[]") ?: "[]"
        return runCatching { json.decodeFromString<List<DownloadedItem>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveList(items: List<DownloadedItem>) {
        prefs.edit().putString("items_json", json.encodeToString(items)).apply()
        _downloadedList.value = items
    }

    private val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
        }

    fun getAllDownloaded(): List<DownloadedItem> = _downloadedList.value

    fun isDownloaded(animeId: Long, episode: Int): Boolean = getLocalFile(animeId, episode) != null

    fun getLocalFile(animeId: Long, episode: Int): File? {
        val item = _downloadedList.value.find { it.animeId == animeId && it.episode == episode } ?: return null
        val file = File(downloadsDir, item.fileName)
        return if (isPlayable(file)) file else null
    }

    // Проверяем не только факт непустого файла, но и то, что внутри видео, а не текст плейлиста.
    private fun isPlayable(file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        val head = ByteArray(16)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read <= 0) return false
        val text = String(head, 0, read, Charsets.ISO_8859_1).trimStart()
        return !text.startsWith("#EXTM3U") && !text.startsWith("<")
    }

    private fun pruneBroken() = synchronized(listLock) {
        val current = loadFromPrefs()
        val valid = current.filter { isPlayable(File(downloadsDir, it.fileName)) }
        if (valid.size == current.size) return@synchronized
        (current - valid.toSet()).forEach { File(downloadsDir, it.fileName).delete() }
        saveList(valid)
    }

    /** Поставить серию в очередь WorkManager на скачивание. Повторное нажатие игнорируется. */
    fun enqueue(
        animeId: Long,
        episode: Int,
        title: String,
        dubTitle: String,
        streamUrl: String,
        posterId: String? = null,
        image: String? = null,
    ) {
        try {
            HlsPlaylist.requireHttpsUrl(streamUrl)
        } catch (e: HlsPlaylist.UnsupportedPlaylistException) {
            reportError(animeId, episode, e.message ?: "Эту серию нельзя скачать")
            return
        }

        val key = animeId to episode
        if (!inProgress.add(key)) return
        _downloadState.value = DownloadState.Downloading(animeId, episode, 0)

        val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    EpisodeDownloadWorker.KEY_ANIME_ID to animeId,
                    EpisodeDownloadWorker.KEY_EPISODE to episode,
                    EpisodeDownloadWorker.KEY_TITLE to title,
                    EpisodeDownloadWorker.KEY_DUB_TITLE to dubTitle,
                    EpisodeDownloadWorker.KEY_STREAM_URL to streamUrl,
                    EpisodeDownloadWorker.KEY_POSTER_ID to posterId,
                    EpisodeDownloadWorker.KEY_IMAGE to image,
                )
            )
            .addTag(workName(animeId, episode))
            .build()

        val scheduled = runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(animeId, episode),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
        if (scheduled.isFailure) {
            // Запасной путь, если WorkManager не инициализирован (например, в изолированных окружениях).
            scope.launch {
                runWorkerDownload(animeId, episode, title, dubTitle, streamUrl, posterId, image) {}
            }
        }
    }

    /** Отменить активную или поставленную в очередь загрузку серии. */
    fun cancelDownload(animeId: Long, episode: Int) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(workName(animeId, episode)) }
        inProgress.remove(animeId to episode)
        val current = _downloadState.value
        if (current is DownloadState.Downloading && current.animeId == animeId && current.episode == episode) {
            _downloadState.value = DownloadState.Idle
        }
    }

    internal suspend fun runWorkerDownload(
        animeId: Long,
        episode: Int,
        title: String,
        dubTitle: String,
        streamUrl: String,
        posterId: String?,
        image: String?,
        onProgressPercent: suspend (Int) -> Unit,
    ) {
        val key = animeId to episode
        inProgress.add(key)
        try {
            concurrencySemaphore.withPermit {
                download(animeId, episode, title, dubTitle, streamUrl, posterId, image, onProgressPercent)
            }
        } finally {
            inProgress.remove(key)
        }
    }

    private suspend fun download(
        animeId: Long,
        episode: Int,
        title: String,
        dubTitle: String,
        streamUrl: String,
        posterId: String?,
        image: String?,
        onProgressPercent: suspend (Int) -> Unit,
    ) {
        _downloadState.value = DownloadState.Downloading(animeId, episode, 0)
        val baseName = "${animeId}_ep${episode}_${System.currentTimeMillis()}"
        val partFile = File(downloadsDir, "$baseName.part")
        var lastPercent = 0
        val onProgress: suspend (Int) -> Unit = { percent ->
            val clamped = percent.coerceIn(0, 100)
            if (clamped != lastPercent) {
                lastPercent = clamped
                _downloadState.value = DownloadState.Downloading(animeId, episode, clamped)
                onProgressPercent(clamped)
            }
        }

        try {
            val extension = FileOutputStream(partFile).use { out -> writeEpisode(streamUrl, out, onProgress) }
            val target = File(downloadsDir, "$baseName.$extension")
            if (!partFile.renameTo(target)) throw IOException("Не удалось сохранить файл")

            synchronized(listLock) {
                val current = loadFromPrefs()
                // Прошлая загрузка этой серии (другая озвучка) больше не нужна.
                current.filter { it.animeId == animeId && it.episode == episode && it.fileName != target.name }
                    .forEach { File(downloadsDir, it.fileName).delete() }
                saveList(
                    current.filterNot { it.animeId == animeId && it.episode == episode } + DownloadedItem(
                        animeId = animeId,
                        episode = episode,
                        title = title,
                        dubTitle = dubTitle,
                        fileName = target.name,
                        sizeBytes = target.length(),
                        posterId = posterId,
                        image = image,
                    )
                )
            }
            publish(DownloadState.Completed(animeId, episode))
        } catch (e: CancellationException) {
            partFile.delete()
            _downloadState.value = DownloadState.Idle
            throw e
        } catch (e: Exception) {
            partFile.delete()
            publish(DownloadState.Error(animeId, episode, userMessage(e)))
        }
    }

    /** Ошибка до старта загрузки (не удалось получить ссылку на поток). */
    fun reportError(animeId: Long, episode: Int, message: String) =
        publish(DownloadState.Error(animeId, episode, message))

    private fun publish(state: DownloadState) {
        _downloadState.value = state
        _events.tryEmit(state)
    }

    /**
     * Пишет серию в [out] и возвращает расширение файла. Прямая ссылка на файл
     * копируется как есть; плейлист HLS — скачиваются и склеиваются его кусочки.
     */
    private suspend fun writeEpisode(url: String, out: OutputStream, onProgress: suspend (Int) -> Unit): String {
        val safeUrl = HlsPlaylist.requireHttpsUrl(url)
        val playlist = client.newCall(Request.Builder().url(safeUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Пустой ответ сервера")
            if (!HlsPlaylist.looksLikePlaylist(response.peekBody(64).string())) {
                copyWithProgress(body, out, onProgress)
                return "mp4"
            }
            body.string()
        }

        var media = HlsPlaylist.parse(playlist, safeUrl)
        if (media is HlsPlaylist.Parsed.Master) {
            val variantUrl = media.variantUrl
            media = HlsPlaylist.parse(String(fetch(variantUrl), Charsets.UTF_8), variantUrl)
        }
        if (media !is HlsPlaylist.Parsed.Media) {
            throw HlsPlaylist.UnsupportedPlaylistException("Не удалось разобрать плейлист серии")
        }

        val parts = listOfNotNull(media.initSegment) + media.segments
        parts.forEachIndexed { i, partUrl ->
            out.write(fetch(partUrl))
            onProgress((i + 1) * 100 / parts.size)
        }
        // С #EXT-X-MAP кусочки — фрагменты MP4, без него — MPEG-TS.
        return if (media.initSegment != null) "mp4" else "ts"
    }

    private suspend fun copyWithProgress(body: ResponseBody, out: OutputStream, onProgress: suspend (Int) -> Unit) {
        val total = body.contentLength()
        var copied = 0L
        body.byteStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                copied += read
                if (total > 0) onProgress((copied * 100 / total).toInt())
            }
        }
    }

    /**
     * Кусочек целиком в память (1–2 МБ), и только потом в файл: при обрыве на
     * середине повтор не допишет в склейку половину кусочка дважды.
     */
    private suspend fun fetch(url: String): ByteArray {
        val safeUrl = HlsPlaylist.requireHttpsUrl(url)
        var lastError: IOException? = null
        repeat(FETCH_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            try {
                client.newCall(Request.Builder().url(safeUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw HttpStatusException(response.code)
                    return response.body?.bytes() ?: throw IOException("Пустой ответ сервера")
                }
            } catch (e: IOException) {
                // 4xx, кроме 429, повтором не лечится — ссылка недействительна.
                if (e is HttpStatusException && e.code in 400..499 && e.code != 429) throw e
                lastError = e
            }
            if (attempt < FETCH_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: IOException("Не удалось скачать")
    }

    private fun availableBytes(): Long = runCatching {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null) sm.getAllocatableBytes(sm.getUuidForPath(downloadsDir)) else downloadsDir.usableSpace
    }.getOrElse { downloadsDir.usableSpace }

    private fun userMessage(e: Exception): String = when {
        e is HlsPlaylist.UnsupportedPlaylistException -> e.message ?: "Эту серию нельзя скачать"
        e is HttpStatusException -> "Сервер видео ответил ошибкой ${e.code}. Попробуйте другую озвучку"
        availableBytes() < LOW_SPACE_BYTES -> "Не хватает места на устройстве"
        e is IOException -> "Загрузка прервалась. Проверьте интернет и попробуйте ещё раз"
        else -> "Не удалось скачать серию"
    }

    fun deleteDownload(animeId: Long, episode: Int) {
        cancelDownload(animeId, episode)
        synchronized(listLock) {
            val current = loadFromPrefs()
            val item = current.find { it.animeId == animeId && it.episode == episode } ?: return@synchronized
            File(downloadsDir, item.fileName).delete()
            saveList(current.filterNot { it.animeId == animeId && it.episode == episode })
        }
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        const val FETCH_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_000L
        const val LOW_SPACE_BYTES = 50L * 1024 * 1024
        const val MAX_CONCURRENT_DOWNLOADS = 2

        fun workName(animeId: Long, episode: Int): String = "download_${animeId}_ep${episode}"
    }
}
