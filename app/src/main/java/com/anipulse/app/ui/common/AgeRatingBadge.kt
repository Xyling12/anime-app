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

/** Сопоставляет возрастной рейтинг Shikimori (`g`/`pg`/`pg_13`/`r`/`r_plus`/`rx`) с маркировкой. */
internal fun resolveAgeRatingBadge(rating: String?): AgeRatingBadgeInfo =
    when (rating?.trim()?.lowercase(Locale.ROOT)) {
        "g" -> AgeRatingBadgeInfo("0+", Color(0xE62E7D32))
        "pg" -> AgeRatingBadgeInfo("6+", Color(0xE60277BD))
        "pg_13" -> AgeRatingBadgeInfo("12+", Color(0xE6455A64))
        "r" -> AgeRatingBadgeInfo("16+", Color(0xE6EF6C00))
        "r_plus", "rx" -> AgeRatingBadgeInfo("18+", Color(0xE6B71C1C))
        else -> AgeRatingBadgeInfo("18+", Color(0xE6B71C1C))
    }

/** Возрастной бейдж тайтла на основании рейтинга Shikimori (с безопасным фоллбэком 18+). */
@Composable
fun AgeRatingBadge(modifier: Modifier = Modifier, rating: String? = null) {
    val info = resolveAgeRatingBadge(rating)
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
