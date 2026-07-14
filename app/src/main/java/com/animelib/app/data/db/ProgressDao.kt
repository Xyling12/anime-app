package com.animelib.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Upsert
    suspend fun upsert(progress: EpisodeProgress)

    @Query("SELECT * FROM episode_progress WHERE animeId = :animeId AND episode = :episode LIMIT 1")
    suspend fun get(animeId: Long, episode: Int): EpisodeProgress?

    @Query("SELECT * FROM episode_progress WHERE animeId = :animeId")
    suspend fun forAnime(animeId: Long): List<EpisodeProgress>

    @Query("SELECT * FROM episode_progress WHERE animeId = :animeId")
    fun forAnimeFlow(animeId: Long): Flow<List<EpisodeProgress>>

    /** Последнее по каждому тайтлу — для «Продолжить просмотр». */
    @Query(
        """
        SELECT * FROM episode_progress
        WHERE updatedAt IN (SELECT MAX(updatedAt) FROM episode_progress GROUP BY animeId)
        ORDER BY updatedAt DESC LIMIT 30
        """
    )
    fun continueWatching(): Flow<List<EpisodeProgress>>

    /** Тайтлы истории просмотра, свежие сверху — сиды для рекомендаций «Для вас». */
    @Query("SELECT animeId FROM episode_progress GROUP BY animeId ORDER BY MAX(updatedAt) DESC LIMIT :limit")
    suspend fun recentAnimeIds(limit: Int): List<Long>

    /** Все тайтлы, которые пользователь уже трогал (исключаем из рекомендаций). */
    @Query("SELECT DISTINCT animeId FROM episode_progress")
    suspend fun allAnimeIds(): List<Long>

    // Статистика для Профиля
    @Query("SELECT COUNT(*) FROM episode_progress WHERE watched = 1")
    suspend fun watchedEpisodes(): Int

    @Query("SELECT COALESCE(SUM(MIN(positionMs, durationMs)), 0) FROM episode_progress")
    suspend fun totalWatchTimeMs(): Long

    @Query("SELECT COUNT(DISTINCT animeId) FROM episode_progress")
    suspend fun startedTitles(): Int
}
