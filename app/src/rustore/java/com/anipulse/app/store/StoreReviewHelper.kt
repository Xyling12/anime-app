package com.anipulse.app.store

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import ru.rustore.sdk.review.RuStoreReviewManagerFactory

/** Интеграция оценки приложения в RuStore (активна только в rustore-сборке). */
object StoreReviewHelper {
    const val showRateInStoreAction: Boolean = true
    const val storeListingUrl: String = StoreReviewPolicy.RUSTORE_APP_URL

    fun maybeRequestReview(
        activity: ComponentActivity,
        onPromptAttempted: () -> Unit,
        onReviewLaunched: () -> Unit,
    ) {
        onPromptAttempted()
        val manager = runCatching { RuStoreReviewManagerFactory.create(activity) }.getOrNull() ?: return
        manager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                if (
                    activity.isFinishing ||
                    activity.isDestroyed ||
                    !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    return@addOnSuccessListener
                }
                manager.launchReviewFlow(reviewInfo)
                    .addOnSuccessListener { onReviewLaunched() }
                    .addOnFailureListener { }
            }
            .addOnFailureListener { }
    }
}
