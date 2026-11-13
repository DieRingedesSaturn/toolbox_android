package com.example.toolbox.ui


enum class AppLanguage(val displayName: String) {
    ENGLISH("English"),
    CHINESE("中文"),
}

enum class DeviceLabel {
    MANUFACTURER,
    MODEL,
    DEVICE_NAME,
    ANDROID,
    API_LEVEL,
    KERNEL,
    SOC_CPU,
    CORES,
    ABI,
    MAX_FREQUENCY,
    OPENGL_ES,
    VULKAN,
    TOTAL,
    AVAILABLE,
    USED,
    RESOLUTION,
    DENSITY,
    REFRESH_RATE,
    HDR,
    LEVEL,
    CHARGING,
    TEMPERATURE,
    VOLTAGE,
}

enum class DeviceSection {
    IDENTITY,
    CPU,
    GPU,
    MEMORY,
    STORAGE,
    DISPLAY,
    BATTERY,
}

class ToolboxStrings(val language: AppLanguage)

internal val ToolboxStrings.chinese: Boolean
    get() = language == AppLanguage.CHINESE
