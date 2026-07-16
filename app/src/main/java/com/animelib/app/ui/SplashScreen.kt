package com.animelib.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.animelib.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashGradient = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFFF4D8D)))

/**
 * Анимированная заставка при холодном старте: расходящееся пульс-кольцо, проявление фирменного
 * знака (буква «А» из ic_launcher_foreground) и подпись «AniPulse», затем общее затухание.
 * Окно уже тёмное (Theme.AnimeLib.windowBackground), поэтому белой вспышки перед этим нет.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val markScale = remember { Animatable(0.5f) }
    val markAlpha = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.7f) }
    val ringAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val splashAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            markScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        launch { markAlpha.animateTo(1f, tween(380)) }
        launch {
            ringAlpha.snapTo(0.55f)
            launch { ringScale.animateTo(1.9f, tween(900, easing = LinearOutSlowInEasing)) }
            ringAlpha.animateTo(0f, tween(900))
        }
        delay(340)
        textAlpha.animateTo(1f, tween(360))
        delay(650)
        splashAlpha.animateTo(0f, tween(320))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = splashAlpha.value }
            .background(Color(0xFF0C0C12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) {
                    drawCircle(
                        brush = SplashGradient,
                        radius = size.minDimension / 2.4f * ringScale.value,
                        alpha = ringAlpha.value,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = markScale.value
                            scaleY = markScale.value
                            alpha = markAlpha.value
                        },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "AniPulse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
        }
    }
}
