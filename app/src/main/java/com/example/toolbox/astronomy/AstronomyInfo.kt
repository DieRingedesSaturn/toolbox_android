package com.example.toolbox.astronomy

enum class CelestialBodyType(
    val symbol: String,
    val colorRgb: Long,
) {
    MOON("🌙", 0xFFF5F7FA),
    MERCURY("☿", 0xFF90A4AE),
    VENUS("♀", 0xFFFFD166),
    MARS("♂", 0xFFFF6B6B),
    JUPITER("♃", 0xFFFFA726),
    SATURN("♄", 0xFFE6EE9C),
    URANUS("♅", 0xFF4DD0E1),
    NEPTUNE("♆", 0xFF42A5F5),
    SUN("☀️", 0xFFFFB300),
}

data class TimeInterval(
    val startMillis: Long,
    val endMillis: Long,
)

data class CelestialBodyVisibility(
    val body: CelestialBodyType,
    val nameZh: String,
    val nameEn: String,
    val visibleIntervals: List<TimeInterval>,
    val riseTimeMillis: Long?,
    val setTimeMillis: Long?,
    val transitTimeMillis: Long?,
    val maxAltitudeDegrees: Double,
    val currentAltitudeDegrees: Double,
    val currentAzimuthDegrees: Double,
    val magnitude: Double?,
    val constellation: String?,
) {
    val isVisibleTonight: Boolean get() = visibleIntervals.isNotEmpty()
}

enum class MoonPhase(
    val nameZh: String,
    val nameEn: String,
    val emoji: String,
) {
    NEW_MOON("新月 (朔)", "New Moon", "🌑"),
    WAXING_CRESCENT("蛾眉月", "Waxing Crescent", "🌒"),
    FIRST_QUARTER("上弦月", "First Quarter", "🌓"),
    WAXING_GIBBOUS("盈凸月", "Waxing Gibbous", "🌔"),
    FULL_MOON("满月 (望)", "Full Moon", "🌕"),
    WANING_GIBBOUS("亏凸月", "Waning Gibbous", "🌖"),
    LAST_QUARTER("下弦月", "Last Quarter", "🌗"),
    WANING_CRESCENT("残月", "Waning Crescent", "🌘"),
}

data class MoonPhaseInfo(
    val phase: MoonPhase,
    val phaseFraction: Double, // 0.0 to 1.0 (0=New, 0.25=First Quarter, 0.5=Full, 0.75=Last Quarter)
    val illuminationPercent: Int, // 0 to 100%
    val moonAgeDays: Double, // 0 to 29.53 days
)

data class NightTimeline(
    val referenceDateMillis: Long,
    val sunsetMillis: Long,
    val duskAstronomicalMillis: Long, // End of astronomical twilight (Dark night starts)
    val dawnAstronomicalMillis: Long, // Start of astronomical twilight (Dark night ends)
    val sunriseMillis: Long,
    val bodies: List<CelestialBodyVisibility>,
    val moonPhase: MoonPhaseInfo,
    val observerLatitude: Double,
    val observerLongitude: Double,
)
