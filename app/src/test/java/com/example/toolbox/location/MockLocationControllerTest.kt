package com.example.toolbox.location

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.location.Location
import android.location.provider.ProviderProperties as AndroidProviderProperties
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [RecordingMockLocationManager::class])
class MockLocationControllerTest {
    private lateinit var context: Context
    private lateinit var appOps: AppOpsManager
    private val config = MockLocationConfig(30.5, 114.2, 25_000f, 30)
    private val providers: RecordingMockLocationManager
        get() = Shadow.extract(context.getSystemService(LocationManager::class.java))

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        appOps = context.getSystemService(AppOpsManager::class.java)
        context.getSharedPreferences("mock_location", Context.MODE_PRIVATE).edit().clear().commit()
        setAccess(true)
    }

    private fun setAccess(allowed: Boolean) {
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName,
            if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED)
    }

    @Test
    fun deniedAccessNeverClaimsOwnershipOrChangesProviders() {
        setAccess(false)
        val controller = MockLocationController(context)
        assertFalse(controller.hasAccess())
        assertThrows(IllegalStateException::class.java) { controller.start(config) }
        assertFalse(controller.hasPendingCleanup())
    }

    @Test
    fun reportsTheSystemLocationSwitchWithoutReadingCoordinates() {
        val manager = context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setLocationEnabled(false)
        assertFalse(MockLocationController(context).servicesEnabled())
        shadowOf(manager).setLocationEnabled(true)
        assertTrue(MockLocationController(context).servicesEnabled())
    }

    @Test
    fun startJournalsProvidersAndStopIsIdempotent() {
        val controller = MockLocationController(context)
        controller.start(config)
        assertTrue(controller.hasPendingCleanup())
        assertTrue(MockLocationController(context).hasPendingCleanup())
        val journal = context.getSharedPreferences("mock_location", Context.MODE_PRIVATE).all
        assertEquals(setOf("providers"), journal.keys)
        assertTrue(controller.stop())
        assertTrue(controller.stop())
        assertFalse(MockLocationController(context).hasPendingCleanup())
    }

    @Test
    fun newControllerCanCleanUpAnInterruptedSession() {
        MockLocationController(context).start(config)
        val recovered = MockLocationController(context)
        assertTrue(recovered.hasPendingCleanup())
        assertTrue(recovered.stop())
        assertFalse(recovered.hasPendingCleanup())
    }

    @Test
    fun revocationRejectsNewCoordinatesAndRetainsCleanupJournal() {
        val controller = MockLocationController(context)
        controller.start(config)
        setAccess(false)
        assertThrows(SecurityException::class.java) { controller.publish(config) }
        assertFalse(controller.stop())
        assertTrue(MockLocationController(context).hasPendingCleanup())
        setAccess(true)
        assertTrue(controller.stop())
    }

    @Test
    fun invalidInputDoesNotReplaceAnActiveSession() {
        val controller = MockLocationController(context)
        controller.start(config)
        assertThrows(IllegalArgumentException::class.java) { controller.start(config.copy(latitude = Double.NaN)) }
        assertTrue(controller.hasPendingCleanup())
        assertTrue(controller.stop())
    }

    @Test
    fun stoppingDoesNotRemoveUnownedTestProviders() {
        val manager = context.getSystemService(LocationManager::class.java)
        manager.addTestProvider("another-test", android.location.provider.ProviderProperties.Builder().build())
        MockLocationController(context).apply { start(config); assertTrue(stop()) }
        assertEquals(setOf("another-test"), providers.installed)
    }

    @Test
    fun failedSecondProviderRollsBackOnlyOwnedProviders() {
        providers.installed += "another-test"
        providers.failOnAdd = LocationManager.NETWORK_PROVIDER
        val controller = MockLocationController(context)
        assertThrows(IllegalStateException::class.java) { controller.start(config) }
        assertEquals(listOf("gps", "network"), providers.added)
        assertEquals(setOf("another-test"), providers.installed)
        assertFalse(MockLocationController(context).hasPendingCleanup())
    }

    @Test
    fun publishedFixHasCompleteTimestampsAndNoInventedAltitude() {
        val controller = MockLocationController(context)
        controller.start(config)
        val fix = providers.locations.getValue(LocationManager.GPS_PROVIDER)
        assertEquals(config.latitude, fix.latitude, 0.0)
        assertEquals(config.longitude, fix.longitude, 0.0)
        assertEquals(config.accuracyMeters, fix.accuracy, 0f)
        assertTrue(fix.time > 0)
        assertTrue(fix.elapsedRealtimeNanos > 0)
        assertFalse(fix.hasAltitude())
        assertFalse(fix.hasSpeed())
        assertTrue(controller.stop())
    }
}

/** Robolectric's standard location shadow does not implement test-provider injection. */
@Implements(LocationManager::class)
class RecordingMockLocationManager : ShadowLocationManager() {
    val installed = mutableSetOf<String>()
    val added = mutableListOf<String>()
    val locations = mutableMapOf<String, Location>()
    var failOnAdd: String? = null

    @Implementation(minSdk = 31)
    protected fun addTestProvider(provider: String, properties: AndroidProviderProperties) {
        added += provider
        if (provider == failOnAdd) error("Simulated provider creation failure")
        installed += provider
    }

    @Implementation
    protected fun setTestProviderEnabled(provider: String, enabled: Boolean) {
        check(provider in installed)
    }

    @Implementation
    protected fun setTestProviderLocation(provider: String, location: Location) {
        check(provider in installed)
        locations[provider] = Location(location)
    }

    @Implementation
    protected fun removeTestProvider(provider: String) {
        installed -= provider
        locations.remove(provider)
    }
}
