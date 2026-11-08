package com.example.toolbox.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PlaceNameReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun lookup(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!latitude.isFinite() || !longitude.isFinite()) return@withContext null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@withContext null
        if (!Geocoder.isPresent()) return@withContext null
        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            try {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val addresses = readAddresses(geocoder, latitude, longitude)
                addresses.firstOrNull()?.let(::formatAddress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun readAddresses(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    private fun complete(addresses: List<Address>) {
                        if (completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(addresses)
                        }
                    }

                    override fun onGeocode(addresses: MutableList<Address>) = complete(addresses)

                    override fun onError(errorMessage: String?) = complete(emptyList())
                })
            }
        } else {
            @Suppress("DEPRECATION")
            cancellablePlaceLookup(legacyGeocoderExecutor) {
                geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
            }.orEmpty()
        }
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 5_000L
    }
}

// A vendor backend may ignore interruption. Keep at most one such call, with no queued lookups.
// An idle worker expires; this does not keep a service or polling task alive.
private val legacyGeocoderExecutor = ThreadPoolExecutor(
    1, 1, 30L, TimeUnit.SECONDS, SynchronousQueue(),
    { task -> Thread(task, "Toolbox-geocoder").apply { isDaemon = true } },
).apply { allowCoreThreadTimeOut(true) }

internal suspend fun <T> cancellablePlaceLookup(executor: Executor, query: () -> T): T? =
    suspendCancellableCoroutine { continuation ->
        val task = FutureTask<Unit> {
            val result = runCatching(query).getOrNull()
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { task.cancel(true) }
        if (continuation.isActive) {
            try {
                executor.execute(task)
            } catch (_: RejectedExecutionException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

fun formatAddress(address: Address): String? = formatPlaceName(
    featureName = address.featureName,
    thoroughfare = address.thoroughfare,
    subThoroughfare = address.subThoroughfare,
    subLocality = address.subLocality,
    locality = address.locality,
    subAdminArea = address.subAdminArea,
    adminArea = address.adminArea,
    countryName = address.countryName,
)

fun formatPlaceName(
    featureName: String?,
    thoroughfare: String?,
    subThoroughfare: String?,
    subLocality: String?,
    locality: String?,
    subAdminArea: String?,
    adminArea: String?,
    countryName: String?,
): String? {
    fun clean(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    val street = listOfNotNull(clean(thoroughfare), clean(subThoroughfare))
        .joinToString(" ")
        .ifBlank { null }
    val feature = clean(featureName)?.takeUnless { name ->
        name == street ||
            name == clean(thoroughfare) ||
            street?.startsWith(name) == true ||
            name == locality ||
            name == adminArea ||
            PLUS_CODE.matches(name)
    }
    return listOfNotNull(
        feature,
        street,
        clean(subLocality),
        clean(locality),
        clean(subAdminArea),
        clean(adminArea),
        clean(countryName),
    ).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}

private val PLUS_CODE = Regex("^[A-Z0-9]{4,}\\+[A-Z0-9]{2,}$")
