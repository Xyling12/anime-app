package com.anipulse.app

import com.anipulse.app.data.SyncPayload
import com.anipulse.app.data.SyncedFavorite
import com.anipulse.app.data.SyncedProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSyncPayloadSerialization() {
        val payload = SyncPayload(
            progress = listOf(
                SyncedProgress(
                    animeId = 42L,
                    episode = 1,
                    positionMs = 120_000L,
                    durationMs = 1_400_000L,
                    watched = false,
                    dubId = "anilibria",
                    title = "Test Anime",
                    posterId = 42L,
                    totalEpisodes = 12,
                    updatedAt = 1700000000000L
                )
            ),
            favorites = listOf(
                SyncedFavorite(
                    animeId = 42L,
                    title = "Test Anime",
                    score = "9.5",
                    status = "watching",
                    updatedAt = 1700000000000L,
                    deleted = false
                )
            )
        )

        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"animeId\":42"))
        assertTrue(encoded.contains("\"positionMs\":120000"))
        assertTrue(encoded.contains("\"status\":\"watching\""))

        val decoded = json.decodeFromString<SyncPayload>(encoded)
        assertEquals(1, decoded.progress.size)
        assertEquals(1, decoded.favorites.size)
        assertEquals(42L, decoded.progress[0].animeId)
        assertEquals(120_000L, decoded.progress[0].positionMs)
        assertEquals("watching", decoded.favorites[0].status)
    }

    @Test
    fun testLwwConflictResolution() {
        val oldProgress = SyncedProgress(animeId = 1L, episode = 1, positionMs = 5000L, updatedAt = 100L)
        val newProgress = SyncedProgress(animeId = 1L, episode = 1, positionMs = 15000L, updatedAt = 200L)

        val winningProgress = if (newProgress.updatedAt >= oldProgress.updatedAt) newProgress else oldProgress
        assertEquals(15000L, winningProgress.positionMs)
    }
}
