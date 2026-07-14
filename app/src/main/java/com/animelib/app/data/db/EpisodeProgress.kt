package com.animelib.app.data.db

import androidx.room.Entity

/** Прогресс просмотра одной серии тайтла: место остановки + отметка «просмотрено». */
@Entity(tableName = "episode_progress", primaryKeys = ["animeId", "episode"])
data class EpisodeProgress(
    val animeId: Long,
    val episode: Int,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val dubId: String? = null,
    val title: String = "",
    val posterId: Long = 0,
    val totalEpisodes: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
