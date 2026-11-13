package com.anipulse.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Классификация ширины окна под смартфоны и планшеты (Material 3 breakpoints: 600dp / 840dp). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

data class AdaptiveLayoutInfo(
    val widthDp: Int,
    val smallestWidthDp: Int,
    val widthClass: WidthClass,
    val isTabletDevice: Boolean,
    val useNavigationRail: Boolean,
    val episodeColumns: Int,
    val bannerCardWidthFraction: Float,
    val readableMaxWidth: Dp,
)

internal fun computeAdaptiveLayout(widthDp: Int, smallestWidthDp: Int): AdaptiveLayoutInfo {
    val widthClass = when {
        widthDp >= 840 -> WidthClass.EXPANDED
        widthDp >= 600 -> WidthClass.MEDIUM
        else -> WidthClass.COMPACT
    }
    val episodeCols = when {
        widthDp >= 900 -> 10
        widthDp >= 600 -> 8
        else -> 5
    }
    val bannerFraction = when (widthClass) {
        WidthClass.EXPANDED -> 0.56f
        WidthClass.MEDIUM -> 0.72f
        WidthClass.COMPACT -> 1f
    }
    return AdaptiveLayoutInfo(
        widthDp = widthDp,
        smallestWidthDp = smallestWidthDp,
        widthClass = widthClass,
        isTabletDevice = smallestWidthDp >= 600,
        useNavigationRail = widthClass != WidthClass.COMPACT,
        episodeColumns = episodeCols,
        bannerCardWidthFraction = bannerFraction,
        readableMaxWidth = 760.dp,
    )
}

@Composable
fun rememberAdaptiveLayout(): AdaptiveLayoutInfo {
    val config = LocalConfiguration.current
    return computeAdaptiveLayout(
        widthDp = config.screenWidthDp,
        smallestWidthDp = config.smallestScreenWidthDp,
    )
}
