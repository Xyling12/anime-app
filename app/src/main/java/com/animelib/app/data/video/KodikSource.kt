package com.animelib.app.data.video

import com.animelib.app.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kodik как источник видео с ТОЧНЫМ сопоставлением по shikimori_id.
 * Маппинг: наш шлюз /alapi/kodik-find?shikimoriId=X (kodik-api.com/get-player) —
 * отдаёт точную ссылку плеера для любого тайтла (весь каталог, бесплатно, HD).
 * Извлечение: /alapi/kodik?link=…&episode=N — прямые m3u8 по качествам (нативно).
 * Никакого поиска по названию → чужое видео исключено.
 */
@Singleton
class KodikSource @Inject constructor(
    private val client: OkHttpClient,
) : VideoSource {

    override val type = VideoSourceType.KODIK
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findDubs(malId: Long, names: List<String>): List<Dub> = withContext(Dispatchers.IO) {
        // Все озвучки Kodik по shikimori_id (шлюз парсит список переводов).
        val body = httpGet(Api.GATEWAY + "kodik-dubs?shikimoriId=$malId") ?: return@withContext emptyList()
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return@withContext emptyList()
        arr.mapIndexedNotNull { i, el ->
            val o = el.jsonObject
            val link = o["link"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
            val title = o["title"]?.jsonPrimitive?.content?.decodeEntities()?.ifBlank { "Kodik" } ?: "Kodik"
            val isSub = o["type"]?.jsonPrimitive?.content == "sub"
            Dub(
                id = "kodik:$malId:$i",
                source = VideoSourceType.KODIK,
                title = if (isSub) "$title (суб)" else title,
                type = if (isSub) DubType.SUB else DubType.VOICE,
                episodesCount = 0,
                ref = link,
            )
        }
    }

    private fun String.decodeEntities() =
        replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

    override suspend fun episodeStream(dub: Dub, episode: Int): EpisodeStream? = withContext(Dispatchers.IO) {
        val url = Api.GATEWAY + "kodik?link=" + URLEncoder.encode(dub.ref, "UTF-8") + "&episode=$episode"
        val body = httpGet(url) ?: return@withContext null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
        val quality = obj.entries.mapNotNull { (q, v) ->
            val n = q.toIntOrNull() ?: return@mapNotNull null
            n to v.jsonPrimitive.content
        }.toMap()
        if (quality.isEmpty()) null else EpisodeStream(byQuality = quality)
    }

    private fun httpGet(url: String): String? =
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
}
