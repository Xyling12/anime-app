package com.anipulse.app.data.video

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разделяемое между экраном тайтла и плеером состояние текущего воспроизведения.
 * Позволяет передать список озвучек и контекст, не сериализуя объекты в навигацию.
 */
@Singleton
class PlaybackSession @Inject constructor() {
    var animeId: Long = 0
    var title: String = ""
    var posterId: Long = 0
    var dubs: List<Dub> = emptyList()
    var selectedDubId: String? = null
    var episode: Int = 1
    var totalEpisodes: Int = 0
    /** true — начать серию с начала, игнорируя сохранённую позицию. */
    var startOver: Boolean = false

    fun start(
        animeId: Long,
        title: String,
        posterId: Long,
        dubs: List<Dub>,
        selectedDubId: String?,
        episode: Int,
        totalEpisodes: Int,
        startOver: Boolean,
    ) {
        this.animeId = animeId
        this.title = title
        this.posterId = posterId
        this.dubs = dubs
        this.selectedDubId = selectedDubId
        this.episode = episode
        this.totalEpisodes = totalEpisodes
        this.startOver = startOver
    }

    fun selectedDub(): Dub? = dubs.firstOrNull { it.id == selectedDubId } ?: dubs.firstOrNull()
}
