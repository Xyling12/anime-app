package com.anipulse.app.data.video

import java.net.URI
import java.util.Locale

/** Политика безопасности для встроенного WebView-плеера: только HTTPS и доверенные видео-хосты. */
object WebPlayerPolicy {

    private val allowedHostSuffixes = listOf(
        "kodikplayer.com",
        "kodik.cc",
        "kodik.biz",
        "kodik.info",
        "kodik.top",
        "kodik.online",
        "aniqit.com",
        "anivod.com",
        "anipulsetv.ru",
        "anilibria.top",
        "anilibria.tv",
        "anilibria.wtf",
        "aniliberty.top",
    )

    fun extractHost(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
        return host.ifEmpty { null }
    }

    fun isAllowedInitialUrl(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return matchesAllowedSuffix(host)
    }

    fun isAllowedNavigation(url: String, initialUrl: String): Boolean {
        val host = extractHost(url) ?: return false
        val initialHost = extractHost(initialUrl)
        if (initialHost != null && matchesHostOrSubdomain(host, initialHost)) return true
        return matchesAllowedSuffix(host)
    }

    private fun matchesAllowedSuffix(host: String): Boolean =
        allowedHostSuffixes.any { suffix -> matchesHostOrSubdomain(host, suffix) }

    private fun matchesHostOrSubdomain(host: String, base: String): Boolean =
        host == base || host.endsWith(".$base")
}
