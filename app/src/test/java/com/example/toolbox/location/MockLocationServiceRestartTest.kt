package com.example.toolbox.location

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockLocationServiceRestartTest {
    @Test
    fun commandsStayNonStickyWhenNoSessionIsRunning() {
        val service = Robolectric.buildService(MockLocationService::class.java).create().get()
        try {
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
            assertEquals(
                Service.START_NOT_STICKY,
                service.onStartCommand(Intent(ACTION_START), 0, 2),
            )
            assertEquals(
                Service.START_NOT_STICKY,
                service.onStartCommand(Intent(ACTION_UPDATE), 0, 3),
            )
            assertNull(sessionField().get(service))
            assertEquals(
                Service.START_NOT_STICKY,
                service.onStartCommand(Intent(ACTION_STOP), 0, 4),
            )
        } finally {
            service.onDestroy()
        }
    }

    @Test
    fun updateWhileRunningDoesNotBecomeStickyOrRestartTheClock() {
        val service = Robolectric.buildService(MockLocationService::class.java).create().get()
        val job = Job()
        try {
            sessionField().set(service, job)
            configField().set(service, MockLocationConfig(30.0, 114.0, 50f, 30))
            val update = Intent(ACTION_UPDATE)
                .putExtra("latitude", 31.2)
                .putExtra("longitude", 121.5)
                .putExtra("accuracy", 25f)
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(update, 0, 1))
            val updated = configField().get(service) as MockLocationConfig
            assertEquals(31.2, updated.latitude, 0.0)
            assertEquals(121.5, updated.longitude, 0.0)
            assertEquals(25f, updated.accuracyMeters)
            assertEquals(30, updated.durationMinutes)
            assertEquals(31.2, MockLocationService.state.value.config?.latitude)
            assertEquals(
                Service.START_NOT_STICKY,
                service.onStartCommand(null, 0, 2),
            )
            assertEquals(job, sessionField().get(service))
        } finally {
            job.cancel()
            service.onDestroy()
        }
    }

    private fun sessionField() =
        MockLocationService::class.java.getDeclaredField("session").apply { isAccessible = true }

    private fun configField() =
        MockLocationService::class.java.getDeclaredField("sessionConfig").apply { isAccessible = true }

    private companion object {
        const val ACTION_START = "com.example.toolbox.location.START_MOCK"
        const val ACTION_UPDATE = "com.example.toolbox.location.UPDATE_MOCK"
        const val ACTION_STOP = "com.example.toolbox.location.STOP_MOCK"
    }
}
