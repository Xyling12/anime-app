package com.anipulse.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Short cold-start brand beat used only by the isolated redesign preview. */
@Composable
fun LaunchPulseOverlay() {
    val transition = rememberInfiniteTransition(label = "launch-pulse")
    val scale = transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing)),
        label = "logo-scale",
    )
    Box(
        Modifier.fillMaxSize().background(Color(0xFF09090F)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "A",
            color = Color.White,
            fontSize = 108.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.scale(scale.value),
        )
        Canvas(Modifier.size(width = 190.dp, height = 72.dp)) {
            val y = size.height * .58f
            val points = listOf(
                Offset(0f, y), Offset(size.width * .30f, y),
                Offset(size.width * .40f, y - size.height * .32f),
                Offset(size.width * .49f, y + size.height * .30f),
                Offset(size.width * .59f, y - size.height * .20f),
                Offset(size.width * .68f, y), Offset(size.width, y),
            )
            for (i in 0 until points.lastIndex) {
                drawLine(
                    brush = Brush.linearGradient(listOf(Color(0xFF9B7BFF), Color(0xFFFF4D8D))),
                    start = points[i], end = points[i + 1],
                    strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
    }
}
