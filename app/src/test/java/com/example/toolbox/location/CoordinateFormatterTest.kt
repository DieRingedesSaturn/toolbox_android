package com.example.toolbox.location

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatterTest {
    @Test
    fun formatsDecimalLatitude() {
        assertEquals("25.033000°", formatDecimalDegrees(25.033))
    }

    @Test
    fun formatsNegativeLongitudeWithHemisphere() {
        assertEquals("121° 33′ 00.00″ W", formatLongitudeDms(-121.55))
    }

    @Test
    fun formatsEquatorAndPrimeMeridianAsPositiveHemisphere() {
        assertEquals("0° 00′ 00.00″ N", formatLatitudeDms(0.0))
        assertEquals("0° 00′ 00.00″ E", formatLongitudeDms(0.0))
    }
}
