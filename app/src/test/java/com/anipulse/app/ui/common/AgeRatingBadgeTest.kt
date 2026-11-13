package com.anipulse.app.ui.common

import org.junit.Assert.assertEquals
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
}
