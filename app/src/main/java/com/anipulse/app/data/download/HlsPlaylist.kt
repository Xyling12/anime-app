package com.anipulse.app.data.download

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Разбор HLS-плейлиста для скачивания серии.
 *
 * Kodik и AniLibria отдают серию не файлом, а плейлистом (`720.mp4:hls:manifest.m3u8`)
 * со списком кусочков MPEG-TS по ~6 секунд. Раньше загрузчик сохранял сам плейлист —
 * 12 КБ текста — под именем `.mp4` и отмечал серию скачанной; плеер такой файл открыть
 * не мог. Чтобы получить видео, нужно скачать все кусочки и склеить их по порядку:
 * поток MPEG-TS допускает простую конкатенацию, а ExoPlayer играет итоговый `.ts`
 * с перемоткой.
 */
internal object HlsPlaylist {

    sealed class Parsed {
        /** Мастер-плейлист: сначала нужно скачать плейлист выбранного качества. */
        data class Master(val variantUrl: String) : Parsed()

        /**
         * Плейлист серии. [initSegment] есть только у fMP4-потоков (`#EXT-X-MAP`):
         * его пишут в начало файла, тогда склейка — обычный фрагментированный MP4.
         */
        data class Media(val segments: List<String>, val initSegment: String?) : Parsed()
    }

    class UnsupportedPlaylistException(message: String) : Exception(message)

    /** Ответ — плейлист, а не сам видеофайл. */
    fun looksLikePlaylist(head: String): Boolean =
        head.trimStart('﻿', ' ', '\t', '\r', '\n').startsWith("#EXTM3U")

    fun parse(text: String, playlistUrl: String): Parsed {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull()?.let(::looksLikePlaylist) != true) {
            throw UnsupportedPlaylistException("Ответ не похож на плейлист HLS")
        }

        // Мастер-плейлист: URI варианта идёт следующей строкой после #EXT-X-STREAM-INF.
        // Берём вариант с наибольшим битрейтом — качество уже выбрано ссылкой на него.
        val variants = lines.indices
            .filter { lines[it].startsWith("#EXT-X-STREAM-INF") }
            .mapNotNull { i ->
                val uri = lines.getOrNull(i + 1)?.takeUnless { it.startsWith("#") } ?: return@mapNotNull null
                (attribute(lines[i], "BANDWIDTH")?.toLongOrNull() ?: 0L) to uri
            }
        variants.maxByOrNull { it.first }?.let { return Parsed.Master(resolve(playlistUrl, it.second)) }

        var initSegment: String? = null
        val segments = mutableListOf<String>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val method = attribute(line, "METHOD")
                    if (method != null && !method.equals("NONE", ignoreCase = true)) {
                        throw UnsupportedPlaylistException("Эта серия зашифрована, скачать её нельзя")
                    }
                }
                line.startsWith("#EXT-X-MAP") ->
                    initSegment = attribute(line, "URI")?.let { resolve(playlistUrl, it) }
                line.startsWith("#") -> Unit
                else -> segments += resolve(playlistUrl, line)
            }
        }
        if (segments.isEmpty()) throw UnsupportedPlaylistException("В плейлисте нет видео")
        return Parsed.Media(segments, initSegment)
    }

    /**
     * Ссылки в плейлисте относительные и с двоеточиями: `720.mp4:hls:seg-1-v1-a1.ts`.
     * Без `./` часть до двоеточия может быть принята за схему URL, поэтому
     * относительную ссылку явно привязываем к каталогу плейлиста.
     */
    internal fun resolve(base: String, ref: String): String {
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw UnsupportedPlaylistException("Некорректная ссылка на плейлист")
        val absolute = ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("/")
        return (baseUrl.resolve(if (absolute) ref else "./$ref")
            ?: throw UnsupportedPlaylistException("Некорректная ссылка в плейлисте")).toString()
    }

    /** Значение атрибута тега: `METHOD=AES-128` или `URI="init.mp4"`. */
    private fun attribute(line: String, name: String): String? {
        val match = Regex("""(?:^|[:,])$name=("([^"]*)"|[^,]*)""").find(line) ?: return null
        val raw = match.groupValues[1]
        return (if (raw.startsWith("\"")) match.groupValues[2] else raw).trim()
    }
}
