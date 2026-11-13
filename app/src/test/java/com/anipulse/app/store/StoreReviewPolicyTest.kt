package com.anipulse.app.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreReviewPolicyTest {

    private val dayMs = 24L * 60 * 60 * 1000L

    @Test
    fun waitsUntilEnoughWatchedEpisodesAndInstallAge() {
        val now = 1_700_000_000_000L
        assertFalse(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 2,
                firstSeenAtMs = now - 2 * dayMs,
                lastPromptAtMs = 0L,
                lastAttemptAtMs = 0L,
                promptCount = 0,
                nowMs = now,
            ),
        )
        assertFalse(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 5,
                firstSeenAtMs = now - 10_000L,
                lastPromptAtMs = 0L,
                lastAttemptAtMs = 0L,
                promptCount = 0,
                nowMs = now,
            ),
        )
        assertTrue(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 3,
                firstSeenAtMs = now - dayMs - 1L,
                lastPromptAtMs = 0L,
                lastAttemptAtMs = 0L,
                promptCount = 0,
                nowMs = now,
            ),
        )
    }

    @Test
    fun enforcesAttemptCooldownPromptCooldownAndMaxPrompts() {
        val now = 1_700_000_000_000L
        assertFalse(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 10,
                firstSeenAtMs = now - 60 * dayMs,
                lastPromptAtMs = 0L,
                lastAttemptAtMs = now - 60_000L,
                promptCount = 0,
                nowMs = now,
            ),
        )
        assertFalse(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 10,
                firstSeenAtMs = now - 60 * dayMs,
                lastPromptAtMs = now - 10 * dayMs,
                lastAttemptAtMs = now - 10 * dayMs,
                promptCount = 1,
                nowMs = now,
            ),
        )
        assertTrue(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 10,
                firstSeenAtMs = now - 60 * dayMs,
                lastPromptAtMs = now - 46 * dayMs,
                lastAttemptAtMs = now - 46 * dayMs,
                promptCount = 1,
                nowMs = now,
            ),
        )
        assertFalse(
            StoreReviewPolicy.shouldPromptReview(
                watchedEpisodes = 10,
                firstSeenAtMs = now - 200 * dayMs,
                lastPromptAtMs = now - 60 * dayMs,
                lastAttemptAtMs = now - 60 * dayMs,
                promptCount = StoreReviewPolicy.MAX_PROMPTS,
                nowMs = now,
            ),
        )
    }
}
