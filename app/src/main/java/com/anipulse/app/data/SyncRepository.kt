package com.anipulse.app.data

import com.anipulse.app.data.db.ContinueHidden
import com.anipulse.app.data.db.ContinueHiddenDao
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
    private val continueHiddenDao: ContinueHiddenDao,
) {
    private val mutex = Mutex()

    suspend fun syncAll() = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        val payload = SyncPayload(
            progress = progressDao.allOnce().map(EpisodeProgress::toSynced),
            favorites = favoriteDao.allOnce().map(Favorite::toSynced),
            hiddenContinue = continueHiddenDao.allOnce().associate { it.animeId.toString() to it.hiddenAt },
        )
        apply(gateway.mergeSyncState(bearer, payload))
    }

    suspend fun pushProgress(value: EpisodeProgress) = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        apply(gateway.mergeSyncState(bearer, SyncPayload(progress = listOf(value.toSynced()))))
    }

    suspend fun pushHiddenContinue(animeId: Long, hiddenAt: Long) = mutex.withLock {
        val bearer = settings.authToken?.let { "Bearer $it" } ?: return@withLock
        apply(gateway.mergeSyncState(bearer, SyncPayload(hiddenContinue = mapOf(animeId.toString() to hiddenAt))))
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
        payload.progress.forEach {
            if (it.deleted) progressDao.delete(it.animeId, it.episode) else progressDao.upsert(it.toEntity())
        }
        payload.favorites.forEach {
            if (it.deleted) favoriteDao.delete(it.animeId) else favoriteDao.upsert(it.toEntity())
        }
        if (payload.hiddenContinue.isNotEmpty()) {
            val existing = continueHiddenDao.allOnce().associate { it.animeId to it.hiddenAt }
            payload.hiddenContinue.forEach { (idStr, hiddenAt) ->
                val animeId = idStr.toLongOrNull() ?: return@forEach
                val prev = existing[animeId]
                if (prev == null || hiddenAt > prev) {
                    continueHiddenDao.hide(ContinueHidden(animeId, hiddenAt))
                }
            }
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
