package com.animelib.app.data.video

/**
 * Абстракция над поставщиком видео (AniLibria, Kodik/AnimeGO, …).
 * Позволяет добавлять/убирать источники, не трогая UI и плеер.
 */
interface VideoSource {
    val type: VideoSourceType

    /**
     * Найти доступные озвучки тайтла.
     * @param malId id MyAnimeList (== Shikimori id для большинства тайтлов)
     * @param names названия для сопоставления (рус/англ/ромадзи)
     */
    suspend fun findDubs(malId: Long, names: List<String>): List<Dub>

    /** Получить поток конкретного эпизода выбранной озвучки. */
    suspend fun episodeStream(dub: Dub, episode: Int): EpisodeStream?
}
