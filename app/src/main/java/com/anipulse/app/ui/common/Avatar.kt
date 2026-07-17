package com.anipulse.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 12 пресетов аватара: градиент + аниме-эмодзи. Индекс = id на сервере. */
val AVATAR_PRESETS: List<Pair<List<Color>, String>> = listOf(
    listOf(Color(0xFF7C4DFF), Color(0xFFFF4D8D)) to "⚡",
    listOf(Color(0xFFFF4D8D), Color(0xFFFF9D4D)) to "🔥",
    listOf(Color(0xFF4DC3FF), Color(0xFF7C4DFF)) to "🌙",
    listOf(Color(0xFF66BB6A), Color(0xFF4DC3FF)) to "🍃",
    listOf(Color(0xFFFF9D4D), Color(0xFFFFD54F)) to "⭐",
    listOf(Color(0xFFEF5350), Color(0xFF7C4DFF)) to "👹",
    listOf(Color(0xFF7C4DFF), Color(0xFF4DFFC3)) to "🐉",
    listOf(Color(0xFFFF4D8D), Color(0xFFB39DFF)) to "🌸",
    listOf(Color(0xFF29B6F6), Color(0xFF66BB6A)) to "🗡️",
    listOf(Color(0xFF9B7BFF), Color(0xFFFF4D8D)) to "😼",
    listOf(Color(0xFFFFD54F), Color(0xFFFF4D8D)) to "🍜",
    listOf(Color(0xFF4DC3FF), Color(0xFFFF4D8D)) to "🎧",
)

@Composable
fun Avatar(id: Int, size: Dp, modifier: Modifier = Modifier) {
    val preset = AVATAR_PRESETS[id.coerceIn(0, AVATAR_PRESETS.size - 1)]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(preset.first)),
        contentAlignment = Alignment.Center,
    ) {
        Text(preset.second, fontSize = (size.value * 0.45f).sp)
    }
}
