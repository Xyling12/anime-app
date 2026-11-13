package com.anipulse.app.data.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionTest {

    @Test
    fun `session start updates fields and falls back to first dub when selected is missing`() {
        val session = PlaybackSession()
        val dub1 = Dub("d1", VideoSourceType.ANILIBRIA, "AniLibria", DubType.VOICE, 12, "ref1")
        val dub2 = Dub("d2", VideoSourceType.KODIK, "AniDUB", DubType.VOICE, 12, "ref2")

        session.start(
            animeId = 42L,
            title = "Тестовый тайтл",
            posterId = 777L,
            dubs = listOf(dub1, dub2),
            selectedDubId = "d2",
            episode = 3,
            totalEpisodes = 12,
            startOver = true,
            rating = "pg_13",
        )

        assertEquals(42L, session.animeId)
        assertEquals("d2", session.selectedDub()?.id)
        assertEquals("pg_13", session.rating)
        assertTrue(session.startOver)

        session.selectedDubId = "unknown"
        assertEquals("d1", session.selectedDub()?.id)

        session.dubs = emptyList()
        assertNull(session.selectedDub())
    }

    @Test
    fun `episode stream defaultQuality prefers 720p or lower before 1080p`() {
        val fhdAndHd = EpisodeStream(
            byQuality = mapOf(480 to "https://a/480.m3u8", 720 to "https://a/720.m3u8", 1080 to "https://a/1080.m3u8"),
        )
        assertTrue(fhdAndHd.isNative)
        assertEquals(720, fhdAndHd.defaultQuality())
        assertEquals(1080, fhdAndHd.bestQuality())

        val fhdOnly = EpisodeStream(byQuality = mapOf(1080 to "https://a/1080.m3u8"))
        assertEquals(1080, fhdOnly.defaultQuality())

        val embedOnly = EpisodeStream(embedUrl = "https://kodik.info/seria/1")
        assertFalse(embedOnly.isNative)
        assertNull(embedOnly.defaultQuality())
        assertNull(embedOnly.bestQuality())
    }
}
