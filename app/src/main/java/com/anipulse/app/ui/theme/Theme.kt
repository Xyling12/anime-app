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
    background = Color(0xFF0C0C12),
    surface = Color(0xFF14141D),
    surfaceVariant = Color(0xFF1D1D29),
    onBackground = Color(0xFFF2F0F7),
    onSurface = Color(0xFFF2F0F7),
    onSurfaceVariant = Color(0xFF9D9AB0),
    outline = Color(0xFF3A3A4C),
    outlineVariant = Color(0xFF2B2B3A),
)

// Новая светлая тема: чистый минимализм, белый фон, тёмный текст, акценты
private val LightColors = lightColorScheme(
    primary = Color(0xFF6A4FBF), // Deep purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF4D8D), // Neon Pink for some accents
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF4D8D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE6F3),
    onSecondaryContainer = Color(0xFF4A4A5A),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F7),
    onBackground = Color(0xFF111116),
    onSurface = Color(0xFF111116),
    onSurfaceVariant = Color(0xFF4A4A5A),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF0F0F0),
)

@Composable
fun AnimeLibTheme(
    darkTheme: Boolean = false,
    // Фирменные цвета AniPulse всегда: динамические системные цвета ломали стиль.
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
