package com.example.toolbox.device

data class DeviceInfo(
    val identity: IdentityInfo,
    val cpu: CpuInfo,
    val gpu: GpuInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val display: DisplayInfo,
    val battery: BatteryInfo,
)

data class IdentityInfo(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val apiLevel: Int,
    val kernel: String,
)

data class CpuInfo(
    val name: String,
    val abi: String,
    val cores: Int,
    val maxFrequency: String,
)

data class GpuInfo(
    val openGlEs: String,
    val vulkan: String,
)

data class MemoryInfo(
    val total: Long,
    val available: Long,
)

data class StorageInfo(
    val total: Long,
    val available: Long,
)

data class DisplayInfo(
    val resolution: String,
    val density: String,
    val refreshRate: String,
    val hdr: String,
)

data class BatteryInfo(
    val level: String,
    val charging: String,
    val temperature: String,
    val voltage: String,
)
