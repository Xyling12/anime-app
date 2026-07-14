package com.animelib.app.data.kodik

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Извлечение прямой m3u8-ссылки из плеера kodikplayer.com.
 * Механика (подтверждена по app.player_single.js, 2026-07-07):
 *   POST /ftor  с полями type/hash/id + подписанные d_sign/pd_sign/ref_sign из urlParams;
 *   ответ .links[quality] со ссылкой, зашифрованной ROT13 + base64.
 * Защита Kodik меняется — при неудаче возвращаем null, вызывающий код уходит в WebView-fallback.
 * Весь хрупкий парсинг изолирован здесь.
 */
@Singleton
class KodikLinkExtractor @Inject constructor(
    private val client: OkHttpClient,
) {
    data class VideoLinks(val byQuality: Map<Int, String>)

    private val json = Json { ignoreUnknownKeys = true }
    private val ua =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    suspend fun extract(playerLink: String): VideoLinks? = withContext(Dispatchers.IO) {
        runCatching {
            val pageUrl = if (playerLink.startsWith("//")) "https:$playerLink" else playerLink
            val host = Regex("https?://([^/]+)/").find(pageUrl)?.groupValues?.get(1) ?: "kodikplayer.com"

            // type/hash/id из пути: /{type}/{id}/{hash}/{quality}p
            val path = Regex("//[^/]+/(seria|video|serial|film)/(\\d+)/([0-9a-f]+)/")
                .find(pageUrl) ?: return@runCatching null
            val type = path.groupValues[1]
            val id = path.groupValues[2]
            val hash = path.groupValues[3]

            val page = client.newCall(
                Request.Builder().url(pageUrl).header("User-Agent", ua).build()
            ).execute().use { it.body?.string() } ?: return@runCatching null

            val paramsJson = Regex("urlParams\\s*=\\s*'([^']+)'").find(page)?.groupValues?.get(1)
                ?: return@runCatching null
            val params = json.parseToJsonElement(paramsJson).jsonObject

            val form = FormBody.Builder().apply {
                add("type", type); add("hash", hash); add("id", id)
                add("bad_user", "true"); add("info", "{}")
                for (k in listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign")) {
                    params[k]?.jsonPrimitive?.content?.let { add(k, it) }
                }
            }.build()

            val resp = client.newCall(
                Request.Builder()
                    .url("https://$host/ftor")
                    .header("User-Agent", ua)
                    .header("Referer", pageUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .post(form)
                    .build()
            ).execute().use { it.body?.string() } ?: return@runCatching null

            val links = json.parseToJsonElement(resp).jsonObject["links"]?.jsonObject
                ?: return@runCatching null

            val out = mutableMapOf<Int, String>()
            for ((quality, arr) in links) {
                val src = arr.jsonArray.firstOrNull()?.jsonObject?.get("src")?.jsonPrimitive?.content ?: continue
                decodeSrc(src)?.let { out[quality.toIntOrNull() ?: 0] = it }
            }
            if (out.isEmpty()) null else VideoLinks(out)
        }.getOrNull()
    }

    /** src либо открытый URL, либо ROT13 по латинице + base64. */
    internal fun decodeSrc(src: String): String? {
        if (src.startsWith("//")) return "https:$src"
        if (src.startsWith("http")) return src
        val rot = buildString {
            for (c in src) append(
                when (c) {
                    in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    else -> c
                }
            )
        }
        return runCatching {
            val d = String(Base64.decode(rot, Base64.DEFAULT))
            when {
                d.startsWith("//") -> "https:$d"
                d.startsWith("http") -> d
                else -> null
            }
        }.getOrNull()
    }
}
