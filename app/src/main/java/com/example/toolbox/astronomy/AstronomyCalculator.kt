package com.example.toolbox.astronomy

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object AstronomyCalculator {

    private const val DEG2RAD = PI / 180.0
    private const val RAD2DEG = 180.0 / PI

    private fun normalizeDegrees(deg: Double): Double {
        var result = deg % 360.0
        if (result < 0) result += 360.0
        return result
    }

    private fun normalizeHours(hours: Double): Double {
        var result = hours % 24.0
        if (result < 0) result += 24.0
        return result
    }

    fun millisToJulianDate(millis: Long): Double =
        millis / 86400000.0 + 2440587.5

    fun gmst(jd: Double): Double {
        val d = jd - 2451545.0
        val t = d / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38710000.0
        return normalizeDegrees(gmst)
    }

    fun localSiderealTime(jd: Double, lonDegrees: Double): Double =
        normalizeDegrees(gmst(jd) + lonDegrees)

    data class EquatorialCoordinates(
        val raDegrees: Double,
        val decDegrees: Double,
        val distanceAu: Double = 1.0,
    )

    data class HorizontalCoordinates(
        val altitudeDegrees: Double,
        val azimuthDegrees: Double,
    )

    fun equatorialToHorizontal(
        eq: EquatorialCoordinates,
        latDegrees: Double,
        lonDegrees: Double,
        jd: Double,
    ): HorizontalCoordinates {
        val lst = localSiderealTime(jd, lonDegrees)
        val ha = normalizeDegrees(lst - eq.raDegrees) * DEG2RAD
        val latRad = latDegrees * DEG2RAD
        val decRad = eq.decDegrees * DEG2RAD

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(ha)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
        val altDeg = altRad * RAD2DEG

        val cosAlt = cos(altRad)
        val azRad = if (abs(cosAlt) > 1e-6) {
            val y = -sin(ha)
            val x = (sin(decRad) - sin(latRad) * sinAlt) / cos(latRad)
            atan2(y, x)
        } else {
            0.0
        }
        val azDeg = normalizeDegrees(azRad * RAD2DEG)

        return HorizontalCoordinates(
            altitudeDegrees = altDeg,
            azimuthDegrees = azDeg,
        )
    }

    fun calculateSunEquatorial(jd: Double): EquatorialCoordinates {
        val d = jd - 2451545.0
        val t = d / 36525.0

        val meanAnomaly = normalizeDegrees(357.52910 + 35999.05029 * t - 0.0001537 * t * t) * DEG2RAD
        val meanLongitude = normalizeDegrees(280.46646 + 36000.76983 * t)

        val c = (1.914602 - 0.004817 * t) * sin(meanAnomaly) + (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly)
        val eclipticLongitude = normalizeDegrees(meanLongitude + c) * DEG2RAD
        val obliquity = (23.439291 - 0.0130042 * t) * DEG2RAD

        val x = cos(eclipticLongitude)
        val y = cos(obliquity) * sin(eclipticLongitude)
        val z = sin(obliquity) * sin(eclipticLongitude)

        val ra = normalizeDegrees(atan2(y, x) * RAD2DEG)
        val dec = asin(z.coerceIn(-1.0, 1.0)) * RAD2DEG
        val r = 1.00014 - 0.01671 * cos(meanAnomaly) - 0.00014 * cos(2 * meanAnomaly)

        return EquatorialCoordinates(ra, dec, r)
    }

    fun calculateMoonEquatorial(jd: Double): EquatorialCoordinates {
        val d = jd - 2451545.0

        val l0 = normalizeDegrees(218.316 + 13.176396 * d) * DEG2RAD
        val m = normalizeDegrees(134.963 + 13.064993 * d) * DEG2RAD
        val f = normalizeDegrees(93.272 + 13.229350 * d) * DEG2RAD
        val dElong = normalizeDegrees(297.850 + 12.190749 * d) * DEG2RAD

        val lon = l0 + (6.289 * sin(m) + 1.274 * sin(2 * dElong - m) + 0.658 * sin(2 * dElong) - 0.214 * sin(2 * m)) * DEG2RAD
        val lat = (5.128 * sin(f) + 0.280 * sin(m + f) + 0.277 * sin(m - f) + 0.173 * sin(2 * dElong - f)) * DEG2RAD
        val obliquity = 23.439291 * DEG2RAD

        val x = cos(lat) * cos(lon)
        val y = cos(obliquity) * cos(lat) * sin(lon) - sin(obliquity) * sin(lat)
        val z = sin(obliquity) * cos(lat) * sin(lon) + cos(obliquity) * sin(lat)

        val ra = normalizeDegrees(atan2(y, x) * RAD2DEG)
        val dec = asin(z.coerceIn(-1.0, 1.0)) * RAD2DEG

        return EquatorialCoordinates(ra, dec, 0.00257)
    }

    fun calculateMoonPhase(jd: Double): MoonPhaseInfo {
        val d = jd - 2451545.0
        val dElong = normalizeDegrees(297.850 + 12.190749 * d)
        val phaseFraction = dElong / 360.0
        val moonAgeDays = phaseFraction * 29.530588853

        val illumination = ((1.0 - cos(dElong * DEG2RAD)) / 2.0 * 100.0).roundToInt().coerceIn(0, 100)

        val phase = when {
            dElong < 22.5 || dElong >= 337.5 -> MoonPhase.NEW_MOON
            dElong < 67.5 -> MoonPhase.WAXING_CRESCENT
            dElong < 112.5 -> MoonPhase.FIRST_QUARTER
            dElong < 157.5 -> MoonPhase.WAXING_GIBBOUS
            dElong < 202.5 -> MoonPhase.FULL_MOON
            dElong < 247.5 -> MoonPhase.WANING_GIBBOUS
            dElong < 292.5 -> MoonPhase.LAST_QUARTER
            else -> MoonPhase.WANING_CRESCENT
        }

        return MoonPhaseInfo(
            phase = phase,
            phaseFraction = phaseFraction,
            illuminationPercent = illumination,
            moonAgeDays = moonAgeDays,
        )
    }

    private data class OrbitalElements(
        val a: Double, // Semi-major axis (AU)
        val e0: Double, val eRate: Double,
        val i0: Double, val iRate: Double,
        val l0: Double, val lRate: Double,
        val w0: Double, val wRate: Double, // Longitude of perihelion
        val node0: Double, val nodeRate: Double, // Longitude of ascending node
    )

    private val PLANET_ELEMENTS = mapOf(
        CelestialBodyType.MERCURY to OrbitalElements(0.387098, 0.205630, 0.000025, 7.0049, -0.0059, 252.2509, 149472.6741, 77.4561, 0.1605, 48.3308, -0.1253),
        CelestialBodyType.VENUS to OrbitalElements(0.723332, 0.006773, -0.000049, 3.3947, -0.0008, 181.9798, 58517.8157, 131.5637, 0.0048, 76.6799, -0.2777),
        CelestialBodyType.MARS to OrbitalElements(1.523679, 0.093405, 0.000092, 1.8497, -0.0006, 355.4330, 19140.2993, 336.0602, 0.4444, 49.5581, -0.2950),
        CelestialBodyType.JUPITER to OrbitalElements(5.202603, 0.048498, 0.000163, 1.3033, -0.0055, 34.3515, 3034.9057, 14.3312, 0.2156, 100.4644, 0.1767),
        CelestialBodyType.SATURN to OrbitalElements(9.554909, 0.055546, -0.000346, 2.4889, -0.0037, 50.0774, 1222.1138, 93.0572, 0.5665, 113.6655, -0.2567),
        CelestialBodyType.URANUS to OrbitalElements(19.218446, 0.046381, -0.000027, 0.7732, 0.0008, 314.0550, 428.4669, 173.0053, 0.0893, 74.0060, 0.0773),
        CelestialBodyType.NEPTUNE to OrbitalElements(30.110387, 0.009456, 0.000006, 1.7700, -0.0003, 304.3487, 218.4862, 48.1203, 0.0292, 131.7841, -0.0061),
    )

    fun calculatePlanetEquatorial(body: CelestialBodyType, jd: Double): EquatorialCoordinates {
        val elements = PLANET_ELEMENTS[body] ?: return EquatorialCoordinates(0.0, 0.0)
        val d = jd - 2451545.0
        val t = d / 36525.0

        val a = elements.a
        val e = elements.e0 + elements.eRate * t
        val inc = (elements.i0 + elements.iRate * t) * DEG2RAD
        val l = normalizeDegrees(elements.l0 + elements.lRate * t)
        val w = normalizeDegrees(elements.w0 + elements.wRate * t)
        val node = normalizeDegrees(elements.node0 + elements.nodeRate * t) * DEG2RAD

        val m = normalizeDegrees(l - w) * DEG2RAD
        val omega = normalizeDegrees(w - (node * RAD2DEG)) * DEG2RAD

        // Solve Kepler's equation E - e*sin(E) = M
        var eccE = m
        for (step in 0..10) {
            val delta = (eccE - e * sin(eccE) - m) / (1.0 - e * cos(eccE))
            eccE -= delta
            if (abs(delta) < 1e-7) break
        }

        val xPrime = a * (cos(eccE) - e)
        val yPrime = a * sqrt(1.0 - e * e) * sin(eccE)

        // Heliocentric ecliptic coordinates
        val xh = (cos(omega) * cos(node) - sin(omega) * sin(node) * cos(inc)) * xPrime +
            (-sin(omega) * cos(node) - cos(omega) * sin(node) * cos(inc)) * yPrime
        val yh = (cos(omega) * sin(node) + sin(omega) * cos(node) * cos(inc)) * xPrime +
            (-sin(omega) * sin(node) + cos(omega) * cos(node) * cos(inc)) * yPrime
        val zh = (sin(omega) * sin(inc)) * xPrime + (cos(omega) * sin(inc)) * yPrime

        // Heliocentric coordinates of Earth (Sun position inverted)
        val sunEq = calculateSunEquatorial(jd)
        val sunR = sunEq.distanceAu
        val sunLon = normalizeDegrees(sunEq.raDegrees) * DEG2RAD // approximation for ecliptic
        val xs = sunR * cos(sunLon)
        val ys = sunR * sin(sunLon)
        val zs = 0.0

        // Geocentric coordinates
        val xg = xh + xs
        val yg = yh + ys
        val zg = zh + zs

        val obliquity = 23.439291 * DEG2RAD
        val xEq = xg
        val yEq = cos(obliquity) * yg - sin(obliquity) * zg
        val zEq = sin(obliquity) * yg + cos(obliquity) * zg

        val ra = normalizeDegrees(atan2(yEq, xEq) * RAD2DEG)
        val dec = asin((zEq / sqrt(xEq * xEq + yEq * yEq + zEq * zEq)).coerceIn(-1.0, 1.0)) * RAD2DEG
        val dist = sqrt(xg * xg + yg * yg + zg * zg)

        return EquatorialCoordinates(ra, dec, dist)
    }

    fun approximateMagnitude(body: CelestialBodyType, distanceAu: Double): Double = when (body) {
        CelestialBodyType.MERCURY -> -0.4
        CelestialBodyType.VENUS -> -4.2
        CelestialBodyType.MARS -> 0.5
        CelestialBodyType.JUPITER -> -2.3
        CelestialBodyType.SATURN -> 0.7
        CelestialBodyType.URANUS -> 5.7
        CelestialBodyType.NEPTUNE -> 7.8
        CelestialBodyType.MOON -> -12.0
        CelestialBodyType.SUN -> -26.7
    }

    fun generateNightTimeline(
        referenceDateMillis: Long,
        latitude: Double,
        longitude: Double,
    ): NightTimeline {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val noonToday = cal.timeInMillis
        val noonTomorrow = noonToday + 86400000L

        // Sample Sun altitudes to find Sunset and Sunrise
        var sunsetMillis = noonToday + (6 * 3600000L) // fallback 18:00
        var duskAstronomicalMillis = sunsetMillis + (90 * 60000L) // fallback 19:30
        var dawnAstronomicalMillis = noonTomorrow - (90 * 60000L) // fallback 04:30
        var sunriseMillis = noonTomorrow - (6 * 3600000L) // fallback 06:00

        val stepMillis = 5 * 60000L // 5 minute steps
        var prevSunAlt = 10.0
        var foundSunset = false
        var foundDusk = false
        var foundDawn = false
        var foundSunrise = false

        var t = noonToday
        while (t <= noonTomorrow) {
            val jd = millisToJulianDate(t)
            val sunEq = calculateSunEquatorial(jd)
            val sunHoriz = equatorialToHorizontal(sunEq, latitude, longitude, jd)
            val alt = sunHoriz.altitudeDegrees

            // Sunset: crossing -0.833° downward
            if (!foundSunset && prevSunAlt >= -0.833 && alt < -0.833 && t < noonToday + (12 * 3600000L)) {
                sunsetMillis = t
                foundSunset = true
            }
            // Astronomical dusk: crossing -18° downward
            if (!foundDusk && prevSunAlt >= -18.0 && alt < -18.0 && t < noonToday + (12 * 3600000L)) {
                duskAstronomicalMillis = t
                foundDusk = true
            }
            // Astronomical dawn: crossing -18° upward
            if (!foundDawn && prevSunAlt <= -18.0 && alt > -18.0 && t > noonToday + (12 * 3600000L)) {
                dawnAstronomicalMillis = t
                foundDawn = true
            }
            // Sunrise: crossing -0.833° upward
            if (!foundSunrise && prevSunAlt <= -0.833 && alt > -0.833 && t > noonToday + (12 * 3600000L)) {
                sunriseMillis = t
                foundSunrise = true
            }

            prevSunAlt = alt
            t += stepMillis
        }

        if (duskAstronomicalMillis < sunsetMillis) duskAstronomicalMillis = sunsetMillis + 3600000L
        if (dawnAstronomicalMillis > sunriseMillis) dawnAstronomicalMillis = sunriseMillis - 3600000L

        // Target night window: from sunset - 30 min to sunrise + 30 min
        val windowStart = sunsetMillis - (30 * 60000L)
        val windowEnd = sunriseMillis + (30 * 60000L)

        val targetBodies = listOf(
            CelestialBodyType.MOON,
            CelestialBodyType.VENUS,
            CelestialBodyType.JUPITER,
            CelestialBodyType.SATURN,
            CelestialBodyType.MARS,
            CelestialBodyType.MERCURY,
            CelestialBodyType.URANUS,
            CelestialBodyType.NEPTUNE,
        )

        val nowMillis = System.currentTimeMillis()
        val nowJd = millisToJulianDate(nowMillis)
        val moonPhaseInfo = calculateMoonPhase(nowJd)

        val calDay = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = calDay.timeInMillis
        val dayEnd = dayStart + (28 * 3600000L) // 28 hours to cover full diurnal cycle

        val bodyVisibilities = targetBodies.map { body ->
            var fullDayMaxAlt = -90.0
            var fullDayTransit: Long? = null
            var fullDayRise: Long? = null
            var fullDaySet: Long? = null

            // 1. Calculate 24-hour diurnal Rise, Set, Transit across the full day
            var prevFullAlt = -90.0
            var tDay = dayStart
            val stepDay = 5 * 60000L // 5-min precision

            while (tDay <= dayEnd) {
                val jd = millisToJulianDate(tDay)
                val eq = when (body) {
                    CelestialBodyType.MOON -> calculateMoonEquatorial(jd)
                    else -> calculatePlanetEquatorial(body, jd)
                }
                val horiz = equatorialToHorizontal(eq, latitude, longitude, jd)
                val alt = horiz.altitudeDegrees

                if (alt > fullDayMaxAlt) {
                    fullDayMaxAlt = alt
                    fullDayTransit = tDay
                }

                if (prevFullAlt != -90.0) {
                    if (prevFullAlt <= 0.0 && alt > 0.0 && fullDayRise == null) {
                        fullDayRise = tDay
                    }
                    if (prevFullAlt >= 0.0 && alt < 0.0 && fullDaySet == null) {
                        fullDaySet = tDay
                    }
                }

                prevFullAlt = alt
                tDay += stepDay
            }

            // 2. Calculate Visible Intervals during Tonight's Night Window (for Gantt Chart)
            val intervals = mutableListOf<TimeInterval>()
            var currentIntervalStart: Long? = null
            var sampleTime = windowStart
            val sampleStep = 5 * 60000L

            while (sampleTime <= windowEnd) {
                val jd = millisToJulianDate(sampleTime)
                val eq = when (body) {
                    CelestialBodyType.MOON -> calculateMoonEquatorial(jd)
                    else -> calculatePlanetEquatorial(body, jd)
                }
                val horiz = equatorialToHorizontal(eq, latitude, longitude, jd)
                val alt = horiz.altitudeDegrees

                val isAboveHorizon = alt > 0.0
                if (isAboveHorizon) {
                    if (currentIntervalStart == null) {
                        currentIntervalStart = sampleTime
                    }
                } else {
                    if (currentIntervalStart != null) {
                        intervals.add(TimeInterval(currentIntervalStart, sampleTime))
                        currentIntervalStart = null
                    }
                }

                sampleTime += sampleStep
            }

            if (currentIntervalStart != null) {
                intervals.add(TimeInterval(currentIntervalStart, windowEnd))
            }

            // Current position
            val currentEq = when (body) {
                CelestialBodyType.MOON -> calculateMoonEquatorial(nowJd)
                else -> calculatePlanetEquatorial(body, nowJd)
            }
            val currentHoriz = equatorialToHorizontal(currentEq, latitude, longitude, nowJd)

            val nameZh = when (body) {
                CelestialBodyType.MOON -> "月球"
                CelestialBodyType.MERCURY -> "水星"
                CelestialBodyType.VENUS -> "金星"
                CelestialBodyType.MARS -> "火星"
                CelestialBodyType.JUPITER -> "木星"
                CelestialBodyType.SATURN -> "土星"
                CelestialBodyType.URANUS -> "天王星"
                CelestialBodyType.NEPTUNE -> "海王星"
                CelestialBodyType.SUN -> "太阳"
            }
            val nameEn = when (body) {
                CelestialBodyType.MOON -> "Moon"
                CelestialBodyType.MERCURY -> "Mercury"
                CelestialBodyType.VENUS -> "Venus"
                CelestialBodyType.MARS -> "Mars"
                CelestialBodyType.JUPITER -> "Jupiter"
                CelestialBodyType.SATURN -> "Saturn"
                CelestialBodyType.URANUS -> "Uranus"
                CelestialBodyType.NEPTUNE -> "Neptune"
                CelestialBodyType.SUN -> "Sun"
            }

            CelestialBodyVisibility(
                body = body,
                nameZh = nameZh,
                nameEn = nameEn,
                visibleIntervals = intervals,
                riseTimeMillis = fullDayRise,
                setTimeMillis = fullDaySet,
                transitTimeMillis = fullDayTransit,
                maxAltitudeDegrees = fullDayMaxAlt,
                currentAltitudeDegrees = currentHoriz.altitudeDegrees,
                currentAzimuthDegrees = currentHoriz.azimuthDegrees,
                magnitude = approximateMagnitude(body, currentEq.distanceAu),
                constellation = null,
            )
        }

        return NightTimeline(
            referenceDateMillis = referenceDateMillis,
            sunsetMillis = sunsetMillis,
            duskAstronomicalMillis = duskAstronomicalMillis,
            dawnAstronomicalMillis = dawnAstronomicalMillis,
            sunriseMillis = sunriseMillis,
            bodies = bodyVisibilities,
            moonPhase = moonPhaseInfo,
            observerLatitude = latitude,
            observerLongitude = longitude,
        )
    }
}
