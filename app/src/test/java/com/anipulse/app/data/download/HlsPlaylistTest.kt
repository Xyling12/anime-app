package com.anipulse.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {

    // Так отдаёт серию CDN Kodik: относительные ссылки с двоеточиями в имени.
    private val kodikUrl = "https://cloud.solodcdn.com/useruploads/abc/def/720.mp4:hls:manifest.m3u8"
    private val kodikPlaylist = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-ALLOW-CACHE:YES
        #EXT-X-PLAYLIST-TYPE:VOD
        #EXT-X-VERSION:3
        #EXT-X-MEDIA-SEQUENCE:1
        #EXTINF:6.000,
        720.mp4:hls:seg-1-v1-a1.ts
        #EXTINF:6.000,
        720.mp4:hls:seg-2-v1-a1.ts
        #EXTINF:3.500,
        720.mp4:hls:seg-3-v1-a1.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun kodikMediaPlaylistResolvesSegmentsNextToPlaylist() {
        val parsed = HlsPlaylist.parse(kodikPlaylist, kodikUrl) as HlsPlaylist.Parsed.Media
        assertEquals(
            listOf(
                "https://cloud.solodcdn.com/useruploads/abc/def/720.mp4:hls:seg-1-v1-a1.ts",
                "https://cloud.solodcdn.com/useruploads/abc/def/720.mp4:hls:seg-2-v1-a1.ts",
                "https://cloud.solodcdn.com/useruploads/abc/def/720.mp4:hls:seg-3-v1-a1.ts",
            ),
            parsed.segments,
        )
        assertNull(parsed.initSegment)
    }

    @Test
    fun windowsLineEndingsAndAbsoluteSegments() {
        val text = "#EXTM3U\r\n#EXTINF:6,\r\nhttps://cdn.example/a.ts\r\n#EXTINF:6,\r\n/root/b.ts\r\n#EXT-X-ENDLIST\r\n"
        val parsed = HlsPlaylist.parse(text, "https://host.example/dir/index.m3u8") as HlsPlaylist.Parsed.Media
        assertEquals(listOf("https://cdn.example/a.ts", "https://host.example/root/b.ts"), parsed.segments)
    }

    @Test
    fun masterPlaylistPicksHighestBandwidth() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360/index.m3u8
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=900000,BANDWIDTH=2500000,RESOLUTION=1280x720
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480
            480/index.m3u8
        """.trimIndent()
        val parsed = HlsPlaylist.parse(text, "https://host.example/ep1/master.m3u8")
        assertEquals(HlsPlaylist.Parsed.Master("https://host.example/ep1/720/index.m3u8"), parsed)
    }

    @Test
    fun fmp4PlaylistKeepsInitSegment() {
        val text = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6,
            seg1.m4s
        """.trimIndent()
        val parsed = HlsPlaylist.parse(text, "https://host.example/v/index.m3u8") as HlsPlaylist.Parsed.Media
        assertEquals("https://host.example/v/init.mp4", parsed.initSegment)
        assertEquals(listOf("https://host.example/v/seg1.m4s"), parsed.segments)
    }

    @Test
    fun keyMethodNoneIsAllowed() {
        val text = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\na.ts\n"
        val parsed = HlsPlaylist.parse(text, "https://host.example/index.m3u8") as HlsPlaylist.Parsed.Media
        assertEquals(1, parsed.segments.size)
    }

    @Test(expected = HlsPlaylist.UnsupportedPlaylistException::class)
    fun encryptedPlaylistIsRejected() {
        val text = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:6,\na.ts\n"
        HlsPlaylist.parse(text, "https://host.example/index.m3u8")
    }

    @Test(expected = HlsPlaylist.UnsupportedPlaylistException::class)
    fun playlistWithoutSegmentsIsRejected() {
        HlsPlaylist.parse("#EXTM3U\n#EXT-X-ENDLIST\n", "https://host.example/index.m3u8")
    }

    @Test
    fun detectsPlaylistByContent() {
        assertTrue(HlsPlaylist.looksLikePlaylist("#EXTM3U\n#EXT-X-VERSION:3"))
        assertTrue(HlsPlaylist.looksLikePlaylist("﻿#EXTM3U"))
        // Начало MPEG-TS (байт синхронизации 0x47) и MP4 (`ftyp`) — это видео.
        assertFalse(HlsPlaylist.looksLikePlaylist("G@\u0011\u0010\u0000"))
        assertFalse(HlsPlaylist.looksLikePlaylist("\u0000\u0000\u0000\u0018ftypmp42"))
    }
}
