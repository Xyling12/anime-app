package com.animelib.app.data.video

import com.animelib.app.data.Api
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
    private val json = Json { ignoreUnknownKeys = true }

    /** Точные таймкоды опенинга и эндинга из AniSkip по MAL id + серии (через шлюз). */
    suspend fun skipTimes(malId: Long, episode: Int): Pair<Skip?, Skip?> = withContext(Dispatchers.IO) {
        val url = Api.GATEWAY + "aniskip/v2/skip-times/$malId/$episode?types=op&types=ed&episodeLength=0"
        val body = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null to null
        runCatching {
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                ?: return@withContext null to null
            fun find(type: String): Skip? {
                val e = results.map { it.jsonObject }.firstOrNull { it["skipType"]?.jsonPrimitive?.content == type }
                    ?: return null
                val iv = e["interval"]!!.jsonObject
                return Skip(
                    start = iv["startTime"]!!.jsonPrimitive.content.toFloat().toInt(),
                    stop = iv["endTime"]!!.jsonPrimitive.content.toFloat().toInt(),
                )
            }
            find("op") to find("ed")
        }.getOrDefault(null to null)
    }

    suspend fun dubs(malId: Long, names: List<String>): List<Dub> = coroutineScope {
        sources
            .map { src -> async { runCatching { src.findDubs(malId, names) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    suspend fun stream(dub: Dub, episode: Int): EpisodeStream? {
        val source = sources.firstOrNull { it.type == dub.source } ?: return null
        return runCatching { source.episodeStream(dub, episode) }.getOrNull()
    }
}
