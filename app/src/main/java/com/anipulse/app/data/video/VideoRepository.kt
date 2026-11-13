package com.anipulse.app.data.video

import com.anipulse.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CancellationException

/**
 * Собирает озвучки со всех источников для тайтла и отдаёт поток выбранной.
 * Источники опрашиваются параллельно; порядок в выдаче: AniLibria (нативный FHD) — первым.
 */
@Singleton
class VideoRepository @Inject constructor(
    private val anilibria: AnilibriaSource,
    private val kodik: KodikSource,
    private val client: OkHttpClient,
) {
    private val sources: List<VideoSource> = listOf(anilibria, kodik)

    private inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** Точные таймкоды опенинга и эндинга из AniSkip по MAL id + серии (через шлюз). */
    suspend fun skipTimes(malId: Long, episode: Int): Pair<Skip?, Skip?> = withContext(Dispatchers.IO) {
        val url = Api.GATEWAY + "aniskip/v2/skip-times/$malId/$episode?types=op&types=ed&episodeLength=0"
        val body = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null to null
        parseAniSkipTimes(body)
    }

    suspend fun dubs(malId: Long, names: List<String>): List<Dub> = coroutineScope {
        sources
            .map { src -> async { runSuspendCatching { src.findDubs(malId, names) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    suspend fun stream(dub: Dub, episode: Int): EpisodeStream? {
        val source = sources.firstOrNull { it.type == dub.source } ?: return null
        return runSuspendCatching { source.episodeStream(dub, episode) }.getOrNull()
    }
}

private val aniSkipJson = Json { ignoreUnknownKeys = true }

/**
 * Разбирает ответ AniSkip и отсеивает битые/ранние интервалы эндинга
 * (в некоторых сериях AniSkip отдаёт ED на первых секундах серии, напр. 4..94с при длине 1420с).
 */
internal fun parseAniSkipTimes(body: String): Pair<Skip?, Skip?> = runCatching {
    val results = aniSkipJson.parseToJsonElement(body).jsonObject["results"]?.jsonArray
        ?: return@runCatching null to null

    fun find(type: String): Skip? {
        for (el in results) {
            val obj = el.jsonObject
            if (obj["skipType"]?.jsonPrimitive?.content != type) continue
            val iv = obj["interval"]?.jsonObject ?: continue
            val start = iv["startTime"]?.jsonPrimitive?.content?.toFloatOrNull()?.toInt() ?: continue
            val stop = iv["endTime"]?.jsonPrimitive?.content?.toFloatOrNull()?.toInt() ?: continue
            val epLen = obj["episodeLength"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val skip = Skip(start = start, stop = stop)
            if (!skip.isValidInterval()) continue
            if (epLen > 0f && start >= epLen.toInt()) continue
            if (type == "ed") {
                if (start <= 0) continue
                if (epLen > 30f && start < (epLen * 0.5f).toInt()) continue
            }
            return skip
        }
        return null
    }
    find("op") to find("ed")
}.getOrDefault(null to null)
