package com.example.toolbox.location

enum class LocationReadStatus {
    SUCCESS,
    PERMISSION_REQUIRED,
    SERVICES_DISABLED,
    TIMEOUT,
    UNAVAILABLE,
    ERROR,
}

data class PositioningSystemInfo(
    val availableProviders: List<String>,
    val enabledProviders: List<String>,
    val supportedConstellations: List<String>,
    val isGnssHardwareAvailable: Boolean,
)

data class TerrainElevationInfo(
    val elevationMeters: Double,
    val source: String,
)

data class LocationInfo(
    val status: LocationReadStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val verticalAccuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val provider: String? = null,
    val timestampMillis: Long? = null,
    val positioningSystems: PositioningSystemInfo? = null,
)

enum class GnssConstellation {
    GPS,
    BEIDOU,
    GLONASS,
    GALILEO,
    QZSS,
    NAVIC,
    SBAS,
    UNKNOWN;

    fun displayName(chinese: Boolean): String = when (this) {
        GPS -> if (chinese) "GPS (美国)" else "GPS"
        BEIDOU -> if (chinese) "北斗 (中国)" else "BeiDou"
        GLONASS -> if (chinese) "GLONASS (俄罗斯)" else "GLONASS"
        GALILEO -> if (chinese) "伽利略 (欧洲)" else "Galileo"
        QZSS -> if (chinese) "准天顶 (日本)" else "QZSS"
        NAVIC -> if (chinese) "NavIC (印度)" else "NavIC"
        SBAS -> if (chinese) "SBAS (增强)" else "SBAS"
        UNKNOWN -> if (chinese) "未知" else "Unknown"
    }

    fun shortLabel(): String = when (this) {
        GPS -> "GPS"
        BEIDOU -> "BDS"
        GLONASS -> "GLO"
        GALILEO -> "GAL"
        QZSS -> "QZS"
        NAVIC -> "NAV"
        SBAS -> "SBA"
        UNKNOWN -> "?"
    }

    fun colorRgb(): Long = when (this) {
        BEIDOU -> 0xFFE05252
        GPS -> 0xFF4A90E2
        GLONASS -> 0xFF50B83C
        GALILEO -> 0xFFF5A623
        QZSS -> 0xFF9013FE
        NAVIC -> 0xFFFF6F00
        SBAS -> 0xFF7E8B9B
        UNKNOWN -> 0xFF888888
    }
}

data class SatelliteInfo(
    val svid: Int,
    val constellation: GnssConstellation,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Double? = null,
    val hasAlmanacData: Boolean = false,
    val hasEphemerisData: Boolean = false,
)

data class GnssSkyViewStatus(
    val totalCount: Int,
    val usedInFixCount: Int,
    val satellites: List<SatelliteInfo>,
    val constellationCounts: Map<GnssConstellation, Int>,
    val timestampMillis: Long = System.currentTimeMillis(),
)
