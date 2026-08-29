package com.anipulse.app

import com.anipulse.app.data.ContentPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPolicyTest {

    @Test
    fun testContentPolicyAllowed() {
        assertTrue(ContentPolicy.allowed(1L))
        assertTrue(ContentPolicy.allowed(100L))
        // Death Note ID 1535 should be blocked
        assertFalse(ContentPolicy.allowed(1535L))
        // Tokyo Ghoul ID 22319 should be blocked
        assertFalse(ContentPolicy.allowed(22319L))
    }
}
