package com.anipulse.app.data

import com.anipulse.app.data.shikimori.ShikiAnime
import com.anipulse.app.data.shikimori.ShikiAnimeDetails
import com.anipulse.app.data.shikimori.ShikiRelatedNode
import com.anipulse.app.data.shikimori.ShikimoriApi
import com.anipulse.app.data.video.Dub
import com.anipulse.app.data.video.EpisodeStream
import com.anipulse.app.data.video.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeRepository @Inject constructor(
    private val shikimori: ShikimoriApi,
    private val video: VideoRepository,
    private val gateway: GatewayApi,
) {
    suspend fun catalog(
        page: Int,
        order: String = "popularity",
        status: String? = null,
        kind: String? = null,
        genre: String? = null,
    ): List<ShikiAnime> = shikimori.animes(page = page, order = order, status = status, kind = kind, genre = genre)
        .filter(ContentPolicy::allowed)

    suspend fun search(query: String): List<ShikiAnime> = shikimori.animes(search = query, limit = 30)
        .filter(ContentPolicy::allowed)

    suspend fun genres() = shikimori.genres()

    /** Похожие тайтлы (Shikimori) — база рекомендаций «Для вас». */
    suspend fun similar(id: Long): List<ShikiAnime> = shikimori.similar(id)
        .filter(ContentPolicy::allowed)

    /**
     * Связанные тайтлы (sequel/prequel/side_story/…) с типом связи.
     * Сортируем по году выхода, чтобы пользователю было понятно, в каком порядке смотреть.
     * Anime может быть null (тогда это манга) — пропускаем.
     */
    suspend fun related(id: Long): List<ShikiRelatedNode> = runCatching { shikimori.related(id) }
        .getOrDefault(emptyList())
        .filter { it.anime != null && ContentPolicy.allowed(it.anime) }
        .sortedBy { it.anime?.airedOn.orEmpty() }

    /** Календарь выхода серий (Shikimori). */
    suspend fun calendar() = shikimori.calendar()
        .filter { ContentPolicy.allowed(it.anime) }

    /** Свежие озвученные серии AniLibria (лента «Обновления»). */
    suspend fun anilibriaUpdates() = gateway.anilibriaUpdates()

    suspend fun details(id: Long): ShikiAnimeDetails {
        require(ContentPolicy.allowed(id)) { "Этот тайтл недоступен в текущей версии приложения" }
        return shikimori.animeDetails(id)
    }

    /** Все озвучки тайтла со всех источников. */
    suspend fun dubs(details: ShikiAnimeDetails): List<Dub> =
        video.dubs(
            malId = details.id,
            names = listOfNotNull(details.name, details.russian),
        )

    suspend fun dubs(id: Long): List<Dub> = dubs(details(id))

    suspend fun episodeStream(dub: Dub, episode: Int): EpisodeStream? = video.stream(dub, episode)

    /** Точные таймкоды опенинга и эндинга (AniSkip) по MAL id. */
    suspend fun skipTimes(malId: Long, episode: Int) = video.skipTimes(malId, episode)
}
