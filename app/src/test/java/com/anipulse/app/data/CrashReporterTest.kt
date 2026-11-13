package com.anipulse.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `redactSensitive strips bearer jwt and query secrets`() {
        val fakeJwt = listOf("eyJ" + "hbGciOiJIUzI1NiJ9", "eyJzdWIiOiIxIn0", "signature123").joinToString(".")
        val raw = "HTTP failed Authorization: Bearer secret_token_12345 " +
            "jwt=$fakeJwt " +
            "url=https://anipulsetv.ru/auth?code=abcdef987654&episode=1&token=xyz"

        val redacted = CrashReporter.redactSensitive(raw)

        assertFalse(redacted.contains("secret_token_12345"))
        assertFalse(redacted.contains("signature123"))
        assertFalse(redacted.contains("abcdef987654"))
        assertFalse(redacted.contains("=xyz"))
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains("[JWT]"))
        assertTrue(redacted.contains("code=[REDACTED]"))
        assertTrue(redacted.contains("token=[REDACTED]"))
        assertTrue(redacted.contains("episode=1"))
    }

    @Test
    fun `buildRecord formats metadata caps length and sanitizes message`() {
        val longMsg = "Bearer top_secret_999 " + "x".repeat(4000)
        val record = CrashReporter.buildRecord(
            threadName = "main",
            throwable = IllegalStateException(longMsg),
            versionName = "1.0.0",
            versionCode = 32,
            device = "Pixel Tablet",
            osVersion = "Android 15",
            abis = "arm64-v8a",
            timestampMs = 1_700_000_000_000L,
        )

        assertTrue(record.reportText.length <= CrashReporter.MAX_REPORT_CHARS)
        assertTrue(record.reportText.contains("[CRASH] AniPulse v1.0.0 (32)"))
        assertTrue(record.reportText.contains("ABI: arm64-v8a"))
        assertTrue(record.reportText.contains("Поток: main"))
        assertFalse(record.reportText.contains("top_secret_999"))
        assertFalse(record.summary.contains("top_secret_999"))
    }

    @Test
    fun `save read and clear roundtrip works and handles broken json`() {
        val file = File(tmp.root, "last_crash.json")
        assertNull(CrashReporter.readFromFile(file))

        val record = CrashReporter.buildRecord(
            threadName = "worker-1",
            throwable = RuntimeException("boom"),
            versionName = "1.0.0",
            versionCode = 32,
            device = "Test",
            osVersion = "Android 14",
            abis = "armeabi-v7a",
            timestampMs = 1_700_000_000_000L,
        )
        CrashReporter.saveToFile(file, record)

        val restored = CrashReporter.readFromFile(file)
        assertNotNull(restored)
        assertEquals(record, restored)

        CrashReporter.clearFile(file)
        assertFalse(file.exists())
        assertNull(CrashReporter.readFromFile(file))

        file.writeText("{not valid json", Charsets.UTF_8)
        assertNull(CrashReporter.readFromFile(file))
    }
}
