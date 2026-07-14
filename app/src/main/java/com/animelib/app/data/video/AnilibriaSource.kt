package com.animelib.app.data.video

import com.animelib.app.data.anilibria.AnilibriaApi
import com.animelib.app.data.anilibria.AlRelease
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AniLibria — собственная озвучка студии. Отдаёт прямые HLS-ссылки (480/720/1080)
 * и таймкоды опенинга/эндинга, поэтому играется нативно в ExoPlayer.
 * Сопоставление с Shikimori — поиском по названию (у AniLibria нет поиска по MAL id).
 */
@Singleton
class AnilibriaSource @Inject constructor(
    private val api: AnilibriaApi,
) : VideoSource {

    override val type = VideoSourceType.ANILIBRIA

    override suspend fun findDubs(malId: Long, names: List<String>): List<Dub> {
        val release = resolveRelease(names) ?: return emptyList()
        val count = maxOf(release.episodesTotal, release.episodes.size)
        if (count == 0) return emptyList()
        return listOf(
            Dub(
                id = "anilibria:${release.id}",
                source = VideoSourceType.ANILIBRIA,
                title = "AniLibria",
                type = DubType.VOICE,
                episodesCount = count,
                ref = release.id.toString(),
            )
        )
    }

    override suspend fun episodeStream(dub: Dub, episode: Int): EpisodeStream? {
        val releaseId = dub.ref.toLongOrNull() ?: return null
        val release = runCatching { api.release(releaseId) }.getOrNull() ?: return null
        val ep = release.episodes.firstOrNull { it.ordinal.toInt() == episode }
            ?: release.episodes.getOrNull(episode - 1)
            ?: return null

        val quality = buildMap {
            ep.hls480?.let { put(480, it) }
            ep.hls720?.let { put(720, it) }
            ep.hls1080?.let { put(1080, it) }
        }
        if (quality.isEmpty()) return null

        return EpisodeStream(
            byQuality = quality,
            opening = ep.opening?.toSkip(),
            ending = ep.ending?.toSkip(),
        )
    }

    /**
     * Ищем релиз по названиям. ВАЖНО: возвращаем только при уверенном совпадении имени —
     * иначе лучше не показать озвучку AniLibria, чем проиграть чужое видео (баг «не тот тайтл»).
     */
    private suspend fun resolveRelease(names: List<String>): AlRelease? {
        val queries = names.filter { it.isNotBlank() }.map { it.normalize() }
        for (name in names.filter { it.isNotBlank() }) {
            val results = runCatching { api.search(name) }.getOrNull().orEmpty()
            val match = results.firstOrNull { r ->
                val cand = listOfNotNull(r.name?.main, r.name?.english).map { it.normalize() }
                cand.any { c -> queries.any { q -> namesMatch(c, q) } }
            }
            if (match != null) return match
        }
        return null // уверенного совпадения нет → без AniLibria-озвучки
    }

    /** Совпадение имён: равенство или уверенное вхождение (для длинных названий). */
    private fun namesMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (long, short) = if (a.length >= b.length) a to b else b to a
        return short.length >= 6 && long.contains(short)
    }

    private fun String.normalize() = lowercase().replace(Regex("[^a-zа-я0-9]"), "")

    private fun com.animelib.app.data.anilibria.AlSkip.toSkip(): Skip? {
        val s = start ?: return null
        val e = stop ?: return null
        return Skip(s, e)
    }
}
