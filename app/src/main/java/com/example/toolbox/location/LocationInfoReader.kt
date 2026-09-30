package com.example.toolbox.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.coroutines.resume

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.location.GnssStatus

class LocationInfoReader(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasFineLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun startObservingGnss(
        onStatus: (GnssSkyViewStatus) -> Unit,
    ): (() -> Unit)? {
        if (!hasFineLocationPermission()) return null
        val manager = locationManager ?: return null

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val parsed = parseGnssStatus(status)
                onStatus(parsed)
            }
        }

        val dummyListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {}
            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.registerGnssStatusCallback(mainExecutor, callback)
            } else {
                manager.registerGnssStatusCallback(callback, android.os.Handler(Looper.getMainLooper()))
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                @Suppress("DEPRECATION")
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    dummyListener,
                    Looper.getMainLooper(),
                )
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                @Suppress("DEPRECATION")
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    dummyListener,
                    Looper.getMainLooper(),
                )
            }
            {
                runCatching {
                    manager.unregisterGnssStatusCallback(callback)
                    manager.removeUpdates(dummyListener)
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun readPositioningSystems(): PositioningSystemInfo {
        val manager = locationManager
        val hasGpsFeature = appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LOCATION_GPS)
        val allProviders = runCatching { manager?.allProviders.orEmpty() }.getOrDefault(emptyList())
        val enabledProviders = allProviders.filter { provider ->
            runCatching { manager?.isProviderEnabled(provider) == true }.getOrDefault(false)
        }

        return PositioningSystemInfo(
            availableProviders = allProviders.map(::formatProviderName),
            enabledProviders = enabledProviders.map(::formatProviderName),
            isGnssHardwareAvailable = hasGpsFeature,
        )
    }

    suspend fun queryTerrainElevation(latitude: Double, longitude: Double): TerrainElevationInfo? = withContext(Dispatchers.IO) {
        val openMeteoUrl = "https://api.open-meteo.com/v1/elevation?latitude=$latitude&longitude=$longitude"
        fetchUrl(openMeteoUrl)?.let { body ->
            runCatching { parseOpenMeteoElevation(body) }.getOrNull()
        } ?: run {
            val openElevationUrl = "https://api.open-elevation.com/api/v1/lookup?locations=$latitude,$longitude"
            fetchUrl(openElevationUrl)?.let { body ->
                runCatching { parseOpenElevation(body) }.getOrNull()
            }
        }
    }

    private fun fetchUrl(urlString: String): String? {
        val connection = runCatching {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MILLIS.toInt()
                readTimeout = HTTP_TIMEOUT_MILLIS.toInt()
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "Toolbox-Android/0.1")
                setRequestProperty("Accept", "application/json")
            }
        }.getOrNull() ?: return null

        return try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun readCurrentLocation(): LocationInfo {
        if (!hasLocationPermission()) {
            return LocationInfo(
                status = LocationReadStatus.PERMISSION_REQUIRED,
                positioningSystems = readPositioningSystems(),
            )
        }
        val manager = locationManager ?: return LocationInfo(
            status = LocationReadStatus.UNAVAILABLE,
            positioningSystems = readPositioningSystems(),
        )
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (providers.isEmpty()) {
            return LocationInfo(
                status = LocationReadStatus.SERVICES_DISABLED,
                positioningSystems = readPositioningSystems(),
            )
        }

        val location = runCatching {
            withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                requestLocation(manager, providers)
            }
        }.getOrNull()

        return if (location != null) {
            location.toLocationInfo()
        } else {
            LocationInfo(
                status = LocationReadStatus.TIMEOUT,
                positioningSystems = readPositioningSystems(),
            )
        }
    }

    private suspend fun requestLocation(
        manager: LocationManager,
        providers: List<String>,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSignals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()
        var pendingProviders = providers.size
        var completed = false

        val cleanup = {
            listeners.forEach { listener -> runCatching { manager.removeUpdates(listener) } }
            cancellationSignals.forEach { signal -> runCatching { signal.cancel() } }
        }

        fun complete(location: Location?) {
            if (completed || !continuation.isActive) return
            completed = true
            cleanup()
            continuation.resume(location)
        }

        fun noLocation() {
            if (completed) return
            pendingProviders -= 1
            if (pendingProviders <= 0) complete(null)
        }

        continuation.invokeOnCancellation {
            completed = true
            cleanup()
        }

        providers.forEach { provider ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cancellationSignals += signal
                    @Suppress("NewApi")
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        mainExecutor,
                        Consumer<Location?> { location ->
                            if (location != null) complete(location) else noLocation()
                        },
                    )
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            complete(location)
                        }

                        @Deprecated("Deprecated in Android API")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    listeners += listener
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                noLocation()
            } catch (_: IllegalArgumentException) {
                noLocation()
            }
        }
    }

    private fun formatProviderName(provider: String): String = when (provider.lowercase()) {
        LocationManager.GPS_PROVIDER -> "GPS (卫星硬件)"
        LocationManager.NETWORK_PROVIDER -> "Network (网络/基站)"
        LocationManager.FUSED_PROVIDER -> "Fused (融合定位)"
        LocationManager.PASSIVE_PROVIDER -> "Passive (被动监听)"
        else -> provider
    }

    private fun Location.toLocationInfo(): LocationInfo = LocationInfo(
        status = LocationReadStatus.SUCCESS,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        verticalAccuracyMeters = if (hasVerticalAccuracy()) {
            verticalAccuracyMeters
        } else {
            null
        },
        altitudeMeters = if (hasAltitude()) altitude else null,
        provider = provider,
        timestampMillis = time.takeIf { it > 0L },
        positioningSystems = readPositioningSystems(),
    )

    companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
        const val HTTP_TIMEOUT_MILLIS = 5_000L

        fun parseOpenMeteoElevation(jsonString: String): TerrainElevationInfo? {
            val json = org.json.JSONObject(jsonString)
            val array = json.optJSONArray("elevation") ?: return null
            if (array.length() == 0 || array.isNull(0)) return null
            val elevation = array.getDouble(0)
            return TerrainElevationInfo(elevationMeters = elevation, source = "Open-Meteo DEM")
        }

        fun parseOpenElevation(jsonString: String): TerrainElevationInfo? {
            val json = org.json.JSONObject(jsonString)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val item = results.getJSONObject(0)
            val elevation = item.getDouble("elevation")
            return TerrainElevationInfo(elevationMeters = elevation, source = "Open-Elevation DEM")
        }

        fun parseGnssStatus(status: GnssStatus): GnssSkyViewStatus {
            val count = status.satelliteCount
            val list = ArrayList<SatelliteInfo>(count)
            var usedCount = 0
            val counts = mutableMapOf<GnssConstellation, Int>()

            for (i in 0 until count) {
                val constellation = mapConstellationType(status.getConstellationType(i))
                val used = status.usedInFix(i)
                if (used) usedCount++
                counts[constellation] = (counts[constellation] ?: 0) + 1

                val carrierFreq = if (status.hasCarrierFrequencyHz(i)) {
                    status.getCarrierFrequencyHz(i).toDouble()
                } else {
                    null
                }

                list.add(
                    SatelliteInfo(
                        svid = status.getSvid(i),
                        constellation = constellation,
                        cn0DbHz = status.getCn0DbHz(i),
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = used,
                        carrierFrequencyHz = carrierFreq,
                        hasAlmanacData = status.hasAlmanacData(i),
                        hasEphemerisData = status.hasEphemerisData(i),
                    ),
                )
            }

            list.sortWith(compareByDescending<SatelliteInfo> { it.usedInFix }.thenByDescending { it.cn0DbHz })

            return GnssSkyViewStatus(
                totalCount = count,
                usedInFixCount = usedCount,
                satellites = list,
                constellationCounts = counts,
            )
        }

        fun mapConstellationType(type: Int): GnssConstellation = when (type) {
            GnssStatus.CONSTELLATION_GPS -> GnssConstellation.GPS
            GnssStatus.CONSTELLATION_BEIDOU -> GnssConstellation.BEIDOU
            GnssStatus.CONSTELLATION_GLONASS -> GnssConstellation.GLONASS
            GnssStatus.CONSTELLATION_GALILEO -> GnssConstellation.GALILEO
            GnssStatus.CONSTELLATION_QZSS -> GnssConstellation.QZSS
            GnssStatus.CONSTELLATION_IRNSS -> GnssConstellation.NAVIC
            GnssStatus.CONSTELLATION_SBAS -> GnssConstellation.SBAS
            else -> GnssConstellation.UNKNOWN
        }
    }
}
