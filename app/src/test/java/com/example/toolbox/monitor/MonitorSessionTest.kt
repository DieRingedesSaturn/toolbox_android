package com.example.toolbox.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MonitorSessionTest {
    private fun sample() = MonitorSample(
        cpuUsagePercent = 20f, cpuFrequencyMhz = null, cpuMinFrequencyMhz = null, cpuMaxFrequencyMhz = null,
        gpuUsagePercent = null, gpuFrequencyMhz = null, gpuMinFrequencyMhz = null, gpuMaxFrequencyMhz = null,
        memoryUsagePercent = null, batteryLevelPercent = null, batteryPowerMw = null,
    )

    @Test fun serviceConfirmationOwnsRunningStateAndLateSamplesAreIgnored() {
        MonitorSession.stopped()
        try {
            assertFalse(MonitorSession.state.value.overlayRunning)
            val previous = MonitorSession.state.value.sample
            MonitorSession.publishOverlay(sample())
            assertSame(previous, MonitorSession.state.value.sample)
            MonitorSession.started()
            val sample = sample()
            MonitorSession.publishOverlay(sample)
            assertTrue(MonitorSession.state.value.overlayRunning)
            assertSame(sample, MonitorSession.state.value.sample)
            MonitorSession.stopped()
            MonitorSession.publishOverlay(sample())
            assertFalse(MonitorSession.state.value.overlayRunning)
            assertSame(sample, MonitorSession.state.value.sample)
        } finally { MonitorSession.stopped() }
    }

    @Test fun startingOverlayCancelsForegroundSamplerAndLeavingCancelsObservation() = runBlocking {
        MonitorSession.stopped()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val observer = launch {
            MonitorSession.observeForeground {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        try {
            withTimeout(5000) { entered.await() }
            MonitorSession.started()
            withTimeout(5000) { cancelled.await() }
            assertTrue(MonitorSession.state.value.overlayRunning)
        } finally {
            observer.cancelAndJoin()
            MonitorSession.stopped()
        }
        assertTrue(observer.isCancelled)
    }

    @Test fun topologyFillHandlesMissingOfflineAndInvalidMaximaConsistently() {
        assertEquals(0f, CpuCoreFrequency(0, null, 100, 1000).fillFraction(), 0.0001f)
        assertEquals(0f, CpuCoreFrequency(0, 500, 100, 0).fillFraction(), 0.0001f)
        assertEquals(0.5f, CpuCoreFrequency(0, 600, 200, 1000).fillFraction(), 0.0001f)
        assertEquals(0.25f, CpuCoreFrequency(0, 600, 200, 1000, 25f).fillFraction(usage = true), 0.0001f)
    }
}
