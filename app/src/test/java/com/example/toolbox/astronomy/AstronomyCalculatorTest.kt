package com.example.toolbox.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AstronomyCalculatorTest {

    @Test
    fun convertsJulianDateCorrectly() {
        // J2000.0 epoch: 2000-01-01 12:00:00 UTC = 2451545.0 JD
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2000, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val jd = AstronomyCalculator.millisToJulianDate(cal.timeInMillis)
        assertEquals(2451545.0, jd, 0.001)
    }

    @Test
    fun calculatesSunCoordinates() {
        val jd = 2451545.0 // J2000.0
        val sun = AstronomyCalculator.calculateSunEquatorial(jd)
        // Around J2000.0 (Jan 1), Sun is around RA ~ 280° (18h 40m) and Dec ~ -23°
        assertTrue("Sun RA should be near 280°: ${sun.raDegrees}", sun.raDegrees in 270.0..290.0)
        assertTrue("Sun Dec should be near -23°: ${sun.decDegrees}", sun.decDegrees in -24.0..-22.0)
        assertTrue("Sun distance should be near 1 AU: ${sun.distanceAu}", sun.distanceAu in 0.98..1.02)
    }

    @Test
    fun calculatesMoonPhaseInfo() {
        val now = System.currentTimeMillis()
        val jd = AstronomyCalculator.millisToJulianDate(now)
        val moonPhase = AstronomyCalculator.calculateMoonPhase(jd)

        assertNotNull(moonPhase.phase)
        assertTrue(moonPhase.illuminationPercent in 0..100)
        assertTrue(moonPhase.moonAgeDays in 0.0..30.0)
        assertTrue(moonPhase.phaseFraction in 0.0..1.0)
    }

    @Test
    fun generatesNightTimelineForBeijing() {
        // Beijing: 39.9° N, 116.4° E
        val now = System.currentTimeMillis()
        val timeline = AstronomyCalculator.generateNightTimeline(
            referenceDateMillis = now,
            latitude = 39.9042,
            longitude = 116.4074,
        )

        assertTrue("Sunset should be before Sunrise", timeline.sunsetMillis < timeline.sunriseMillis)
        assertTrue("Dusk should be <= Dawn", timeline.duskAstronomicalMillis <= timeline.dawnAstronomicalMillis)
        assertEquals(8, timeline.bodies.size)

        val moon = timeline.bodies.find { it.body == CelestialBodyType.MOON }
        assertNotNull(moon)
        assertTrue("Max altitude should be >= -90 and <= 90", moon!!.maxAltitudeDegrees in -90.0..90.0)
    }
}
