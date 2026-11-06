package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MockLocationMapPickerTest {
    @Test
    fun formatsCoordinatesWithSixDecimalsAndADot() {
        assertEquals("30.500000", formatPickedCoordinate(30.5))
        assertEquals("-114.250000", formatPickedCoordinate(-114.25))
        assertEquals("0.000000", formatPickedCoordinate(0.0))
    }

    @Test
    fun parsesFiniteCoordinatesInsideTheRequestedRange() {
        assertEquals(30.5, parseMapCoordinate(" 30.5 ", -90.0, 90.0)!!, 0.0)
        assertEquals(-180.0, parseMapCoordinate("-180", -180.0, 180.0)!!, 0.0)
        assertNull(parseMapCoordinate("", -90.0, 90.0))
        assertNull(parseMapCoordinate("91", -90.0, 90.0))
        assertNull(parseMapCoordinate("NaN", -90.0, 90.0))
        assertNull(parseMapCoordinate("30,5", -90.0, 90.0))
    }

    @Test
    fun buildsAssetUrlWithOptionalStartAndDarkFlag() {
        assertEquals(
            "file:///android_asset/mockmap/index.html",
            mockMapAssetUrl(null, null, false),
        )
        assertEquals(
            "file:///android_asset/mockmap/index.html?dark=1",
            mockMapAssetUrl(null, null, true),
        )
        assertEquals(
            "file:///android_asset/mockmap/index.html?lat=30.500000&lng=114.250000",
            mockMapAssetUrl(30.5, 114.25, false),
        )
        assertEquals(
            "file:///android_asset/mockmap/index.html?lat=-90.000000&lng=-180.000000&dark=1",
            mockMapAssetUrl(-90.0, -180.0, true),
        )
        assertEquals(
            "file:///android_asset/mockmap/index.html",
            mockMapAssetUrl(91.0, 10.0, false),
        )
        assertEquals(
            "file:///android_asset/mockmap/index.html?dark=1",
            mockMapAssetUrl(30.0, null, true),
        )
    }
}
