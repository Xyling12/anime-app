package com.anipulse.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class AgeRatingBadgeInfo(
    val label: String,
    val background: Color,
)

/** Сопоставляет известный возрастной рейтинг Shikimori (`g`/`pg`/`pg_13`/`r`/`r_plus`/`rx`) с маркировкой. */
internal fun resolveKnownAgeRatingBadge(rating: String?): AgeRatingBadgeInfo? =
    when (rating?.trim()?.lowercase(Locale.ROOT)) {
        "g" -> AgeRatingBadgeInfo("0+", Color(0xE62E7D32))
        "pg" -> AgeRatingBadgeInfo("6+", Color(0xE60277BD))
        "pg_13" -> AgeRatingBadgeInfo("12+", Color(0xE6455A64))
        "r" -> AgeRatingBadgeInfo("16+", Color(0xE6EF6C00))
        "r_plus", "rx" -> AgeRatingBadgeInfo("18+", Color(0xE6B71C1C))
        else -> null
    }

/** Сопоставляет возрастной рейтинг Shikimori с обязательным безопасным фоллбэком `18+`. */
internal fun resolveAgeRatingBadge(rating: String?): AgeRatingBadgeInfo =
    resolveKnownAgeRatingBadge(rating) ?: AgeRatingBadgeInfo("18+", Color(0xE6B71C1C))

/** Возвращает бейдж или null, когда рейтинг не указан и фоллбэк отключён (для списочных карточек). */
internal fun resolveAgeRatingBadgeOrNull(rating: String?, showFallback: Boolean): AgeRatingBadgeInfo? =
    resolveKnownAgeRatingBadge(rating) ?: if (showFallback) AgeRatingBadgeInfo("18+", Color(0xE6B71C1C)) else null

/** Возрастной бейдж тайтла на основании рейтинга Shikimori (для карточек списков можно отключить фоллбэк). */
@Composable
fun AgeRatingBadge(
    modifier: Modifier = Modifier,
    rating: String? = null,
    showFallback: Boolean = true,
) {
    val info = resolveAgeRatingBadgeOrNull(rating, showFallback) ?: return
    Text(
        text = info.label,
        modifier = modifier
            .background(info.background, RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}
