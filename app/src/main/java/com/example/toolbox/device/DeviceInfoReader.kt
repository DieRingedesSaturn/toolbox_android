package com.example.toolbox.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.WindowManager
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

object DeviceInfoReader {
    fun read(context: Context): DeviceInfo {
        return DeviceInfo(
            identity = readIdentity(context),
            cpu = readCpu(),
            gpu = readGpu(context),
            memory = readMemory(context),
            storage = readStorage(),
            display = readDisplay(context),
            battery = readBattery(context),
        )
    }

    private fun readIdentity(context: Context): IdentityInfo {
        val deviceName = Settings.Global.getString(
            context.contentResolver,
            Settings.Global.DEVICE_NAME,
        ).takeUnless { it.isNullOrBlank() } ?: Build.DEVICE

        return IdentityInfo(
            manufacturer = Build.MANUFACTURER.displayValue(),
            model = Build.MODEL.displayValue(),
            deviceName = deviceName.displayValue(),
            androidVersion = Build.VERSION.RELEASE.orUnknown(),
            apiLevel = Build.VERSION.SDK_INT,
            kernel = System.getProperty("os.version").orUnknown(),
        )
    }

    private fun readCpu(): CpuInfo {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        } else {
            Build.HARDWARE
        }.displayValue()

        val cores = Runtime.getRuntime().availableProcessors()

        val maxKhz = (0 until cores).mapNotNull { index ->
            readCpuMaxFreqKhz(index)
        }.maxOrNull()

        val maxFrequency = if (maxKhz != null && maxKhz > 0) {
            val ghz = maxKhz / 1_000_000.0
            val mhz = (maxKhz / 1000.0).roundToInt()
            if (ghz >= 1.0) "%.2f GHz (%d MHz)".format(Locale.US, ghz, mhz) else "$mhz MHz"
        } else {
            "Not available"
        }

        return CpuInfo(
            name = name,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orUnknown(),
            cores = cores,
            maxFrequency = maxFrequency,
        )
    }

    private fun readCpuMaxFreqKhz(index: Int): Long? {
        val candidates = listOf(
            File("/sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq"),
            File("/sys/devices/system/cpu/cpufreq/policy$index/cpuinfo_max_freq"),
            File("/sys/devices/system/cpu/cpu$index/cpufreq/scaling_max_freq"),
            File("/sys/devices/system/cpu/cpufreq/policy$index/scaling_max_freq"),
        )
        for (file in candidates) {
            val value = runCatching { file.readText().trim().toLong() }.getOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun readGpu(context: Context): GpuInfo {
        val configuration = context.getSystemService(ActivityManager::class.java)
            ?.deviceConfigurationInfo
        val openGlEs = configuration?.glEsVersion.orUnknown()
        val packageManager = context.packageManager
        val vulkanFeature = if (packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
            "Supported"
        } else {
            "Not reported"
        }
        return GpuInfo(openGlEs = openGlEs, vulkan = vulkanFeature)
    }

    private fun readMemory(context: Context): MemoryInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)
        return MemoryInfo(total = memoryInfo.totalMem, available = memoryInfo.availMem)
    }

    private fun readStorage(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(
            total = stat.totalBytes,
            available = stat.availableBytes,
        )
    }

    @Suppress("DEPRECATION")
    private fun readDisplay(context: Context): DisplayInfo {
        val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay
        val metrics = context.resources.displayMetrics
        val width = display?.width ?: metrics.widthPixels
        val height = display?.height ?: metrics.heightPixels
        val refreshRate = display?.refreshRate?.let { "${it.roundToInt()} Hz" }.orUnknown()
        val hdrTypes = display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()

        return DisplayInfo(
            resolution = "${width} × ${height}",
            density = "${(metrics.density * 160).roundToInt()} dpi",
            refreshRate = refreshRate,
            hdr = if (hdrTypes.isNotEmpty()) "Supported" else "Not reported",
        )
    }

    private fun readBattery(context: Context): BatteryInfo {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        return BatteryInfo(
            level = if (level >= 0 && scale > 0) "${(level * 100f / scale).roundToInt()}%" else "Unknown",
            charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> "Yes"

                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No"

                else -> "Unknown"
            },
            temperature = if (temperature >= 0) {
                String.format(Locale.US, "%.1f °C", temperature / 10f)
            } else {
                "Unknown"
            },
            voltage = if (voltage >= 0) "${voltage} mV" else "Unknown",
        )
    }

    private fun String?.orUnknown(): String = this?.takeUnless { it.isBlank() } ?: "Unknown"

    private fun String.displayValue(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
