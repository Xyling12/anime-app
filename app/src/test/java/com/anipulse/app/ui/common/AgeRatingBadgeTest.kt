package com.anipulse.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeRatingBadgeTest {

    @Test
    fun mapsAllShikimoriRatingsAndFallback() {
        assertEquals("0+", resolveAgeRatingBadge("g").label)
        assertEquals("6+", resolveAgeRatingBadge("pg").label)
        assertEquals("12+", resolveAgeRatingBadge("pg_13").label)
        assertEquals("12+", resolveAgeRatingBadge(" PG_13 ").label)
        assertEquals("16+", resolveAgeRatingBadge("r").label)
        assertEquals("18+", resolveAgeRatingBadge("r_plus").label)
        assertEquals("18+", resolveAgeRatingBadge("rx").label)
        assertEquals("18+", resolveAgeRatingBadge(null).label)
        assertEquals("18+", resolveAgeRatingBadge("unknown").label)
    }

    @Test
    fun hidesFallbackWhenDisabledButPreservesKnownRatings() {
        assertNull(resolveAgeRatingBadgeOrNull(null, showFallback = false))
        assertNull(resolveAgeRatingBadgeOrNull("", showFallback = false))
        assertNull(resolveAgeRatingBadgeOrNull("none", showFallback = false))
        assertNull(resolveAgeRatingBadgeOrNull("unknown", showFallback = false))
        assertEquals("12+", resolveAgeRatingBadgeOrNull("pg_13", showFallback = false)?.label)
        assertEquals("18+", resolveAgeRatingBadgeOrNull("rx", showFallback = false)?.label)
        assertEquals("18+", resolveAgeRatingBadgeOrNull(null, showFallback = true)?.label)
    }
}
