package com.anipulse.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun compactPhoneLayoutKeepsBottomBarAndFiveEpisodeColumns() {
        val info = computeAdaptiveLayout(widthDp = 412, smallestWidthDp = 412)
        assertEquals(WidthClass.COMPACT, info.widthClass)
        assertFalse(info.isTabletDevice)
        assertFalse(info.useNavigationRail)
        assertEquals(5, info.episodeColumns)
        assertEquals(1f, info.bannerCardWidthFraction, 0.001f)
    }

    @Test
    fun mediumTabletLayoutEnablesNavigationRailAndEightEpisodeColumns() {
        val info = computeAdaptiveLayout(widthDp = 720, smallestWidthDp = 720)
        assertEquals(WidthClass.MEDIUM, info.widthClass)
        assertTrue(info.isTabletDevice)
        assertTrue(info.useNavigationRail)
        assertEquals(8, info.episodeColumns)
    }

    @Test
    fun expandedTabletLayoutUsesTenEpisodeColumnsAndTwoUpBanner() {
        val info = computeAdaptiveLayout(widthDp = 1280, smallestWidthDp = 800)
        assertEquals(WidthClass.EXPANDED, info.widthClass)
        assertTrue(info.isTabletDevice)
        assertTrue(info.useNavigationRail)
        assertEquals(10, info.episodeColumns)
        assertTrue(info.bannerCardWidthFraction < 0.7f)
    }
}
