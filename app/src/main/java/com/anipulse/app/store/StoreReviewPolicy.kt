package com.anipulse.app.store

/**
 * Правила показа системного окна оценки приложения в RuStore.
 * Не спрашиваем сразу после установки и не навязываемся чаще заданных интервалов.
 */
object StoreReviewPolicy {
    const val RUSTORE_APP_URL = "https://www.rustore.ru/catalog/app/com.anipulse.app"
    const val MIN_WATCHED_EPISODES = 3
    const val MIN_INSTALL_AGE_MS = 24L * 60 * 60 * 1000L
    const val REVIEW_RETRY_COOLDOWN_MS = 24L * 60 * 60 * 1000L
    const val REVIEW_COOLDOWN_MS = 45L * 24 * 60 * 60 * 1000L
    const val MAX_PROMPTS = 3

    fun shouldPromptReview(
        watchedEpisodes: Int,
        firstSeenAtMs: Long,
        lastPromptAtMs: Long,
        lastAttemptAtMs: Long,
        promptCount: Int,
        nowMs: Long,
    ): Boolean {
        if (watchedEpisodes < MIN_WATCHED_EPISODES) return false
        if (firstSeenAtMs <= 0L || (nowMs - firstSeenAtMs) < MIN_INSTALL_AGE_MS) return false
        if (promptCount >= MAX_PROMPTS) return false
        if (lastAttemptAtMs > 0L && (nowMs - lastAttemptAtMs) < REVIEW_RETRY_COOLDOWN_MS) return false
        if (lastPromptAtMs > 0L && (nowMs - lastPromptAtMs) < REVIEW_COOLDOWN_MS) return false
        return true
    }
}
