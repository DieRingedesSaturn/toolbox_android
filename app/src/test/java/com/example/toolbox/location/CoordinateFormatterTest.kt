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

    @Test
    fun formatsShortCoordinatesNorthEast() {
        assertEquals("39.90°N, 116.41°E", formatShortCoordinates(39.9042, 116.4074))
    }

    @Test
    fun formatsShortCoordinatesSouthWest() {
        assertEquals("33.87°S, 151.21°W", formatShortCoordinates(-33.8688, -151.2093))
    }

    @Test
    fun formatsShortCoordinatesZeroAsNorthEast() {
        assertEquals("0.00°N, 0.00°E", formatShortCoordinates(0.0, 0.0))
    }
}
