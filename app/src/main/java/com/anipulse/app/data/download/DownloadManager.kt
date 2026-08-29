package com.anipulse.app.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DownloadedItem(
    val animeId: Long,
    val episode: Int,
    val title: String,
    val dubTitle: String,
    val fileName: String,
    val sizeBytes: Long,
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

    private val downloadsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "episodes")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getAllDownloaded(): List<DownloadedItem> {
        val raw = prefs.getString("items_json", "[]") ?: "[]"
        return runCatching { json.decodeFromString<List<DownloadedItem>>(raw) }.getOrDefault(emptyList())
    }

    fun isDownloaded(animeId: Long, episode: Int): Boolean {
        val item = getAllDownloaded().find { it.animeId == animeId && it.episode == episode } ?: return false
        val file = File(downloadsDir, item.fileName)
        return file.exists() && file.length() > 0
    }

    fun getLocalFile(animeId: Long, episode: Int): File? {
        val item = getAllDownloaded().find { it.animeId == animeId && it.episode == episode } ?: return null
        val file = File(downloadsDir, item.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun downloadEpisode(
        animeId: Long,
        episode: Int,
        title: String,
        dubTitle: String,
        streamUrl: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = "${animeId}_ep${episode}_${System.currentTimeMillis()}.mp4"
        val targetFile = File(downloadsDir, fileName)

        _downloadState.value = DownloadState.Downloading(animeId, episode, 0)

        try {
            val request = Request.Builder().url(streamUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Error(animeId, episode, "HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastProgress = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalLength > 0) {
                                val progress = ((downloadedBytes * 100) / totalLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    _downloadState.value = DownloadState.Downloading(animeId, episode, progress)
                                }
                            }
                        }
                    }
                }
            }

            // Save to prefs
            val current = getAllDownloaded().filterNot { it.animeId == animeId && it.episode == episode }.toMutableList()
            current.add(
                DownloadedItem(
                    animeId = animeId,
                    episode = episode,
                    title = title,
                    dubTitle = dubTitle,
                    fileName = fileName,
                    sizeBytes = targetFile.length(),
                )
            )
            prefs.edit().putString("items_json", json.encodeToString(current)).apply()

            _downloadState.value = DownloadState.Completed(animeId, episode)
            true
        } catch (e: Exception) {
            targetFile.delete()
            _downloadState.value = DownloadState.Error(animeId, episode, e.message ?: "Download error")
            false
        }
    }

    fun deleteDownload(animeId: Long, episode: Int) {
        val item = getAllDownloaded().find { it.animeId == animeId && it.episode == episode }
        if (item != null) {
            val file = File(downloadsDir, item.fileName)
            if (file.exists()) file.delete()
            val current = getAllDownloaded().filterNot { it.animeId == animeId && it.episode == episode }
            prefs.edit().putString("items_json", json.encodeToString(current)).apply()
        }
    }
}
