package com.example.toolbox.monitor

enum class MonitorMetric {
    CPU,
    GPU,
    MEMORY,
    BATTERY,
    FPS,
}

enum class MonitorCpuDisplayMode {
    TOPOLOGY_MATRIX,
    CORE_FREQUENCIES,
    WEIGHTED_USAGE,
}

data class CpuCoreFrequency(
    val index: Int,
    val currentFrequencyMhz: Int?,
    val minimumFrequencyMhz: Int?,
    val maximumFrequencyMhz: Int?,
    val usagePercent: Float? = null,
)

data class MonitorSample(
    val timestampMillis: Long = System.currentTimeMillis(),
    val cpuUsagePercent: Float?,
    val cpuFrequencyMhz: Int?,
    val cpuMinFrequencyMhz: Int?,
    val cpuMaxFrequencyMhz: Int?,
    val gpuUsagePercent: Float?,
    val gpuFrequencyMhz: Int?,
    val gpuMinFrequencyMhz: Int?,
    val gpuMaxFrequencyMhz: Int?,
    val memoryUsagePercent: Float?,
    val batteryLevelPercent: Float?,
    val batteryPowerMw: Float?,
    val batteryTemperatureCelsius: Float? = null,
    val thermalStatus: Int? = null,
    val thermalHeadroom: Float? = null,
    val displayRefreshRateHz: Float? = null,
    val supportedRefreshRatesHz: List<Int> = emptyList(),
    val cpuCores: List<CpuCoreFrequency> = emptyList(),
)

fun MonitorSample.chartValue(metric: MonitorMetric): Float? = when (metric) {
    MonitorMetric.CPU -> cpuUsagePercent ?: frequencyLoadPercent(MonitorMetric.CPU)
    MonitorMetric.GPU -> gpuUsagePercent ?: frequencyLoadPercent(MonitorMetric.GPU)

    MonitorMetric.MEMORY -> memoryUsagePercent
    MonitorMetric.BATTERY -> batteryLevelPercent
    MonitorMetric.FPS -> displayRefreshRateHz?.let { (it / FPS_CHART_MAX_HZ * 100f).coerceIn(0f, 100f) }
}

fun MonitorSample.frequencyLoadPercent(metric: MonitorMetric): Float? = when (metric) {
    MonitorMetric.CPU -> weightedCpuFrequencyPercent()

    MonitorMetric.GPU -> frequencyRatioPercent(
        current = gpuFrequencyMhz,
        minimum = gpuMinFrequencyMhz,
        maximum = gpuMaxFrequencyMhz,
    )

    else -> null
}

fun MonitorSample.weightedCpuPercent(): Float? =
    weightedCpuUsagePercent() ?: weightedCpuFrequencyPercent()

fun MonitorSample.weightedCpuUsagePercent(): Float? =
    weightedCorePercent { it.usagePercent }

fun MonitorSample.weightedCpuFrequencyPercent(): Float? =
    weightedCorePercent { core ->
        frequencyRatioPercent(
            current = core.currentFrequencyMhz,
            minimum = core.minimumFrequencyMhz,
            maximum = core.maximumFrequencyMhz,
        )
    }

private fun MonitorSample.weightedCorePercent(
    value: (CpuCoreFrequency) -> Float?,
): Float? {
    if (cpuCores.isEmpty()) return null
    val weightedValues = cpuCores.map { core ->
        val percentage = value(core) ?: return null
        val capacity = (core.maximumFrequencyMhz ?: core.currentFrequencyMhz)
            ?.takeIf { it > 0 }
            ?.toFloat()
            ?: return null
        percentage to capacity
    }
    val totalCapacity = weightedValues.sumOf { it.second.toDouble() }.toFloat()
    if (totalCapacity <= 0f) return null
    return weightedValues.sumOf { (percentage, capacity) ->
        (percentage * capacity).toDouble()
    }.toFloat() / totalCapacity
}

private fun frequencyRatioPercent(current: Int?, minimum: Int?, maximum: Int?): Float? {
    val currentValue = current ?: return null
    val maximumValue = maximum?.takeIf { it > 0 } ?: return null
    val minimumValue = minimum?.coerceIn(0, maximumValue) ?: 0
    return if (maximumValue > minimumValue) {
        ((currentValue - minimumValue) * 100f / (maximumValue - minimumValue)).coerceIn(0f, 100f)
    } else {
        (currentValue * 100f / maximumValue).coerceIn(0f, 100f)
    }
}

private const val FPS_CHART_MAX_HZ = 240f
