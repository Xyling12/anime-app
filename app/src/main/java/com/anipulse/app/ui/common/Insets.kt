package com.anipulse.app.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Отступ сверху под статус-бар И вырез камеры. На части реальных устройств
 * (punch-hole/notch) чистый statusBarsPadding() не покрывает вырез — шапки
 * экранов налезали на часы/камеру (репорт владельца с реального телефона).
 * safeDrawing берёт объединение всех системных зон; .only(Top) — без влияния
 * на нижние отступы (ime/навигация обрабатываются отдельно).
 */
fun Modifier.topSafePadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
}
