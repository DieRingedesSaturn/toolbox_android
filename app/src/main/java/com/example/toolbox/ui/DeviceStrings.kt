package com.example.toolbox.ui


val ToolboxStrings.lastUpdated: String
    get() = if (chinese) "最后更新：" else "Last updated: "

fun ToolboxStrings.sectionTitle(section: DeviceSection): String = when (section) {
    DeviceSection.IDENTITY -> if (chinese) "身份" else "Identity"
    DeviceSection.CPU -> if (chinese) "处理器" else "CPU"
    DeviceSection.GPU -> if (chinese) "图形" else "GPU"
    DeviceSection.MEMORY -> if (chinese) "内存" else "Memory"
    DeviceSection.STORAGE -> if (chinese) "存储" else "Storage"
    DeviceSection.DISPLAY -> if (chinese) "显示" else "Display"
    DeviceSection.BATTERY -> if (chinese) "电池" else "Battery"
}

fun ToolboxStrings.label(label: DeviceLabel): String = when (label) {
    DeviceLabel.MANUFACTURER -> if (chinese) "制造商" else "Manufacturer"
    DeviceLabel.MODEL -> if (chinese) "型号" else "Model"
    DeviceLabel.DEVICE_NAME -> if (chinese) "设备名称" else "Device name"
    DeviceLabel.ANDROID -> "Android"
    DeviceLabel.API_LEVEL -> if (chinese) "API 级别" else "API level"
    DeviceLabel.KERNEL -> if (chinese) "内核" else "Kernel"
    DeviceLabel.SOC_CPU -> if (chinese) "SoC / CPU" else "SoC / CPU"
    DeviceLabel.CORES -> if (chinese) "核心数" else "Cores"
    DeviceLabel.ABI -> "ABI"
    DeviceLabel.MAX_FREQUENCY -> if (chinese) "最高频率" else "Max frequency"
    DeviceLabel.OPENGL_ES -> "OpenGL ES"
    DeviceLabel.VULKAN -> "Vulkan"
    DeviceLabel.TOTAL -> if (chinese) "总量" else "Total"
    DeviceLabel.AVAILABLE -> if (chinese) "可用" else "Available"
    DeviceLabel.USED -> if (chinese) "已用" else "Used"
    DeviceLabel.RESOLUTION -> if (chinese) "分辨率" else "Resolution"
    DeviceLabel.DENSITY -> if (chinese) "像素密度" else "Density"
    DeviceLabel.REFRESH_RATE -> if (chinese) "刷新率" else "Refresh rate"
    DeviceLabel.HDR -> "HDR"
    DeviceLabel.LEVEL -> if (chinese) "电量" else "Level"
    DeviceLabel.CHARGING -> if (chinese) "充电状态" else "Charging"
    DeviceLabel.TEMPERATURE -> if (chinese) "温度" else "Temperature"
    DeviceLabel.VOLTAGE -> if (chinese) "电压" else "Voltage"
}

fun ToolboxStrings.localizeValue(value: String): String = when (value) {
    "Unknown" -> if (chinese) "未知" else value
    "Not available" -> if (chinese) "不可用" else value
    "Not reported" -> if (chinese) "未报告" else value
    "Supported" -> if (chinese) "支持" else value
    "Yes" -> if (chinese) "是" else value
    "No" -> if (chinese) "否" else value
    else -> value
}
