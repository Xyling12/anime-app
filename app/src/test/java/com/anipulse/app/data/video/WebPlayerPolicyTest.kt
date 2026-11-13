package com.anipulse.app.data.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPlayerPolicyTest {

    @Test
    fun allowsTrustedHttpsEmbedUrls() {
        assertTrue(WebPlayerPolicy.isAllowedInitialUrl("https://kodikplayer.com/serial/123/hash/720p"))
        assertTrue(WebPlayerPolicy.isAllowedInitialUrl("https://sub.kodik.info/embed/42"))
        assertTrue(WebPlayerPolicy.isAllowedInitialUrl("https://anipulsetv.ru/player"))
    }

    @Test
    fun rejectsInsecureOrDangerousSchemesAndUnknownHosts() {
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("http://kodikplayer.com/serial/1/a/720p"))
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("file:///etc/passwd"))
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("content://com.android.contacts/contacts"))
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("javascript:alert(1)"))
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("https://evil.example/phish.html"))
        assertFalse(WebPlayerPolicy.isAllowedInitialUrl("https://notkodikplayer.com/fake"))
    }

    @Test
    fun navigationRestrictsExternalRedirects() {
        val initial = "https://kodikplayer.com/serial/123/hash/720p"
        assertTrue(WebPlayerPolicy.isAllowedNavigation("https://kodikplayer.com/video/1", initial))
        assertTrue(WebPlayerPolicy.isAllowedNavigation("https://cloud.kodik.cc/player", initial))
        assertFalse(WebPlayerPolicy.isAllowedNavigation("https://ads.tracker.invalid/click", initial))
        assertFalse(WebPlayerPolicy.isAllowedNavigation("intent://scan/#Intent;end", initial))
    }
}
