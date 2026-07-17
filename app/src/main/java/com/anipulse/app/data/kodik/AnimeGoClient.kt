package com.anipulse.app.data.kodik

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над animego.me как поставщиком озвучек Kodik.
 * У AnimeGO нет открытого JSON API, поэтому парсим HTML/AJAX-ответы регулярками.
 * Всё изолировано здесь: если разметка изменится — правится только этот файл.
 */
@Singleton
class AnimeGoClient @Inject constructor(
    private val client: OkHttpClient,
) {
    data class Found(val animeId: String, val title: String)
    data class KodikDub(
        val translationTitle: String,
        val playerUrl: String, // //kodikplayer.com/seria/{id}/{hash}/720p
    )

    private val ua =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Поиск тайтла на AnimeGO: возвращает пары (id, название) для строгой проверки. */
    suspend fun search(query: String): List<Found> = withContext(Dispatchers.IO) {
        val html = get("https://5-42-99-195.sslip.io/alapi/animego/search/all?type=small&q=" + query.enc())
            ?: return@withContext emptyList()
        val u = html.replace("\\/", "/").replace("\\u0022", "\"")
        // Карточка: href="/anime/slug-ID" ... <img ... alt="Название">
        val withTitle = Regex(
            "href=\"/anime/[a-z0-9-]+-(\\d+)\"[^>]*>\\s*<img[^>]*alt=\"([^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(u).map { Found(animeId = it.groupValues[1], title = it.groupValues[2]) }.toList()
        if (withTitle.isNotEmpty()) return@withContext withTitle.distinctBy { it.animeId }
        // запасной вариант — только id
        Regex("/anime/[a-z0-9-]+-(\\d+)").findAll(u)
            .map { Found(animeId = it.groupValues[1], title = "") }
            .distinctBy { it.animeId }.toList()
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Список озвучек Kodik для тайтла (правильный эндпоинт /player/{id}). */
    suspend fun dubs(animeId: String): List<KodikDub> = withContext(Dispatchers.IO) {
        val body = get(
            url = "https://5-42-99-195.sslip.io/alapi/animego/player/$animeId",
            xhr = true,
        ) ?: return@withContext emptyList()

        // Ответ: {"data":{"content":"<html с кнопками озвучек>"}} — JSON-парсер сам разэкранирует.
        val html = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]
                ?.jsonObject?.get("content")?.jsonPrimitive?.content
        }.getOrNull() ?: body

        // Каждая кнопка: data-player="//kodikplayer.com/..." ... data-translation-title="..."
        Regex(
            "data-player=\"(//[^\"]*kodik[^\"]+)\"[^>]*?data-translation-title=\"([^\"]*)\"",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(html)
            .map { m -> KodikDub(translationTitle = m.groupValues[2].ifBlank { "Kodik" }, playerUrl = m.groupValues[1]) }
            .distinctBy { it.translationTitle }
            .toList()
    }

    private fun get(url: String, xhr: Boolean = false, referer: String? = null): String? = runCatching {
        val b = Request.Builder().url(url).header("User-Agent", ua)
        if (xhr) b.header("X-Requested-With", "XMLHttpRequest")
        referer?.let { b.header("Referer", it) }
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()

    private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")
}
