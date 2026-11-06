package com.example.toolbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationConfigTest {
    @Test
    fun acceptsEquatorPolesAndDateLineWithoutInventingDefaults() {
        listOf("0" to "0", "-90" to "-180", "90" to "180", " 30.5 " to " -114.25 ").forEach { (lat, lon) ->
            assertNotNull(MockLocationConfig.parse(lat, lon, "25000", 30))
        }
        assertEquals(0.0, MockLocationConfig.parse("0", "0", "50", 15)?.latitude ?: Double.NaN, 0.0)
    }

    @Test
    fun rejectsMissingMalformedOutOfRangeOrNonFiniteCoordinates() {
        listOf("", "NaN", "Infinity", "91", "-90.01", "30,5", "text").forEach {
            assertNull(it, MockLocationConfig.parse(it, "10", "50", 30))
        }
        listOf("", "NaN", "Infinity", "180.01", "-181").forEach {
            assertNull(it, MockLocationConfig.parse("10", it, "50", 30))
        }
    }

    @Test
    fun parsesAccuracyMetersInsideTheAllowedRange() {
        assertEquals(50f, MockLocationConfig.parseAccuracyMeters("50")!!, 0f)
        assertEquals(1f, MockLocationConfig.parseAccuracyMeters(" 1 ")!!, 0f)
        assertEquals(1_000_000f, MockLocationConfig.parseAccuracyMeters("1000000")!!, 0f)
        listOf("", "0", "0.9", "1000001", "NaN", "Infinity", "50,0").forEach {
            assertNull(it, MockLocationConfig.parseAccuracyMeters(it))
        }
    }

    @Test
    fun rejectsInvalidAccuracyOrUnsupportedDurations() {
        listOf("0", "-1", "0.9", "1000001", "NaN", "Infinity", "").forEach {
            assertNull(MockLocationConfig.parse("30", "114", it, 30))
        }
        listOf(-1, 0, 1, 90, Int.MAX_VALUE).forEach {
            assertNull(MockLocationConfig.parse("30", "114", "50", it))
        }
        listOf(15, 30, 60).forEach {
            assertNotNull(MockLocationConfig.parse("30", "114", "1000000", it))
        }
    }

    @Test
    fun onlyStartingRunningAndStoppingAreBusy() {
        MockLocationPhase.entries.forEach {
            val busy = MockLocationState(it).busy
            if (it in listOf(MockLocationPhase.STARTING, MockLocationPhase.RUNNING, MockLocationPhase.STOPPING)) {
                assertTrue(busy)
            } else assertFalse(busy)
        }
    }
}
