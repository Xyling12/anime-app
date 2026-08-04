package com.anipulse.app.data

import com.anipulse.app.data.db.EpisodeProgress
import com.anipulse.app.data.db.Favorite
import com.anipulse.app.data.db.FavoriteDao
import com.anipulse.app.data.db.ProgressDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val gateway: GatewayApi,
    private val settings: SettingsStore,
    private val progressDao: ProgressDao,
    private val favoriteDao: FavoriteDao,
) {
    private val mutex = Mutex()

    suspend fun syncAll() = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        val payload = SyncPayload(
            progress = progressDao.allOnce().map(EpisodeProgress::toSynced),
            favorites = favoriteDao.allOnce().map(Favorite::toSynced),
        )
        apply(gateway.mergeSyncState(bearer, payload))
    }

    suspend fun pushProgress(value: EpisodeProgress) = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        apply(gateway.mergeSyncState(bearer, SyncPayload(progress = listOf(value.toSynced()))))
    }

    suspend fun pushFavorite(value: Favorite) = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        apply(gateway.mergeSyncState(bearer, SyncPayload(favorites = listOf(value.toSynced()))))
    }

    suspend fun deleteFavorite(animeId: Long) = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        val tombstone = SyncedFavorite(
            animeId = animeId,
            updatedAt = System.currentTimeMillis(),
            deleted = true,
        )
        apply(gateway.mergeSyncState(bearer, SyncPayload(favorites = listOf(tombstone))))
    }

    private suspend fun apply(payload: SyncPayload) {
        payload.progress.forEach { progressDao.upsert(it.toEntity()) }
        payload.favorites.forEach {
            if (it.deleted) favoriteDao.delete(it.animeId) else favoriteDao.upsert(it.toEntity())
        }
    }
}

private fun EpisodeProgress.toSynced() = SyncedProgress(
    animeId, episode, positionMs, durationMs, watched, dubId, title, posterId, totalEpisodes, updatedAt,
)

private fun SyncedProgress.toEntity() = EpisodeProgress(
    animeId, episode, positionMs, durationMs, watched, dubId, title, posterId, totalEpisodes, updatedAt,
)

private fun Favorite.toSynced() = SyncedFavorite(
    animeId = animeId,
    title = title,
    score = score,
    status = status,
    updatedAt = addedAt,
)

private fun SyncedFavorite.toEntity() = Favorite(
    animeId = animeId,
    title = title,
    score = score,
    status = status,
    addedAt = updatedAt,
)
