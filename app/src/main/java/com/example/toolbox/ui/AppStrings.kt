package com.example.toolbox.ui

import android.os.PowerManager
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.location.LocationReadStatus
import com.example.toolbox.network.NetworkTransport

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

class ToolboxStrings(val language: AppLanguage) {
    private val chinese: Boolean
        get() = language == AppLanguage.CHINESE

    val appName: String
        get() = "Toolbox"
    val about: String
        get() = if (chinese) "关于" else "About"
    val settings: String
        get() = if (chinese) "设置" else "Settings"
    val device: String
        get() = if (chinese) "设备" else "Device"
    val location: String
        get() = if (chinese) "位置" else "Location"
    val network: String
        get() = if (chinese) "网络" else "Network"
    val monitor: String
        get() = if (chinese) "实时监控" else "Live monitor"
    val astronomy: String
        get() = if (chinese) "天文" else "Astronomy"
    val loading: String
        get() = if (chinese) "读取中…" else "Reading…"
    val refresh: String
        get() = if (chinese) "刷新" else "Refresh"
    val refreshing: String
        get() = if (chinese) "刷新中…" else "Refreshing…"
    val chooseTool: String
        get() = if (chinese) "选择工具" else "Choose a tool"
    val lastUpdated: String
        get() = if (chinese) "最后更新：" else "Last updated: "
    val noData: String
        get() = if (chinese) "暂无设备信息" else "No device information yet"
    val notReadYet: String
        get() = if (chinese) "尚未读取" else "Not read yet"
    val copy: String
        get() = if (chinese) "复制" else "Copy"
    val monitorDescription: String
        get() = if (chinese) {
            "以悬浮窗和折线图显示 CPU、GPU、内存、电量和 FPS 参考值。可自定义显示项目。"
        } else {
            "Show CPU, GPU, memory, battery, and an FPS reference in a floating line-chart overlay. Choose which metrics to display."
        }
    val monitoredMetrics: String
        get() = if (chinese) "显示项目" else "Visible metrics"
    val cpuFrequenciesTitle: String
        get() = if (chinese) "CPU 核心频率" else "CPU core frequencies"
    val cpuFrequenciesDescription: String
        get() = if (chinese) {
            "按在线核心显示当前频率；“/”后为该核心可报告的最高频率。"
        } else {
            "Current frequency for each online core; the value after “/” is its reported maximum."
        }
    val cpuDisplayModeTitle: String
        get() = if (chinese) "悬浮窗 CPU 显示" else "Overlay CPU display"
    val coreTopologyBars: String
        get() = if (chinese) "核心拓扑直角柱" else "Core Topology Bars"
    val coreFrequencies: String
        get() = if (chinese) "逐核心频率" else "Per-core frequency"
    val weightedCpuUsage: String
        get() = if (chinese) "加权占比折线" else "Weighted usage chart"
    val cpuDisplayModeHint: String
        get() = if (chinese) {
            "支持核心拓扑直角柱、逐核心频率与加权占比折线图，满足不同监控偏好。受系统权限影响时以 ≈ 标记估算值。"
        } else {
            "Supports Core Topology Bars, Per-core Frequencies, and Weighted Usage Chart."
        }
    val cpuTopologyTitle: String
        get() = if (chinese) "CPU 核心拓扑图谱" else "CPU Core Topology"
    val cpuTopologyDescription: String
        get() = if (chinese) {
            "按各核心物理算力能级加权分配宽度，基频归零实时呈现各核调频与负载状态。"
        } else {
            "Weighted core widths by cluster capacity, showing live frequency and load scaling from baseline 0."
        }
    val frequencyMode: String
        get() = if (chinese) "实时主频" else "Frequency"
    val usageMode: String
        get() = if (chinese) "核心负载" else "Core Load"
    val matrixView: String
        get() = if (chinese) "图谱视图" else "Topology Matrix"
    val gridView: String
        get() = if (chinese) "数据网格" else "Data Grid"
    val offlineCore: String
        get() = if (chinese) "休眠" else "Offline"
    val littleCore: String
        get() = if (chinese) "小核" else "Little"
    val midCore: String
        get() = if (chinese) "中核" else "Mid"
    val bigCore: String
        get() = if (chinese) "大核" else "Big"
    val primeCore: String
        get() = if (chinese) "超大核" else "Prime"

    val waitingForData: String
        get() = if (chinese) "等待数据" else "Waiting for data"
    val noFrequencyData: String
        get() = if (chinese) "频率不可用" else "Frequency unavailable"
    val maximumFrequency: String
        get() = if (chinese) "最高" else "Max"
    val overlayBehaviorTitle: String
        get() = if (chinese) "悬浮窗行为" else "Overlay behavior"
    val overlayFixedPosition: String
        get() = if (chinese) "固定位置" else "Lock position"
    val overlayFixedPositionDescription: String
        get() = if (chinese) {
            "开启后悬浮窗固定且触控穿透，不会妨碍背后操作；关闭即可再次拖动。固定时请从监控页或通知停止。"
        } else {
            "Locks the overlay and lets touches pass through; turn it off to drag again. Stop it from the monitor page or notification while locked."
        }
    val overlayAppearanceTitle: String
        get() = if (chinese) "悬浮窗外观" else "Overlay appearance"
    val overlayAppearanceDescription: String
        get() = if (chinese) {
            "支持深色、浅色、Everforest、纯黑预设，以及自定义背景色与透明度调节。"
        } else {
            "Supports Dark, Light, Everforest, Black presets, and custom background color with opacity slider."
        }
    val overlayThemeDark: String
        get() = if (chinese) "深色" else "Dark"
    val overlayThemeLight: String
        get() = if (chinese) "浅色" else "Light"
    val overlayThemeEverforest: String
        get() = "Everforest"
    val overlayThemeBlack: String
        get() = if (chinese) "纯黑" else "Black"
    val overlayThemeCustom: String
        get() = if (chinese) "自定义" else "Custom"
    val overlayOpacityTitle: String
        get() = if (chinese) "不透明度" else "Opacity"
    val overlayPresetColorsTitle: String
        get() = if (chinese) "背景颜色" else "Background color"
    val overlayPreviewTitle: String
        get() = if (chinese) "悬浮窗预览" else "Overlay preview"
    val overlayPermissionTitle: String
        get() = if (chinese) "悬浮窗权限" else "Overlay permission"
    val permissionsTitle: String
        get() = if (chinese) "所需权限" else "Required permissions"
    val overlayPermissionDescription: String
        get() = if (chinese) {
            "用于在其他应用上方显示性能监控悬浮窗。"
        } else {
            "Used to display the performance monitor overlay over other apps."
        }
    val openOverlaySettings: String
        get() = if (chinese) "打开权限设置" else "Open permission settings"
    val overlayReady: String
        get() = if (chinese) "悬浮窗权限已允许" else "Overlay permission granted"
    val notificationPermissionTitle: String
        get() = if (chinese) "通知权限" else "Notification permission"
    val notificationReady: String
        get() = if (chinese) "通知权限已允许" else "Notification permission granted"
    val requestNotificationPermission: String
        get() = if (chinese) "允许通知" else "Allow notifications"
    val notificationPermissionHint: String
        get() = if (chinese) "允许通知以便在状态栏显示监控状态与快捷停止操作。" else "Allow notifications to see monitor status and stop action in status bar."
    val foregroundServicePermissionTitle: String
        get() = if (chinese) "前台服务权限" else "Foreground service permission"
    val foregroundServiceReady: String
        get() = if (chinese) {
            "前台服务已就绪"
        } else {
            "Foreground service ready"
        }
    val startMonitoring: String
        get() = if (chinese) "启动监控" else "Start monitor"
    val stopMonitoring: String
        get() = if (chinese) "停止监控" else "Stop monitor"
    val thermalTitle: String
        get() = if (chinese) "温度与热状态" else "Temperature and thermal state"
    val batteryTemperature: String
        get() = if (chinese) "电池温度" else "Battery temperature"
    val thermalStatusTitle: String
        get() = if (chinese) "系统热状态" else "System thermal status"
    val thermalHeadroomTitle: String
        get() = if (chinese) "热余量" else "Thermal headroom"
    val thermalSensorNote: String
        get() = if (chinese) {
            "展示当前电池温度、系统热状态与热余量。"
        } else {
            "Shows current battery temperature, system thermal state, and thermal headroom."
        }
    val fpsTitle: String
        get() = if (chinese) "FPS / 显示刷新率" else "FPS / display refresh rate"
    val currentFps: String
        get() = if (chinese) "当前刷新率" else "Current refresh rate"
    val supportedRefreshRates: String
        get() = if (chinese) "支持的刷新率" else "Supported refresh rates"
    val fpsSourceNote: String
        get() = if (chinese) {
            "显示屏当前刷新率及面板支持的显示模式。"
        } else {
            "Current screen refresh rate and supported display modes."
        }
    val notAvailable: String
        get() = if (chinese) "不可用" else "Not available"
    fun monitorRunning(fixed: Boolean): String = if (chinese) {
        if (fixed) "监控正在运行，悬浮窗位置已固定。" else "监控正在运行，悬浮窗可拖动。"
    } else {
        if (fixed) "Monitor is running. Overlay position is locked." else "Monitor is running. Drag the overlay to move it."
    }
    fun thermalStatus(value: Int): String = when (value) {
        PowerManager.THERMAL_STATUS_NONE -> if (chinese) "正常" else "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> if (chinese) "轻微" else "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> if (chinese) "中等" else "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> if (chinese) "严重" else "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> if (chinese) "临界" else "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> if (chinese) "紧急" else "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> if (chinese) "即将关机" else "Shutdown"
        else -> notAvailable
    }
    val gpuFallbackHint: String
        get() = if (chinese) {
            "CPU/GPU 优先读取真实占用率；不可用时用频率占比估算（≈），受系统限制则显示不可用。"
        } else {
            "CPU/GPU usage is preferred; frequency ratio is marked as an estimate (≈). Restricted sources show N/A."
        }

    val languageTitle: String
        get() = if (chinese) "语言" else "Language"
    val themeTitle: String
        get() = if (chinese) "外观" else "Appearance"
    val accentTitle: String
        get() = if (chinese) "主题颜色" else "Theme color"
    val system: String
        get() = if (chinese) "跟随系统" else "System"
    val light: String
        get() = if (chinese) "浅色" else "Light"
    val dark: String
        get() = if (chinese) "深色" else "Dark"
    val dynamic: String
        get() = if (chinese) "动态" else "Dynamic"
    val everforest: String
        get() = "Everforest"
    val blue: String
        get() = if (chinese) "蓝色" else "Blue"
    val green: String
        get() = if (chinese) "绿色" else "Green"
    val orange: String
        get() = if (chinese) "橙色" else "Orange"
    val purple: String
        get() = if (chinese) "紫色" else "Purple"

    val aboutTitle: String
        get() = if (chinese) "轻量 Android Toolbox" else "A lightweight Android Toolbox"
    val aboutDescription: String
        get() = if (chinese) {
            "Toolbox 是一款简洁实用的系统工具箱，提供设备硬件信息、实时性能悬浮窗、位置导航与网络状态诊断。"
        } else {
            "Toolbox is a clean system utility providing hardware info, live monitor overlay, location navigation, and network diagnostics."
        }
    val aboutScopeTitle: String
        get() = if (chinese) "主要功能" else "Key features"
    val aboutScope: List<String>
        get() = if (chinese) {
            listOf(
                "设备信息：处理器、图形、内存、存储、显示和电池",
                "实时监控：悬浮窗性能图表、多主题与自定义背景",
                "位置导航：地理坐标、定位精度、海拔与星空雷达图",
                "网络状态：连接详情、本地地址与公网 IP 地理位置",
            )
        } else {
            listOf(
                "Device Info: CPU, GPU, memory, storage, display, and battery",
                "Live Monitor: overlay performance graphs, themes, and custom colors",
                "Location: coordinates, accuracy, terrain elevation, and sky view radar",
                "Network: connection details, local IPs, and public IP geolocation",
            )
        }
    val version: String
        get() = if (chinese) "版本 0.1" else "Version 0.1"

    val locationDescription: String
        get() = if (chinese) "查看当前设备的地理坐标、定位精度、卫星导航与海拔高程信息。" else "View current coordinates, accuracy, satellite positioning, and elevation."
    val locationPermissionTitle: String
        get() = if (chinese) "位置权限" else "Location permission"
    val locationPermissionDescription: String
        get() = if (chinese) {
            "用于读取当前设备的经纬度坐标与卫星导航信号。"
        } else {
            "Used to retrieve current coordinates and satellite positioning signals."
        }
    val requestLocationPermission: String
        get() = if (chinese) "请求位置权限" else "Request location permission"
    val locationPermissionGranted: String
        get() = if (chinese) "位置权限已允许" else "Location permission granted"
    val locationPermissionNotGranted: String
        get() = if (chinese) "尚未允许位置权限" else "Location permission not granted"
    val locationPrecision: String
        get() = if (chinese) "定位精度" else "Location precision"
    val preciseLocation: String
        get() = if (chinese) "精确" else "Precise"
    val approximateLocation: String
        get() = if (chinese) "大致" else "Approximate"
    val currentLocationTitle: String
        get() = if (chinese) "当前位置" else "Current location"
    val getCurrentLocation: String
        get() = if (chinese) "获取当前位置" else "Get current location"
    val locationLoading: String
        get() = if (chinese) "定位中…" else "Locating…"
    val locationOpenSettings: String
        get() = if (chinese) "打开位置设置" else "Open location settings"
    val latitude: String
        get() = if (chinese) "纬度" else "Latitude"
    val longitude: String
        get() = if (chinese) "经度" else "Longitude"
    val latitudeDms: String
        get() = if (chinese) "纬度（DMS）" else "Latitude (DMS)"
    val longitudeDms: String
        get() = if (chinese) "经度（DMS）" else "Longitude (DMS)"
    val accuracy: String
        get() = if (chinese) "水平精度" else "Horizontal accuracy"
    val verticalAccuracy: String
        get() = if (chinese) "垂直精度" else "Vertical accuracy"
    val altitude: String
        get() = if (chinese) "提供方高度" else "Provider altitude"
    val provider: String
        get() = if (chinese) "提供方" else "Provider"
    val locationTime: String
        get() = if (chinese) "定位时间" else "Location time"
    val locationDataUnavailable: String
        get() = if (chinese) "尚未成功获取位置数据。" else "No successful location data yet."
    val positioningSystemsTitle: String
        get() = if (chinese) "定位系统与提供方" else "Positioning systems & providers"
    val availableProviders: String
        get() = if (chinese) "可用定位提供方" else "Available providers"
    val enabledProviders: String
        get() = if (chinese) "已启用提供方" else "Enabled providers"
    val gnssConstellations: String
        get() = if (chinese) "支持的卫星导航系统" else "Supported GNSS constellations"
    val gnssHardwareStatus: String
        get() = if (chinese) "GNSS 芯片硬件" else "GNSS hardware"
    val gnssHardwareAvailable: String
        get() = if (chinese) "已具备独立硬件支持" else "Hardware supported"
    val gnssHardwareUnavailable: String
        get() = if (chinese) "未检测到独立 GNSS 硬件" else "No dedicated GNSS hardware"
    val terrainElevationTitle: String
        get() = if (chinese) "地表地形海拔 (DEM)" else "Terrain elevation (DEM)"
    val queryTerrainElevation: String
        get() = if (chinese) "查询地表地形海拔" else "Query terrain elevation"
    val queryingTerrainElevation: String
        get() = if (chinese) "查询海拔中…" else "Querying elevation…"
    val terrainElevationSource: String
        get() = if (chinese) "高程数据源" else "Elevation source"
    val elevationDifferenceNote: String
        get() = if (chinese) {
            "GNSS 高度由卫星芯片几何计算；地表地形海拔由公开高程模型（DEM）按经纬度查询，二者互为参考。"
        } else {
            "GNSS altitude is calculated by GPS hardware; terrain elevation is queried from public DEM elevation models."
        }
    val gnssSkyViewTitle: String
        get() = if (chinese) "卫星雷达与信号强度" else "Satellite sky view & signals"
    val gnssObserving: String
        get() = if (chinese) "正在观测卫星…" else "Observing satellites…"
    val gnssStartObservation: String
        get() = if (chinese) "开启卫星观测" else "Start observation"
    val gnssStopObservation: String
        get() = if (chinese) "停止观测" else "Stop observation"
    val gnssObservationHint: String
        get() = if (chinese) {
            "实时观测天空中的卫星分布、信号强度与 3D 定位状态。"
        } else {
            "Real-time observation of satellite distribution, signal strength, and 3D fix status."
        }
    val gnssNoSatellitesHint: String
        get() = if (chinese) {
            "尚未搜到卫星信号。室内或密集建筑遮挡可能影响搜星，建议移步开阔室外。"
        } else {
            "No satellite signals detected yet. Indoor or obstructed environments may block signals; try moving outdoors."
        }
    val gnssTotalSatellites: String
        get() = if (chinese) "搜星总数" else "Visible satellites"
    val gnssUsedInFix: String
        get() = if (chinese) "参解卫星" else "Used in fix"
    val gnssSignalStrength: String
        get() = if (chinese) "信号强度 (C/N0)" else "Signal strength (C/N0)"
    val gnssSkyRadar: String
        get() = if (chinese) "星空雷达天空图" else "Sky view radar"
    val gnssElevation: String
        get() = if (chinese) "仰角" else "Elevation"
    val gnssAzimuth: String
        get() = if (chinese) "方位角" else "Azimuth"
    val gnssUsedTag: String
        get() = if (chinese) "已参解" else "Used"
    val gnssUnusedTag: String
        get() = if (chinese) "未锁定" else "Unused"
    val gnssFixStatus: String
        get() = if (chinese) "定位解状态" else "Fix status"
    val gnssFixStatusSearching: String
        get() = if (chinese) "搜星阶段（未完成 3D 定位）" else "Searching (No 3D fix)"
    val gnssFixStatusLocked: String
        get() = if (chinese) "已锁定 3D 定位" else "3D fix locked"
    val gnssIndoorFixHint: String
        get() = if (chinese) {
            "已捕获卫星信号但参解为 0，说明当前处于搜星/同步星历阶段。室内或窗边信号衰减严重无法完成 3D 定位解算，移至开阔室外可立即锁定。"
        } else {
            "Satellites detected but 0 used in fix: the chip is synchronizing ephemeris. Indoor attenuation prevents 3D fix calculation; move outdoors for a rapid fix."
        }
    val gnssLegendTitle: String
        get() = if (chinese) "图例" else "Legend"
    val gnssEarthCenter: String
        get() = if (chinese) "中心：手机观测点 (90° 天顶)" else "Center: Observer (90° Zenith)"
    val networkDescription: String
        get() = if (chinese) "查看当前网络的连接状态、本地 IP 地址、DNS 服务器与公网出口信息。" else "View network connection status, local IP addresses, DNS servers, and public egress info."
    val networkConnectionTitle: String
        get() = if (chinese) "连接详情" else "Connection details"
    val networkConnection: String
        get() = if (chinese) "连接状态" else "Connection"
    val networkTransport: String
        get() = if (chinese) "传输方式" else "Transport"
    val networkInterface: String
        get() = if (chinese) "网络接口" else "Interface"
    val networkMetered: String
        get() = if (chinese) "按流量计费" else "Metered"
    val localAddresses: String
        get() = if (chinese) "本地地址" else "Local addresses"
    val dnsServers: String
        get() = if (chinese) "DNS 服务器" else "DNS servers"
    val gateways: String
        get() = if (chinese) "网关" else "Gateways"
    val networkPermissionTitle: String
        get() = if (chinese) "网络权限" else "Network permissions"
    val networkPermissionDescription: String
        get() = if (chinese) {
            "用于读取本地网络连接状态与路由接口信息。"
        } else {
            "Used to read local network connection status and routing interface details."
        }
    val publicIpTitle: String
        get() = if (chinese) "公网 IP" else "Public IP"
    val publicIp: String
        get() = if (chinese) "公网地址" else "Public address"
    val publicIpLocation: String
        get() = if (chinese) "IP 地理位置" else "IP location"
    val publicIpIsp: String
        get() = if (chinese) "运营商 / 组织" else "ISP / Organization"
    val publicIpDescription: String
        get() = if (chinese) {
            "查询并显示当前网络出口的公网 IP 地址、大致地理位置与网络运营商。"
        } else {
            "Query and display your public egress IP, approximate location, and ISP."
        }
    val queryPublicIp: String
        get() = if (chinese) "查询公网 IP" else "Query public IP"
    val publicIpNotRequested: String
        get() = if (chinese) "尚未查询" else "Not requested"
    val connected: String
        get() = if (chinese) "已连接" else "Connected"
    val disconnected: String
        get() = if (chinese) "未连接" else "Disconnected"
    val yes: String
        get() = if (chinese) "是" else "Yes"
    val no: String
        get() = if (chinese) "否" else "No"

    val astronomyTitle: String
        get() = if (chinese) "天文观测与夜空时段" else "Astronomy & Night Sky"
    val astronomyDescription: String
        get() = if (chinese) "查看今夜太阳系行星及月球的升落与最佳观测时间窗口。" else "View rise/set and optimal observation windows for planets and Moon tonight."
    val nightTimelineTitle: String
        get() = if (chinese) "今夜天体可见时段图" else "Tonight's Visibility Timeline"
    val sunsetLabel: String
        get() = if (chinese) "日落" else "Sunset"
    val sunriseLabel: String
        get() = if (chinese) "日出" else "Sunrise"
    val darkNightLabel: String
        get() = if (chinese) "完全暗夜" else "Dark night"
    val twilightLabel: String
        get() = if (chinese) "暮光" else "Twilight"
    val moonPhaseTitle: String
        get() = if (chinese) "月相与月龄" else "Moon Phase & Age"
    val moonIllumination: String
        get() = if (chinese) "照亮比例" else "Illumination"
    val moonAge: String
        get() = if (chinese) "月龄" else "Moon age"
    val daysUnit: String
        get() = if (chinese) "天" else "days"
    val celestialBodiesTitle: String
        get() = if (chinese) "太阳系天体详情" else "Solar System Bodies"
    val visibleTonight: String
        get() = if (chinese) "今夜可见" else "Visible tonight"
    val notVisibleTonight: String
        get() = if (chinese) "今夜地平线以下" else "Below horizon tonight"
    val maxAltitude: String
        get() = if (chinese) "最高仰角" else "Max altitude"
    val transitTime: String
        get() = if (chinese) "中天时刻" else "Transit time"
    val riseTime: String
        get() = if (chinese) "升起时刻" else "Rise"
    val setTime: String
        get() = if (chinese) "落下时刻" else "Set"
    val currentAltAz: String
        get() = if (chinese) "当前高度 / 方位" else "Current Alt / Az"
    val visualMagnitude: String
        get() = if (chinese) "视星等" else "Magnitude"
    val timelineLegendBar: String
        get() = if (chinese) "彩色横条：天体位于地平线以上（仰角 > 0°）的可观测时段" else "Colored bar: Celestial body above horizon (Alt > 0°)"
    val observerCoordinates: String
        get() = if (chinese) "观测坐标" else "Observer coordinates"
    val defaultLocationNote: String
        get() = if (chinese) "（默认参考坐标）" else "(Default reference)"
    val exportImage: String
        get() = if (chinese) "导出图片" else "Export image"
    val exportSuccess: String
        get() = if (chinese) "已保存至相册 (Pictures/Toolbox)" else "Saved to Pictures/Toolbox"
    val exportFailed: String
        get() = if (chinese) "导出图片失败" else "Failed to export image"
    val shareImage: String
        get() = if (chinese) "分享观星时段图" else "Share visibility chart"

    fun locationStatus(status: LocationReadStatus): String = when (status) {
        LocationReadStatus.SUCCESS -> if (chinese) "定位成功" else "Location received"
        LocationReadStatus.PERMISSION_REQUIRED -> locationPermissionNotGranted
        LocationReadStatus.SERVICES_DISABLED -> if (chinese) "位置服务未开启" else "Location services are disabled"
        LocationReadStatus.TIMEOUT -> if (chinese) "定位超时，请重试" else "Location request timed out; try again"
        LocationReadStatus.UNAVAILABLE -> if (chinese) "位置服务不可用" else "Location service unavailable"
        LocationReadStatus.ERROR -> if (chinese) "定位失败" else "Location request failed"
    }

    fun networkTransport(value: NetworkTransport): String = when (value) {
        NetworkTransport.WIFI -> "Wi-Fi"
        NetworkTransport.CELLULAR -> if (chinese) "移动网络" else "Cellular"
        NetworkTransport.ETHERNET -> if (chinese) "以太网" else "Ethernet"
        NetworkTransport.VPN -> "VPN"
        NetworkTransport.BLUETOOTH -> if (chinese) "蓝牙" else "Bluetooth"
        NetworkTransport.OTHER -> if (chinese) "其他" else "Other"
    }

    fun moduleTitle(module: ToolboxModule): String = when (module) {
        ToolboxModule.HOME -> appName
        ToolboxModule.DEVICE -> device
        ToolboxModule.LOCATION -> location
        ToolboxModule.NETWORK -> network
        ToolboxModule.MONITOR -> monitor
        ToolboxModule.ASTRONOMY -> astronomy
        ToolboxModule.USAGE -> appUsage
        ToolboxModule.SETTINGS -> settings
        ToolboxModule.ABOUT -> about
    }

    val appUsage: String
        get() = if (chinese) "应用与流量统计" else "App Usage & Data"
    val usagePermissionTitle: String
        get() = if (chinese) "使用情况访问权限" else "Usage access permission"
    val usagePermissionDescription: String
        get() = if (chinese) {
            "用于读取各应用的前台屏幕使用时间与分网段（蜂窝/WLAN）流量消耗。数据由系统底层记录，完全离线计算，零后台耗电。"
        } else {
            "Used to read screen time and network traffic (Cellular/Wi-Fi) per app. Calculated offline from OS logs with zero background battery usage."
        }
    val openUsageSettings: String
        get() = if (chinese) "前往系统设置授权" else "Grant in System Settings"
    val totalScreenTime: String
        get() = if (chinese) "总屏幕时长" else "Total Screen Time"
    val totalCellularData: String
        get() = if (chinese) "移动流量" else "Mobile Data"
    val totalWifiData: String
        get() = if (chinese) "WLAN 流量" else "Wi-Fi Data"
    val totalTraffic: String
        get() = if (chinese) "总消耗流量" else "Total Traffic"
    val categoryBreakdown: String
        get() = if (chinese) "分类时间分布" else "Category Breakdown"
    val noUsageData: String
        get() = if (chinese) "该时间段内无应用使用与流量记录" else "No usage records found for this period"

    fun moduleSummary(module: ToolboxModule, info: DeviceInfo?): String = when (module) {
        ToolboxModule.DEVICE -> info?.let { "${it.cpu.name} · ${it.cpu.cores} ${if (chinese) "核心" else "cores"}" } ?: loading
        ToolboxModule.LOCATION -> if (chinese) "坐标、海拔与卫星雷达" else "Coordinates, elevation, and sky view"
        ToolboxModule.NETWORK -> if (chinese) "本地连接与公网 IP" else "Local connection and public IP"
        ToolboxModule.MONITOR -> if (chinese) "悬浮窗实时性能监控" else "Floating live performance monitor"
        ToolboxModule.ASTRONOMY -> if (chinese) "行星可见时段与月相" else "Planet visibility & moon phase"
        ToolboxModule.USAGE -> if (chinese) "使用时长与分网流量" else "Screen time & cellular/Wi-Fi data"
        ToolboxModule.SETTINGS -> if (chinese) "外观、主题与偏好设置" else "Appearance, theme, and preferences"
        ToolboxModule.ABOUT -> if (chinese) "版本与应用信息" else "Version and app info"
        else -> ""
    }

    fun sectionTitle(section: DeviceSection): String = when (section) {
        DeviceSection.IDENTITY -> if (chinese) "身份" else "Identity"
        DeviceSection.CPU -> if (chinese) "处理器" else "CPU"
        DeviceSection.GPU -> if (chinese) "图形" else "GPU"
        DeviceSection.MEMORY -> if (chinese) "内存" else "Memory"
        DeviceSection.STORAGE -> if (chinese) "存储" else "Storage"
        DeviceSection.DISPLAY -> if (chinese) "显示" else "Display"
        DeviceSection.BATTERY -> if (chinese) "电池" else "Battery"
    }

    fun label(label: DeviceLabel): String = when (label) {
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

    fun localizeValue(value: String): String = when (value) {
        "Unknown" -> if (chinese) "未知" else value
        "Not available" -> if (chinese) "不可用" else value
        "Not reported" -> if (chinese) "未报告" else value
        "Supported" -> if (chinese) "支持" else value
        "Yes" -> if (chinese) "是" else value
        "No" -> if (chinese) "否" else value
        else -> value
    }

    fun copied(label: String): String = if (chinese) "已复制：$label" else "Copied $label"

    fun monitorMetric(metric: com.example.toolbox.monitor.MonitorMetric): String = when (metric) {
        com.example.toolbox.monitor.MonitorMetric.CPU -> "CPU"
        com.example.toolbox.monitor.MonitorMetric.GPU -> "GPU"
        com.example.toolbox.monitor.MonitorMetric.MEMORY -> if (chinese) "内存" else "Memory"
        com.example.toolbox.monitor.MonitorMetric.BATTERY -> if (chinese) "电量" else "Battery"
        com.example.toolbox.monitor.MonitorMetric.FPS -> "FPS"
    }

    fun cpuCore(index: Int): String = "C$index"
}
