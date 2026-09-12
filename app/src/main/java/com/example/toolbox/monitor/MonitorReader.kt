package com.example.toolbox.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MonitorReader(context: Context) {
    private val appContext = context.applicationContext
    private var previousCpuTicks: CpuTickSnapshot? = null
    private var lastHeadroomReadAt = Long.MIN_VALUE
    private var cachedThermalHeadroom: Float? = null
    private val cpuPolicySources by lazy { discoverCpuPolicySources() }
    private val gpuFrequencySources by lazy { discoverGpuFrequencySources() }

    fun read(): MonitorSample {
        val cpu = readCpu()
        val gpu = readGpu()
        val memory = readMemory()
        val battery = readBattery()
        val thermal = readThermal()
        val display = readDisplay()
        return MonitorSample(
            cpuUsagePercent = cpu.usagePercent,
            cpuFrequencyMhz = cpu.currentFrequencyMhz,
            cpuMinFrequencyMhz = cpu.minimumFrequencyMhz,
            cpuMaxFrequencyMhz = cpu.maximumFrequencyMhz,
            gpuUsagePercent = gpu.usagePercent,
            gpuFrequencyMhz = gpu.currentFrequencyMhz,
            gpuMinFrequencyMhz = gpu.minimumFrequencyMhz,
            gpuMaxFrequencyMhz = gpu.maximumFrequencyMhz,
            memoryUsagePercent = memory,
            batteryLevelPercent = battery.levelPercent,
            batteryPowerMw = battery.powerMw,
            batteryTemperatureCelsius = battery.temperatureCelsius,
            thermalStatus = thermal.status,
            thermalHeadroom = thermal.headroom,
            displayRefreshRateHz = display.refreshRateHz,
            supportedRefreshRatesHz = display.supportedRefreshRatesHz,
            cpuCores = cpu.cores,
        )
    }

    private fun readCpu(): CpuSample {
        val frequencies = readCpuFrequency()
        val ticks = readCpuTicks()
        val previous = previousCpuTicks
        previousCpuTicks = ticks
        val currentCores = frequencies.map { core ->
            val tickUsage = ticks?.perCore?.get(core.index)?.let { current ->
                previous?.perCore?.get(core.index)?.let { old -> usageBetween(current, old) }
            }
            val usagePercent = tickUsage ?: frequencyRatioPercent(
                current = core.currentFrequencyMhz,
                minimum = core.minimumFrequencyMhz,
                maximum = core.maximumFrequencyMhz,
            )
            core.copy(usagePercent = usagePercent)
        }
        val currentAggregate = ticks?.aggregate ?: ticks?.perCore?.values?.let(::sumCpuTicks)
        val previousAggregate = previous?.aggregate ?: previous?.perCore?.values?.let(::sumCpuTicks)
        return CpuSample(
            usagePercent = if (currentAggregate != null && previousAggregate != null) {
                usageBetween(currentAggregate, previousAggregate)
            } else {
                null
            },
            currentFrequencyMhz = currentCores.mapNotNull { it.currentFrequencyMhz }.averageInt(),
            minimumFrequencyMhz = currentCores.mapNotNull { it.minimumFrequencyMhz }.minOrNull(),
            maximumFrequencyMhz = currentCores.mapNotNull { it.maximumFrequencyMhz }.maxOrNull(),
            cores = currentCores,
        )
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

    private fun readCpuFrequency(): List<CpuCoreFrequency> = discoverCpuIndexes().map { index ->
        CpuCoreFrequency(
            index = index,
            currentFrequencyMhz = readCpuValue(index, listOf("scaling_cur_freq", "cpuinfo_cur_freq")),
            minimumFrequencyMhz = readCpuValue(index, listOf("cpuinfo_min_freq", "scaling_min_freq")),
            maximumFrequencyMhz = readCpuValue(index, listOf("cpuinfo_max_freq", "scaling_max_freq")),
        )
    }

    private fun discoverCpuIndexes(): List<Int> {
        val cpuRoot = File("/sys/devices/system/cpu")
        val discovered = cpuRoot.listFiles().orEmpty()
            .mapNotNull { directory -> Regex("cpu(\\d+)").matchEntire(directory.name)?.groupValues?.get(1)?.toIntOrNull() }
            .filter { index ->
                readLong(File(cpuRoot, "cpu$index/online"))?.let { it != 0L } ?: true
            }
            .distinct()
            .sorted()
        return discovered.ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).toList() }
    }

    private fun readCpuValue(index: Int, names: List<String>): Int? = names.asSequence()
        .flatMap { name ->
            val policyDirectory = cpuPolicySources.firstOrNull { index in it.coreIndexes }?.directory
            sequenceOf(File("/sys/devices/system/cpu/cpu$index/cpufreq/$name")) +
                listOfNotNull(
                    policyDirectory?.let { File(it, name) },
                    File("/sys/devices/system/cpu/cpufreq/policy$index/$name"),
                )
        }
        .mapNotNull(::readLong)
        .map(::frequencyToCpuMhz)
        .firstOrNull()

    private fun discoverCpuPolicySources(): List<CpuPolicySource> {
        val root = File("/sys/devices/system/cpu/cpufreq")
        return root.listFiles().orEmpty()
            .filter { directory -> Regex("policy\\d+").matches(directory.name) }
            .mapNotNull { directory ->
                val coreIndexes = readCpuList(File(directory, "related_cpus"))
                    .ifEmpty { readCpuList(File(directory, "affected_cpus")) }
                coreIndexes.takeUnless { it.isEmpty() }
                    ?.let { CpuPolicySource(directory, it.toSet()) }
            }
    }

    private fun readCpuList(file: File): List<Int> = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun readCpuTicks(): CpuTickSnapshot? = runCatching {
        val parsed = File("/proc/stat").useLines { lines ->
            lines.mapNotNull(::parseCpuTicks).toList()
        }
        CpuTickSnapshot(
            aggregate = parsed.firstOrNull { it.index == null }?.ticks,
            perCore = parsed.mapNotNull { entry ->
                entry.index?.let { index -> index to entry.ticks }
            }.toMap(),
        )
    }.getOrNull()

    private fun parseCpuTicks(line: String): ParsedCpuTicks? {
        val fields = line.trim().split(Regex("\\s+"))
        val label = fields.firstOrNull() ?: return null
        val index = when {
            label == "cpu" -> null
            label.startsWith("cpu") -> label.removePrefix("cpu").toIntOrNull() ?: return null
            else -> return null
        }
        val values = fields.drop(1).take(8).mapNotNull { it.toLongOrNull() }
        if (values.size < 5) return null
        return ParsedCpuTicks(
            index = index,
            ticks = CpuTicks(
                idle = values[3] + values[4],
                total = values.sum(),
            ),
        )
    }

    private fun usageBetween(current: CpuTicks, previous: CpuTicks): Float? {
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        return if (totalDelta > 0) {
            ((totalDelta - idleDelta) * 100f / totalDelta).coerceIn(0f, 100f)
        } else {
            null
        }
    }

    private fun sumCpuTicks(ticks: Collection<CpuTicks>): CpuTicks? {
        if (ticks.isEmpty()) return null
        return CpuTicks(
            idle = ticks.sumOf { it.idle },
            total = ticks.sumOf { it.total },
        )
    }

    private fun readGpu(): GpuSample {
        val headroomUsage = readGpuHeadroomUsage()

        val usage = headroomUsage ?: listOf(
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/proc/gpufreq/gpu_loading",
            "/proc/gpufreq/gpu_load",
            "/proc/mali/utilization",
        ).asSequence()
            .mapNotNull { readGpuUsage(File(it)) }
            .firstOrNull()

        val frequency = gpuFrequencySources.asSequence()
            .mapNotNull { source ->
                val current = readLong(source.current)
                val minimum = readLong(source.minimum)
                    ?: readAvailableMinimum(source.available)
                val maximum = readLong(source.maximum)
                    ?: readAvailableMaximum(source.available)
                current?.let { GpuFrequency(it, minimum, maximum) }
            }
            .firstOrNull()

        return GpuSample(
            usagePercent = usage,
            currentFrequencyMhz = frequency?.current?.let(::frequencyToMhz),
            minimumFrequencyMhz = frequency?.minimum?.let(::frequencyToMhz),
            maximumFrequencyMhz = frequency?.maximum?.let(::frequencyToMhz),
        )
    }

    private fun readGpuHeadroomUsage(): Float? {
        if (Build.VERSION.SDK_INT < 36) return null
        return runCatching {
            val healthManager = appContext.getSystemService(android.os.health.SystemHealthManager::class.java)
                ?: return null
            val method = healthManager.javaClass.methods.firstOrNull { it.name == "getGpuHeadroom" }
                ?: return null
            val paramTypes = method.parameterTypes
            val result = if (paramTypes.isEmpty()) {
                method.invoke(healthManager)
            } else if (paramTypes.size == 1) {
                val paramClass = paramTypes[0]
                val builderClass = runCatching { Class.forName("${paramClass.name}\$Builder") }.getOrNull()
                val paramInstance = if (builderClass != null) {
                    val builder = builderClass.getDeclaredConstructor().newInstance()
                    builderClass.getMethod("build").invoke(builder)
                } else {
                    paramClass.getDeclaredConstructor().newInstance()
                }
                method.invoke(healthManager, paramInstance)
            } else {
                null
            }
            val headroom = (result as? Number)?.toFloat()
            if (headroom != null && !headroom.isNaN() && !headroom.isInfinite()) {
                (100f - headroom).coerceIn(0f, 100f)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun readGpuUsage(file: File): Float? {
        val text = runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
            ?: return null
        Regex("(\\d+(?:\\.\\d+)?)\\s*%").find(text)?.let {
            return it.groupValues[1].toFloatOrNull()?.coerceIn(0f, 100f)
        }
        val values = text.split(Regex("[\\s,]+"))
            .mapNotNull { it.toFloatOrNull() }
        if (values.size >= 2 && values[1] > 0f) {
            return (values[0] * 100f / values[1]).coerceIn(0f, 100f)
        }
        return values.firstOrNull()?.takeIf { it in 0f..100f }
    }

    private fun readMemory(): Float? {
        val memoryInfo = ActivityManager.MemoryInfo()
        val manager = appContext.getSystemService(ActivityManager::class.java) ?: return null
        manager.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem <= 0L) return null
        return ((memoryInfo.totalMem - memoryInfo.availMem) * 100f / memoryInfo.totalMem)
            .coerceIn(0f, 100f)
    }

    private fun readBattery(): BatterySample {
        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureTenthsCelsius = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val currentUa = appContext.getSystemService(BatteryManager::class.java)
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeUnless { it == Long.MIN_VALUE }
        val powerMw = if (currentUa != null && voltageMv > 0) {
            abs(currentUa) * voltageMv / 1_000_000f
        } else {
            null
        }
        return BatterySample(
            levelPercent = if (level >= 0 && scale > 0) level * 100f / scale else null,
            powerMw = powerMw,
            temperatureCelsius = temperatureTenthsCelsius
                .takeUnless { it == -1 }
                ?.div(10f),
        )
    }

    private fun readThermal(): ThermalSample {
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager?.currentThermalStatus }.getOrNull()
        } else {
            null
        }
        val now = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (lastHeadroomReadAt == Long.MIN_VALUE || now - lastHeadroomReadAt >= THERMAL_HEADROOM_REFRESH_MILLIS)
        ) {
            lastHeadroomReadAt = now
            cachedThermalHeadroom = runCatching {
                powerManager?.getThermalHeadroom(0)
            }.getOrNull()?.takeUnless { it.isNaN() || it.isInfinite() }
        }
        return ThermalSample(status = status, headroom = cachedThermalHeadroom)
    }

    private fun readDisplay(): DisplaySample {
        val display = appContext.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return DisplaySample(null, emptyList())
        val refreshRate = display.refreshRate.takeIf { it > 0f && it.isFinite() }
        val supportedRefreshRates = display.supportedModes
            .map { it.refreshRate }
            .filter { it > 0f && it.isFinite() }
            .map { it.roundToInt() }
            .distinct()
            .sorted()
        return DisplaySample(refreshRate, supportedRefreshRates)
    }

    private fun discoverGpuFrequencySources(): List<GpuFrequencySource> {
        val sources = mutableListOf<GpuFrequencySource>()
        val roots = listOf(File("/sys/class/devfreq"), File("/sys/devices/platform"))
        roots.flatMap { root -> root.listFiles().orEmpty().asList() }
            .filter { directory ->
                directory.isDirectory && listOf("gpu", "mali", "3d", "kgsl")
                    .any { key -> directory.name.lowercase(Locale.US).contains(key) }
            }
            .forEach { directory ->
                sources += GpuFrequencySource(
                    current = File(directory, "cur_freq"),
                    minimum = File(directory, "min_freq"),
                    maximum = File(directory, "max_freq"),
                    available = File(directory, "available_frequencies"),
                )
            }
        sources += GpuFrequencySource(
            current = File("/sys/class/devfreq/gpu/cur_freq"),
            minimum = File("/sys/class/devfreq/gpu/min_freq"),
            maximum = File("/sys/class/devfreq/gpu/max_freq"),
            available = File("/sys/class/devfreq/gpu/available_frequencies"),
        )
        return sources.distinctBy { it.current.path }
    }

    private fun readAvailableMaximum(file: File): Long? = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull() }
            ?.maxOrNull()
    }.getOrNull()

    private fun readAvailableMinimum(file: File): Long? = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull() }
            ?.minOrNull()
    }.getOrNull()

    private fun readLong(file: File): Long? = runCatching {
        file.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
    }.getOrNull()

    private fun frequencyToMhz(value: Long): Int = when {
        value >= 10_000_000L -> (value / 1_000_000L).toInt()
        value >= 10_000L -> (value / 1_000L).toInt()
        else -> value.toInt()
    }

    private fun frequencyToCpuMhz(value: Long): Int = when {
        value >= 10_000L -> (value / 1_000L).toInt()
        else -> value.toInt()
    }

    private fun List<Int>.averageInt(): Int? = takeUnless { it.isEmpty() }
        ?.average()
        ?.roundToInt()

    private data class CpuTicks(val idle: Long, val total: Long)
    private data class ParsedCpuTicks(val index: Int?, val ticks: CpuTicks)
    private data class CpuTickSnapshot(
        val aggregate: CpuTicks?,
        val perCore: Map<Int, CpuTicks>,
    )
    private data class CpuPolicySource(
        val directory: File,
        val coreIndexes: Set<Int>,
    )
    private data class CpuSample(
        val usagePercent: Float?,
        val currentFrequencyMhz: Int?,
        val minimumFrequencyMhz: Int?,
        val maximumFrequencyMhz: Int?,
        val cores: List<CpuCoreFrequency>,
    )
    private data class GpuSample(
        val usagePercent: Float?,
        val currentFrequencyMhz: Int?,
        val minimumFrequencyMhz: Int?,
        val maximumFrequencyMhz: Int?,
    )
    private data class GpuFrequency(
        val current: Long,
        val minimum: Long?,
        val maximum: Long?,
    )
    private data class BatterySample(
        val levelPercent: Float?,
        val powerMw: Float?,
        val temperatureCelsius: Float?,
    )
    private data class ThermalSample(val status: Int?, val headroom: Float?)
    private data class DisplaySample(
        val refreshRateHz: Float?,
        val supportedRefreshRatesHz: List<Int>,
    )
    private data class GpuFrequencySource(
        val current: File,
        val minimum: File,
        val maximum: File,
        val available: File,
    )

    private companion object {
        const val THERMAL_HEADROOM_REFRESH_MILLIS = 10_000L
    }
}
