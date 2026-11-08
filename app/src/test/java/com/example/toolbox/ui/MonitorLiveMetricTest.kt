package com.example.toolbox.ui

import com.example.toolbox.monitor.CpuCoreFrequency
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorSample
import com.example.toolbox.monitor.chartValue
import com.example.toolbox.monitor.weightedCpuUsagePercent
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorLiveMetricTest {
    private val strings = ToolboxStrings(AppLanguage.ENGLISH)
    private val sample = MonitorSample(
        cpuUsagePercent = 50f,
        cpuFrequencyMhz = 1_000,
        cpuMinFrequencyMhz = 0,
        cpuMaxFrequencyMhz = 3_000,
        gpuUsagePercent = null,
        gpuFrequencyMhz = null,
        gpuMinFrequencyMhz = null,
        gpuMaxFrequencyMhz = null,
        memoryUsagePercent = null,
        batteryLevelPercent = null,
        batteryPowerMw = null,
        cpuCores = listOf(
            CpuCoreFrequency(0, 1_000, 0, 1_000, 0f),
            CpuCoreFrequency(1, 3_000, 0, 3_000, 100f),
        ),
    )

    @Test
    fun cpuTextUsesTheSameAggregateValueAsTheChart() {
        assertEquals(75f, sample.weightedCpuUsagePercent())
        assertEquals(50f, sample.chartValue(MonitorMetric.CPU))
        assertEquals("50%", formatLiveMetric(sample, MonitorMetric.CPU, strings))
    }

    @Test
    fun frequencyFallbackMatchesTheChartAndIsMarkedAsEstimated() {
        val fallback = sample.copy(cpuUsagePercent = null)
        assertEquals(100f, fallback.chartValue(MonitorMetric.CPU))
        assertEquals("≈100%", formatLiveMetric(fallback, MonitorMetric.CPU, strings))
    }

    @Test
    fun missingCpuDataIsUnavailableRatherThanZero() {
        val missing = sample.copy(cpuUsagePercent = null, cpuCores = emptyList())
        assertEquals(strings.notAvailable, formatLiveMetric(missing, MonitorMetric.CPU, strings))
    }

    @Test
    fun zeroMeasuredUsageRemainsMeasuredEvenWhenFrequencyIsHigh() {
        assertEquals("0%", formatLiveMetric(sample.copy(cpuUsagePercent = 0f), MonitorMetric.CPU, strings))
    }
}
