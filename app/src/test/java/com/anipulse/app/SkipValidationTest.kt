package com.anipulse.app

import com.anipulse.app.data.video.Skip
import com.anipulse.app.data.video.parseAniSkipTimes
import com.anipulse.app.ui.player.formatPlaybackSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipValidationTest {

    @Test
    fun `isValidInterval rejects empty and reversed ranges`() {
        assertFalse(Skip(0, 0).isValidInterval())
        assertFalse(Skip(-5, 10).isValidInterval())
        assertFalse(Skip(90, 90).isValidInterval())
        assertFalse(Skip(100, 80).isValidInterval())

        assertTrue(Skip(0, 90).isValidInterval())
        assertTrue(Skip(80, 170).isValidInterval())
    }

    @Test
    fun `isValidEnding rejects early and zero endings`() {
        val twentyFourMinMs = 1_420_000L

        assertFalse(Skip(0, 0).isValidEnding(twentyFourMinMs))
        assertFalse(Skip(0, 90).isValidEnding(twentyFourMinMs))
        // Реальный кейс из AniSkip (Mushoku Tensei S2 Ep 1): титры 4..94с при длине 1420с.
        assertFalse(Skip(4, 94).isValidEnding(twentyFourMinMs))
        assertFalse(Skip(500, 590).isValidEnding(twentyFourMinMs))
        // До загрузки длительности видео кнопка не должна появляться.
        assertFalse(Skip(1325, 1415).isValidEnding(0L))
        // Граница за пределами длины серии.
        assertFalse(Skip(1500, 1590).isValidEnding(twentyFourMinMs))

        assertTrue(Skip(1325, 1415).isValidEnding(twentyFourMinMs))
        // Короткая 3-минутная серия.
        assertTrue(Skip(140, 175).isValidEnding(180_000L))
    }

    @Test
    fun `parseAniSkipTimes filters early ending and preserves valid op and ed`() {
        val earlyEdJson = """
            {
              "found": true,
              "results": [
                {"interval":{"startTime":0,"endTime":11},"skipType":"op","episodeLength":1422},
                {"interval":{"startTime":4.688,"endTime":94.688},"skipType":"ed","episodeLength":1419.8}
              ]
            }
        """.trimIndent()

        val (op1, ed1) = parseAniSkipTimes(earlyEdJson)
        assertEquals(Skip(0, 11), op1)
        assertNull(ed1)

        val normalJson = """
            {
              "found": true,
              "results": [
                {"interval":{"startTime":62,"endTime":152.2},"skipType":"op","episodeLength":1420.086},
                {"interval":{"startTime":1323,"endTime":1415},"skipType":"ed","episodeLength":1422}
              ]
            }
        """.trimIndent()

        val (op2, ed2) = parseAniSkipTimes(normalJson)
        assertEquals(Skip(62, 152), op2)
        assertEquals(Skip(1323, 1415), ed2)

        val fallbackSecondEdJson = """
            {
              "found": true,
              "results": [
                {"interval":{"startTime":5,"endTime":95},"skipType":"ed","episodeLength":1420},
                {"interval":{"startTime":1325,"endTime":1415},"skipType":"ed","episodeLength":1420}
              ]
            }
        """.trimIndent()

        val (_, ed3) = parseAniSkipTimes(fallbackSecondEdJson)
        assertEquals(Skip(1325, 1415), ed3)
    }

    @Test
    fun `formatPlaybackSpeed formats standard and fractional speeds`() {
        assertEquals("0.5x", formatPlaybackSpeed(0.5f))
        assertEquals("0.75x", formatPlaybackSpeed(0.75f))
        assertEquals("1x", formatPlaybackSpeed(1.0f))
        assertEquals("1.25x", formatPlaybackSpeed(1.25f))
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
        assertEquals("1.75x", formatPlaybackSpeed(1.75f))
        assertEquals("2x", formatPlaybackSpeed(2.0f))
    }
}
