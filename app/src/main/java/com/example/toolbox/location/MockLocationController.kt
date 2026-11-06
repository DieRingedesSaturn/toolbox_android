package com.example.toolbox.location

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

/** All calls are serialized by the service on IO. No real coordinates are read or persisted. */
class MockLocationController(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(LocationManager::class.java)
    private val preferences = this.context.getSharedPreferences("mock_location", Context.MODE_PRIVATE)
    private val owned = preferences.getStringSet("providers", emptySet()).orEmpty()
        .filterTo(mutableSetOf()) { it in PROVIDERS }

    fun hasAccess(): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java)
            ?.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun hasPendingCleanup(): Boolean = owned.isNotEmpty()

    fun servicesEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        (Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF)
    }

    fun start(config: MockLocationConfig) {
        require(config.isValid())
        check(hasAccess()) { "Mock location access required" }
        check(stop()) { "Previous mock providers need cleanup" }
        try {
            val names = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) +
                listOf("fused").filter { it in manager.allProviders }
            names.forEach { provider ->
                // Journal before changing Android state so a killed process can offer cleanup.
                owned += provider
                check(saveOwned()) { "Cannot record mock provider ownership" }
                addProvider(provider)
                manager.setTestProviderEnabled(provider, true)
            }
            publish(config)
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    fun publish(config: MockLocationConfig) {
        require(config.isValid())
        if (!hasAccess()) throw SecurityException("Mock location access revoked")
        owned.forEach { provider ->
            manager.setTestProviderLocation(provider, Location(provider).apply {
                latitude = config.latitude
                longitude = config.longitude
                accuracy = config.accuracyMeters
                time = System.currentTimeMillis().coerceAtLeast(1L)
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L)
            })
        }
    }

    fun stop(): Boolean {
        if (owned.isNotEmpty() && !hasAccess()) return false
        owned.toList().forEach { provider ->
            val removed = try {
                manager.removeTestProvider(provider)
                true
            } catch (_: IllegalArgumentException) {
                // Older Android versions throw if an already-removed test provider is absent.
                true
            } catch (_: Exception) {
                false
            }
            if (removed) owned -= provider
        }
        return saveOwned() && owned.isEmpty()
    }

    private fun addProvider(provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.addTestProvider(provider, ProviderProperties.Builder()
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .build())
        } else {
            @Suppress("DEPRECATION")
            manager.addTestProvider(provider, false, false, false, false, false, false, false,
                android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
        }
    }

    @SuppressLint("UseKtx") // The journal must reach disk before replacing a provider.
    private fun saveOwned(): Boolean = preferences.edit().putStringSet("providers", owned.toSet()).commit()

    companion object {
        private val PROVIDERS = setOf("gps", "network", "fused")
    }
}
