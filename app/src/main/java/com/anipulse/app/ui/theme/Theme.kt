package com.anipulse.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Фирменная палитра AniPulse: «пульс» — розовый акцент + фиолетовый, тёплый тёмный фон.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4D8D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF47172B),
    onPrimaryContainer = Color(0xFFFFB1C8),
    secondary = Color(0xFF9B7BFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF47172B),      // выбранные чипы — глубокий розовый
    onSecondaryContainer = Color(0xFFFFB1C8),
    background = Color(0xFF09090F),
    surface = Color(0xFF15151F),
    surfaceVariant = Color(0xFF1D1D29),
    onBackground = Color(0xFFF2F0F7),
    onSurface = Color(0xFFF2F0F7),
    onSurfaceVariant = Color(0xFF9D9AB0),
    outline = Color(0xFF343442),
    outlineVariant = Color(0xFF252530),
)

// Светлая тема AniPulse: чистый минимализм, мягкий светлый фон, белые карточки, фирменный розовый пульс
private val LightColors = lightColorScheme(
    primary = Color(0xFFFF4D8D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3EC),
    onPrimaryContainer = Color(0xFF90003E),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EBFF),
    onSecondaryContainer = Color(0xFF3700B3),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF0F4),
    onBackground = Color(0xFF13131A),
    onSurface = Color(0xFF13131A),
    onSurfaceVariant = Color(0xFF6B6E7D),
    outline = Color(0xFFE2E4EA),
    outlineVariant = Color(0xFFECEDF2),
)

@Composable
fun AnimeLibTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
