package com.example.toolbox.ui

import android.os.PowerManager

val ToolboxStrings.monitorDescription: String
    get() = if (chinese) {
        "以悬浮窗和折线图显示 CPU、GPU、内存、电量和 FPS 参考值。可自定义显示项目。"
    } else {
        "Show CPU, GPU, memory, battery, and an FPS reference in a floating line-chart overlay. Choose which metrics to display."
    }

val ToolboxStrings.monitoredMetrics: String
    get() = if (chinese) "显示项目" else "Visible metrics"

val ToolboxStrings.cpuFrequenciesTitle: String
    get() = if (chinese) "CPU 核心频率" else "CPU core frequencies"

val ToolboxStrings.cpuFrequenciesDescription: String
    get() = if (chinese) {
        "按在线核心显示当前频率；“/”后为该核心可报告的最高频率。"
    } else {
        "Current frequency for each online core; the value after “/” is its reported maximum."
    }

val ToolboxStrings.cpuDisplayModeTitle: String
    get() = if (chinese) "悬浮窗 CPU 显示" else "Overlay CPU display"

val ToolboxStrings.coreTopologyBars: String
    get() = if (chinese) "核心柱状" else "Core bars"

val ToolboxStrings.coreFrequencies: String
    get() = if (chinese) "逐核心频率" else "Per-core frequency"

val ToolboxStrings.weightedCpuUsage: String
    get() = if (chinese) "加权占比折线" else "Weighted usage chart"

val ToolboxStrings.cpuDisplayModeHint: String
    get() = if (chinese) {
        "核心柱状、逐核心频率或加权占比折线。受系统限制时以 ≈ 标记估算值。"
    } else {
        "Core bars, per-core frequencies, or a weighted usage chart. Restricted sources are marked ≈."
    }

val ToolboxStrings.cpuTopologyTitle: String
    get() = if (chinese) "CPU 核心" else "CPU cores"

val ToolboxStrings.cpuTopologyDescription: String
    get() = if (chinese) {
        "按集群分组显示各核当前频率或占用。"
    } else {
        "Cores grouped by cluster, showing live frequency or load."
    }

val ToolboxStrings.frequencyMode: String
    get() = if (chinese) "频率" else "Freq"

val ToolboxStrings.usageMode: String
    get() = if (chinese) "占用" else "Load"

val ToolboxStrings.matrixView: String
    get() = if (chinese) "柱状" else "Bars"

val ToolboxStrings.gridView: String
    get() = if (chinese) "明细" else "List"

val ToolboxStrings.offlineCore: String
    get() = if (chinese) "休眠" else "Offline"

val ToolboxStrings.littleCore: String
    get() = if (chinese) "小核" else "Little"

val ToolboxStrings.midCore: String
    get() = if (chinese) "中核" else "Mid"

val ToolboxStrings.bigCore: String
    get() = if (chinese) "大核" else "Big"

val ToolboxStrings.primeCore: String
    get() = if (chinese) "超大核" else "Prime"

val ToolboxStrings.waitingForData: String
    get() = if (chinese) "等待数据" else "Waiting for data"

val ToolboxStrings.noFrequencyData: String
    get() = if (chinese) "频率不可用" else "Frequency unavailable"

val ToolboxStrings.maximumFrequency: String
    get() = if (chinese) "最高" else "Max"

val ToolboxStrings.overlayBehaviorTitle: String
    get() = if (chinese) "悬浮窗行为" else "Overlay behavior"

val ToolboxStrings.overlayFixedPosition: String
    get() = if (chinese) "固定位置" else "Lock position"

val ToolboxStrings.overlayFixedPositionDescription: String
    get() = if (chinese) {
        "固定后触控穿透。从本页或通知停止。"
    } else {
        "Locked overlay is touch-through. Stop it here or from the notification."
    }

val ToolboxStrings.overlayAppearanceTitle: String
    get() = if (chinese) "悬浮窗外观" else "Overlay appearance"

val ToolboxStrings.overlayAppearanceDescription: String
    get() = if (chinese) {
        "主题、背景色与透明度。"
    } else {
        "Theme, background, and opacity."
    }

val ToolboxStrings.overlayThemeDark: String
    get() = if (chinese) "深色" else "Dark"

val ToolboxStrings.overlayThemeLight: String
    get() = if (chinese) "浅色" else "Light"

val ToolboxStrings.overlayThemeEverforest: String
    get() = "Everforest"

val ToolboxStrings.overlayThemeSage: String
    get() = if (chinese) "鼠尾草" else "Sage"

val ToolboxStrings.overlayThemeBlack: String
    get() = if (chinese) "纯黑" else "Black"

val ToolboxStrings.overlayThemeCustom: String
    get() = if (chinese) "自定义" else "Custom"

val ToolboxStrings.overlayOpacityTitle: String
    get() = if (chinese) "不透明度" else "Opacity"

val ToolboxStrings.overlayPresetColorsTitle: String
    get() = if (chinese) "背景颜色" else "Background color"

val ToolboxStrings.overlayPreviewTitle: String
    get() = if (chinese) "悬浮窗预览" else "Overlay preview"

val ToolboxStrings.overlayPermissionTitle: String
    get() = if (chinese) "悬浮窗权限" else "Overlay permission"

val ToolboxStrings.permissionsTitle: String
    get() = if (chinese) "所需权限" else "Required permissions"

val ToolboxStrings.overlayPermissionDescription: String
    get() = if (chinese) {
        "用于在其他应用上方显示性能监控悬浮窗。"
    } else {
        "Used to display the performance monitor overlay over other apps."
    }

val ToolboxStrings.openOverlaySettings: String
    get() = if (chinese) "打开权限设置" else "Open permission settings"

val ToolboxStrings.overlayReady: String
    get() = if (chinese) "悬浮窗权限已允许" else "Overlay permission granted"

val ToolboxStrings.requestNotificationPermission: String
    get() = if (chinese) "允许通知" else "Allow notifications"

val ToolboxStrings.foregroundServicePermissionTitle: String
    get() = if (chinese) "前台服务权限" else "Foreground service permission"

val ToolboxStrings.foregroundServiceReady: String
    get() = if (chinese) {
        "前台服务已就绪"
    } else {
        "Foreground service ready"
    }

val ToolboxStrings.startMonitoring: String
    get() = if (chinese) "启动监控" else "Start monitor"

val ToolboxStrings.stopMonitoring: String
    get() = if (chinese) "停止监控" else "Stop monitor"

val ToolboxStrings.thermalTitle: String
    get() = if (chinese) "温度与热状态" else "Temperature and thermal state"

val ToolboxStrings.batteryTemperature: String
    get() = if (chinese) "电池温度" else "Battery temperature"

val ToolboxStrings.thermalStatusTitle: String
    get() = if (chinese) "系统热状态" else "System thermal status"

val ToolboxStrings.thermalHeadroomTitle: String
    get() = if (chinese) "热余量" else "Thermal headroom"

val ToolboxStrings.thermalSensorNote: String
    get() = if (chinese) {
        "展示当前电池温度、系统热状态与热余量。"
    } else {
        "Shows current battery temperature, system thermal state, and thermal headroom."
    }

val ToolboxStrings.fpsTitle: String
    get() = if (chinese) "FPS / 显示刷新率" else "FPS / display refresh rate"

val ToolboxStrings.currentFps: String
    get() = if (chinese) "当前刷新率" else "Current refresh rate"

val ToolboxStrings.supportedRefreshRates: String
    get() = if (chinese) "支持的刷新率" else "Supported refresh rates"

val ToolboxStrings.fpsSourceNote: String
    get() = if (chinese) {
        "显示屏当前刷新率及面板支持的显示模式。"
    } else {
        "Current screen refresh rate and supported display modes."
    }

fun ToolboxStrings.monitorRunning(fixed: Boolean): String = if (chinese) {
    if (fixed) "监控正在运行，悬浮窗位置已固定。" else "监控正在运行，悬浮窗可拖动。"
} else {
    if (fixed) "Monitor is running. Overlay position is locked." else "Monitor is running. Drag the overlay to move it."
}

fun ToolboxStrings.thermalStatus(value: Int): String = when (value) {
    PowerManager.THERMAL_STATUS_NONE -> if (chinese) "正常" else "Normal"
    PowerManager.THERMAL_STATUS_LIGHT -> if (chinese) "轻微" else "Light"
    PowerManager.THERMAL_STATUS_MODERATE -> if (chinese) "中等" else "Moderate"
    PowerManager.THERMAL_STATUS_SEVERE -> if (chinese) "严重" else "Severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> if (chinese) "临界" else "Critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> if (chinese) "紧急" else "Emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> if (chinese) "即将关机" else "Shutdown"
    else -> notAvailable
}

val ToolboxStrings.gpuFallbackHint: String
    get() = if (chinese) {
        "CPU/GPU 优先读取真实占用率；不可用时用频率占比估算（≈），受系统限制则显示不可用。"
    } else {
        "CPU/GPU usage is preferred; frequency ratio is marked as an estimate (≈). Restricted sources show N/A."
    }

fun ToolboxStrings.monitorMetric(metric: com.example.toolbox.monitor.MonitorMetric): String = when (metric) {
    com.example.toolbox.monitor.MonitorMetric.CPU -> "CPU"
    com.example.toolbox.monitor.MonitorMetric.GPU -> "GPU"
    com.example.toolbox.monitor.MonitorMetric.MEMORY -> if (chinese) "内存" else "Memory"
    com.example.toolbox.monitor.MonitorMetric.BATTERY -> if (chinese) "电量" else "Battery"
    com.example.toolbox.monitor.MonitorMetric.FPS -> "FPS"
}

fun ToolboxStrings.cpuCore(index: Int): String = "C$index"

val ToolboxStrings.cpuCoreName: String
    get() = if (chinese) "CPU 核心" else "CPU core"

val ToolboxStrings.currentFrequencyLabel: String
    get() = if (chinese) "实时主频" else "Current frequency"

val ToolboxStrings.cpuMaxFrequency: String
    get() = if (chinese) "最高主频" else "Max frequency"
