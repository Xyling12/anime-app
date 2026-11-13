package com.anipulse.app.store

import androidx.activity.ComponentActivity

/** Заглушка оценки в RuStore для прямой сборки с сайта (без зависимости от RuStore SDK). */
object StoreReviewHelper {
    const val showRateInStoreAction: Boolean = false
    const val storeListingUrl: String = StoreReviewPolicy.RUSTORE_APP_URL

    fun maybeRequestReview(
        activity: ComponentActivity,
        onPromptAttempted: () -> Unit,
        onReviewLaunched: () -> Unit,
    ) = Unit
}
