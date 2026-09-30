package com.example.toolbox.ui

import android.os.PowerManager
import com.example.toolbox.astronomy.CelestialBodyVisibility
import com.example.toolbox.astronomy.MoonPhase
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.ledger.BackupFailure
import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CycleUnit
import com.example.toolbox.ledger.LedgerCategory
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.location.LocationReadStatus
import com.example.toolbox.network.NetworkTransport
import com.example.toolbox.usage.AppCategory
import com.example.toolbox.usage.UsageSortMode
import com.example.toolbox.usage.UsageTimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val back: String
        get() = if (chinese) "返回" else "Back"
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
    val custom: String
        get() = if (chinese) "自定义" else "Custom"
    val accentDynamicHint: String
        get() = if (chinese) {
            "跟随系统壁纸取色（需要 Android 12+）"
        } else {
            "Colors follow your wallpaper (requires Android 12+)"
        }
    val accentHexLabel: String
        get() = if (chinese) "色值" else "Hex color"
    val accentHexError: String
        get() = if (chinese) "请输入 6 位十六进制色值" else "Enter 6 hex digits"

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
                "天文观测：今夜行星可见时段、月相与观测图导出",
                "应用与流量：前台屏幕时长、蜂窝/WLAN 分网统计与分类筛选",
                "记账成本：收支明细、大件日均摊销与订阅周期成本",
            )
        } else {
            listOf(
                "Device Info: CPU, GPU, memory, storage, display, and battery",
                "Live Monitor: overlay performance graphs, themes, and custom colors",
                "Location: coordinates, accuracy, terrain elevation, and sky view radar",
                "Network: connection details, local IPs, and public IP geolocation",
                "Astronomy: tonight's planet visibility windows, moon phase, and chart export",
                "App usage & data: screen time, cellular/Wi-Fi stats, and category filters",
                "Ledger & cost tracker: expenses, asset amortization, and subscription costs",
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
        get() = if (chinese) "已观测的卫星导航系统" else "Observed GNSS constellations"
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
        ToolboxModule.LEDGER -> ledger
        ToolboxModule.FX -> if (chinese) "汇率换算" else "Currency converter"
        ToolboxModule.SETTINGS -> settings
        ToolboxModule.ABOUT -> about
    }

    val ledger: String
        get() = if (chinese) "记账与成本摊销" else "Ledger & Cost Tracker"
    val ledgerWidgetTitle: String
        get() = if (chinese) "记账" else "Ledger"
    val ledgerTabOverview: String
        get() = if (chinese) "概览" else "Overview"
    val ledgerTabTransactions: String
        get() = if (chinese) "明细" else "Transactions"
    val ledgerTabCosts: String
        get() = if (chinese) "成本" else "Costs"
    val addEntryShort: String
        get() = if (chinese) "记一笔" else "Add"
    val allToolsLabel: String
        get() = if (chinese) "全部工具" else "All tools"
    val unknown: String
        get() = if (chinese) "未知" else "Unknown"
    val chargingNow: String
        get() = if (chinese) "充电中" else "Charging"
    val notCharging: String
        get() = if (chinese) "未充电" else "Not charging"
    fun storageOfTotal(total: String): String =
        if (chinese) "共 $total" else "of $total"
    fun monthEntriesCount(count: Int): String =
        if (chinese) "$count 笔" else "$count entr${if (count == 1) "y" else "ies"}"
    val dailyBurnRateTitle: String
        get() = if (chinese) "平均持有与订阅成本看板" else "Average Usage & Subscription Cost"
    val totalDailyCostLabel: String
        get() = if (chinese) "每日固定持有总成本" else "Total Daily Burn Rate"
    val totalMonthlyCostLabel: String
        get() = if (chinese) "每月等效固定成本" else "Equivalent Monthly Cost"
    val totalYearlyCostLabel: String
        get() = if (chinese) "每年等效固定成本" else "Equivalent Yearly Cost"
    val monthExpenseLabel: String
        get() = if (chinese) "本月支出" else "Spending"
    val monthIncomeLabel: String
        get() = if (chinese) "收入" else "Income"
    val monthNetLabel: String
        get() = if (chinese) "结余" else "Net"
    val categoryBreakdownMonthTitle: String
        get() = if (chinese) "本月支出分类" else "Spending by category"
    val noExpenseThisMonth: String
        get() = if (chinese) "本月暂无支出" else "No expenses this month"
    val dailyCostCaption: String
        get() = if (chinese) "日均持有成本" else "Daily cost"
    val recentEntriesTitle: String
        get() = if (chinese) "最近记录" else "Recent"
    val seeAllAction: String
        get() = if (chinese) "查看全部" else "See all"
    val emptyOverviewHint: String
        get() = if (chinese) {
            "还没有记录，点右下角「记一笔」开始记账。"
        } else {
            "No entries yet — tap 'Add' to record your first one."
        }
    val emptyMonthEntries: String
        get() = if (chinese) "本月暂无收支记录" else "No entries this month"
    val searchAction: String
        get() = if (chinese) "搜索" else "Search"
    val searchFieldHint: String
        get() = if (chinese) {
            "搜索标题、备注、标签、账户或金额"
        } else {
            "Search title, note, tag, account, or amount"
        }
    val searchEmptyResult: String
        get() = if (chinese) "没有匹配的记录" else "No matching entries"
    val fxSearchHint: String
        get() = if (chinese) "搜索货币代码或名称" else "Search code or currency name"
    val fxSearchEmpty: String
        get() = if (chinese) "没有匹配的货币" else "No matching currency"
    fun searchResultCount(count: Int): String =
        if (chinese) "找到 $count 条记录" else {
            "$count result${if (count == 1) "" else "s"}"
        }
    val costSectionAssets: String
        get() = if (chinese) "长期资产" else "Assets"
    val costSectionSubscriptions: String
        get() = if (chinese) "订阅" else "Subscriptions"
    val assetTag: String
        get() = if (chinese) "长期资产" else "Asset"
    val subTag: String
        get() = if (chinese) "订阅" else "Sub"
    val entryDateLabel: String
        get() = if (chinese) "日期" else "Date"
    val prevMonth: String
        get() = if (chinese) "上个月" else "Previous month"
    val nextMonth: String
        get() = if (chinese) "下个月" else "Next month"
    val confirmAction: String
        get() = if (chinese) "确定" else "OK"
    val addLedgerEntry: String
        get() = if (chinese) "记一笔" else "Add entry"
    val editLedgerEntry: String
        get() = if (chinese) "编辑账目" else "Edit entry"
    val trackAverageCostOption: String
        get() = if (chinese) "计入平均成本" else "Track average cost"
    val trackAverageCostHint: String
        get() = if (chinese) {
            "开启后自动折算日均（¥/天）与月均（¥/月）成本，支持一次性买断大件与周期性订阅。"
        } else {
            "Calculates average daily (¥/day) and monthly (¥/mo) cost for one-time assets or recurring subscriptions."
        }
    val costModeOneTime: String
        get() = if (chinese) "一次性资产" else "One-time asset"
    val costModePeriodic: String
        get() = if (chinese) "周期订阅" else "Subscription"
    val entryTitleLabel: String
        get() = if (chinese) "名称（选填，留空使用分类名）" else "Title (optional, defaults to category)"
    val entryAmountLabel: String
        get() = if (chinese) "金额" else "Amount"
    val salvageValueLabel: String
        get() = if (chinese) "预估残值 / 二手回血（选填，与金额同币种）" else "Estimated salvage value (optional, same currency)"
    val targetDaysLabel: String
        get() = if (chinese) "预计使用天数（选填，留空按实际天数摊销）" else "Target lifespan days (optional)"
    val billingCycleLabel: String
        get() = if (chinese) "计费周期" else "Billing cycle"
    val cycleWeekly: String
        get() = if (chinese) "按周（7天）" else "Weekly (7d)"
    val cycleMonthly: String
        get() = if (chinese) "按月" else "Monthly"
    val cycleQuarterly: String
        get() = if (chinese) "按季（3个月）" else "Quarterly"
    val cycleSemiAnnual: String
        get() = if (chinese) "每半年（6个月）" else "Semi-annual (6 mo)"
    val cycleYearly: String
        get() = if (chinese) "按年（12个月）" else "Yearly"
    val cycleCustomChip: String
        get() = if (chinese) "自定义" else "Custom"
    val customDaysInputLabel: String
        get() = if (chinese) "周期数" else "Interval count"
    val entryNoteLabel: String
        get() = if (chinese) "备注（选填）" else "Note (optional)"
    val expenseTypeLabel: String
        get() = if (chinese) "支出" else "Expense"
    val incomeTypeLabel: String
        get() = if (chinese) "收入" else "Income"
    val saveAction: String
        get() = if (chinese) "保存" else "Save"
    val cancelAction: String
        get() = if (chinese) "取消" else "Cancel"
    val deleteAction: String
        get() = if (chinese) "删除" else "Delete"
    val statusActiveInUse: String
        get() = if (chinese) "服役中" else "In use"
    val statusRetired: String
        get() = if (chinese) "已退役" else "Retired"
    val statusSubActive: String
        get() = if (chinese) "订阅中" else "Active"
    val statusSubStopped: String
        get() = if (chinese) "已停订" else "Stopped"
    val perDayUnit: String
        get() = if (chinese) "/天" else "/day"
    val perMonthUnit: String
        get() = if (chinese) "/月" else "/mo"
    val perYearUnit: String
        get() = if (chinese) "/年" else "/yr"
    val emptyCostItemsHint: String
        get() = if (chinese) {
            "暂无成本项。新建支出时开启「计入平均成本」，即可追踪大件折旧与订阅周期成本。"
        } else {
            "No cost items yet. Enable 'Track average cost' when adding an expense to track assets and subscriptions."
        }
    val moreOptions: String
        get() = if (chinese) "更多选项" else "More options"
    val backupExportJson: String
        get() = if (chinese) "导出备份（JSON）" else "Export backup (JSON)"
    val backupExportCsv: String
        get() = if (chinese) "导出表格（CSV）" else "Export spreadsheet (CSV)"
    val backupImport: String
        get() = if (chinese) "从文件导入" else "Import from file"
    val backupImportTitle: String
        get() = if (chinese) "导入备份" else "Import backup"
    val backupFileEntriesLabel: String
        get() = if (chinese) "文件内记录" else "Entries in file"
    val backupDateRangeLabel: String
        get() = if (chinese) "日期范围" else "Date range"
    val backupExportedAtLabel: String
        get() = if (chinese) "导出时间" else "Exported at"
    val backupNoRecords: String
        get() = if (chinese) "无记录" else "No records"
    val backupMergeAction: String
        get() = if (chinese) "合并" else "Merge"
    val backupReplaceAction: String
        get() = if (chinese) "替换…" else "Replace…"
    val backupReplaceConfirmTitle: String
        get() = if (chinese) "确认替换为备份内容" else "Replace with backup?"
    val backupExportFailed: String
        get() = if (chinese) "导出失败" else "Export failed"
    val backupInvalidFile: String
        get() = if (chinese) "文件不是有效的 Toolbox 账本备份" else "Not a valid Toolbox ledger backup"
    val backupNewerSchema: String
        get() = if (chinese) "备份来自更新版本的应用，请先升级" else "Backup comes from a newer app version; update first"
    val backupTooLarge: String
        get() = if (chinese) "文件过大（超过 20 MB）" else "File too large (over 20 MB)"
    val backupReadFailed: String
        get() = if (chinese) "无法读取文件" else "Could not read the file"
    val costModeNone: String
        get() = if (chinese) "不计成本" else "Not tracked"
    val manualRateSwitch: String
        get() = if (chinese) "手动输入汇率" else "Enter rate manually"
    val rateFetchOnSave: String
        get() = if (chinese) "保存时获取该日汇率" else "That day's rate will be fetched on save"
    val manualRateHint: String
        get() = if (chinese) "或开启「手动输入汇率」" else "or enable manual rate input"
    val manualRateInvalid: String
        get() = if (chinese) "汇率必须大于 0" else "Rate must be greater than 0"
    val fxGetRates: String
        get() = if (chinese) "获取汇率" else "Get rates"
    val fxRateDateLabel: String
        get() = if (chinese) "汇率日期" else "Rate date"
    val fxFetchedLabel: String
        get() = if (chinese) "获取时间" else "Fetched"
    val fxSourceLabel: String
        get() = if (chinese) {
            "欧洲央行参考汇率（Frankfurter），工作日更新"
        } else {
            "ECB reference rates (Frankfurter), updated on business days"
        }
    val fxNoRates: String
        get() = if (chinese) "尚未获取汇率" else "No rates yet"
    val fxNetworkError: String
        get() = if (chinese) "网络连接失败或超时" else "Network connection failed or timed out"
    val fxInvalidResponse: String
        get() = if (chinese) "汇率数据无法解析" else "Could not parse the rate data"
    val fxFromLabel: String
        get() = if (chinese) "源币种" else "From"
    val fxMoreChip: String
        get() = if (chinese) "更多…" else "More…"
    val fxPickCurrency: String
        get() = if (chinese) "选择币种" else "Pick currency"
    val csvHeaders: List<String>
        get() = if (chinese) {
            listOf(
                "日期", "类型", "标签", "名称", "金额", "币种",
                "汇率(CNY)", "折合人民币", "备注", "成本模式", "账户", "转入账户",
                "UUID",
            )
        } else {
            listOf(
                "Date", "Type", "Tags", "Title", "Amount", "Currency",
                "Rate (CNY)", "CNY amount", "Note", "Cost mode", "Account",
                "To account", "UUID",
            )
        }

    fun backupExported(count: Int): String =
        if (chinese) "已导出 $count 条记录" else "Exported $count entries"

    fun backupMergePreview(added: Int, updated: Int, unchanged: Int): String =
        if (chinese) {
            "合并：新增 $added，更新 $updated，不变 $unchanged"
        } else {
            "Merge: $added added, $updated updated, $unchanged unchanged"
        }

    fun backupMergedDone(added: Int, updated: Int): String =
        if (chinese) "已合并：新增 $added，更新 $updated" else "Merged: $added added, $updated updated"

    fun backupReplaceConfirmBody(kept: Int, deleted: Int): String = if (chinese) {
        "本地账本将与备份文件完全一致：保留 $kept 条，删除本地 $deleted 条。WebDAV 同步后其他设备也会随之更新。"
    } else {
        "Your local ledger will match the backup exactly: $kept entries kept, $deleted local entries deleted. Other devices will pick this up on the next WebDAV sync."
    }

    fun backupReplacedDone(kept: Int, deleted: Int): String =
        if (chinese) {
            "已替换：保留 $kept 条，删除 $deleted 条"
        } else {
            "Replaced: $kept kept, $deleted deleted"
        }

    fun backupError(failure: BackupFailure): String = when (failure) {
        BackupFailure.INVALID_FILE -> backupInvalidFile
        BackupFailure.NEWER_SCHEMA -> backupNewerSchema
        BackupFailure.TOO_LARGE -> backupTooLarge
    }

    fun decimalsNotAllowed(code: String): String =
        if (chinese) "$code 金额不支持小数" else "$code amounts can't have decimals"

    fun rateLineLocked(code: String, rate: String, date: String): String = if (chinese) {
        "1 $code = $rate CNY · 汇率日期 $date"
    } else {
        "1 $code = $rate CNY · rate date $date"
    }

    fun rateLineManual(code: String, rate: String): String = if (chinese) {
        "1 $code = $rate CNY · 手动汇率"
    } else {
        "1 $code = $rate CNY · manual rate"
    }

    fun rateLineCached(code: String, rate: String, date: String): String = if (chinese) {
        "1 $code = $rate CNY · 缓存 $date"
    } else {
        "1 $code = $rate CNY · cached $date"
    }

    fun manualRateFieldLabel(code: String): String = "1 $code ="

    fun approxCny(cnyText: String): String = "≈ $cnyText"

    fun useCachedRate(date: String): String =
        if (chinese) "使用最近缓存汇率（$date）" else "Use latest cached rate ($date)"

    fun fxShowAll(count: Int): String =
        if (chinese) "显示全部 ($count)" else "Show all ($count)"

    fun fxError(failure: FxFailure, httpCode: Int?): String = when (failure) {
        FxFailure.NETWORK -> fxNetworkError
        FxFailure.HTTP_ERROR -> if (chinese) {
            "汇率服务返回错误${httpCode?.let { " $it" } ?: ""}"
        } else {
            "Rate service error${httpCode?.let { " $it" } ?: ""}"
        }
        FxFailure.INVALID_RESPONSE -> fxInvalidResponse
        FxFailure.ALL_SOURCES_FAILED -> fxAllSourcesFailed
    }

    fun fxSourceError(sourceId: String, failure: FxFailure, httpCode: Int?): String {
        val name = fxSourceName(sourceId).ifBlank { sourceId }
        return fxAttemptLine(name, fxError(failure, httpCode))
    }

    val webDavBackupMenu: String
        get() = if (chinese) "WebDAV 备份与同步" else "WebDAV backup & sync"
    val webDavSyncNow: String
        get() = if (chinese) "立即同步" else "Sync now"
    val webDavHelpText: String
        get() = if (chinese) {
            "请先在服务器上创建好同步文件夹，再粘贴它的 HTTPS 地址（例如 https://dav.jianguoyun.com/dav/Toolbox/；坚果云需使用「应用密码」而非登录密码）。同步会写入 ledger.json、带日期的 ledger-backup-*.json 快照（仅保留最新 10 份，供手动恢复）以及 ledger-backups.json 索引。"
        } else {
            "Create the folder on your server first, then paste its HTTPS URL (e.g. https://dav.jianguoyun.com/dav/Toolbox/ — Jianguoyun requires an app password, not your login password). Sync writes ledger.json, dated ledger-backup-*.json snapshots (newest 10 kept, for manual recovery), and the ledger-backups.json index."
        }
    val webDavFolderUrlLabel: String
        get() = if (chinese) "文件夹地址（HTTPS）" else "Folder URL (HTTPS)"
    val webDavUsernameLabel: String
        get() = if (chinese) "用户名" else "Username"
    val webDavPasswordLabel: String
        get() = if (chinese) "密码 / 应用密码" else "Password / app password"
    val webDavShowPassword: String
        get() = if (chinese) "显示密码" else "Show password"
    val webDavHidePassword: String
        get() = if (chinese) "隐藏密码" else "Hide password"
    val webDavTestConnection: String
        get() = if (chinese) "测试连接" else "Test connection"
    val webDavTestOk: String
        get() = if (chinese) "连接成功" else "Connection OK"
    val webDavConfigSaved: String
        get() = if (chinese) "WebDAV 设置已保存" else "WebDAV settings saved"
    val webDavStatusTitle: String
        get() = if (chinese) "同步状态" else "Sync status"
    val webDavLastSyncLabel: String
        get() = if (chinese) "上次同步时间" else "Last sync"
    val webDavLastResultLabel: String
        get() = if (chinese) "上次结果" else "Last result"
    val webDavClearButton: String
        get() = if (chinese) "清除 WebDAV 设置" else "Clear WebDAV settings"
    val webDavClearConfirmText: String
        get() = if (chinese) {
            "仅清除本机保存的地址、用户名与密码，不会删除服务器上的文件。确定清除？"
        } else {
            "Only removes the locally saved URL, username and password; server files are untouched. Clear?"
        }
    val webDavClearAction: String
        get() = if (chinese) "清除" else "Clear"

    fun webDavError(failure: WebDavFailure, httpCode: Int? = null): String = when (failure) {
        WebDavFailure.INVALID_URL ->
            if (chinese) "请输入以 https:// 开头的文件夹地址" else "Enter a folder URL starting with https://"
        WebDavFailure.UNAUTHORIZED ->
            if (chinese) {
                "用户名或密码错误（坚果云请使用应用密码）"
            } else {
                "Wrong username or password (for Jianguoyun use an app password)"
            }
        WebDavFailure.FOLDER_MISSING ->
            if (chinese) "文件夹不存在，请先在服务器上创建" else "Folder missing on the server — create it first"
        WebDavFailure.PRECONDITION_FAILED ->
            if (chinese) {
                "远端文件正被其他设备修改，请稍后重试"
            } else {
                "Remote file is being changed by another device; try again"
            }
        WebDavFailure.VERSION_UNAVAILABLE ->
            if (chinese) {
                "服务器没有提供可用于安全同步的强 ETag，已停止写入"
            } else {
                "Server did not provide a strong ETag; sync stopped before writing"
            }
        WebDavFailure.HTTP_ERROR ->
            if (chinese) {
                "服务器返回错误${httpCode?.let { " $it" } ?: ""}"
            } else {
                "Server returned an error${httpCode?.let { " $it" } ?: ""}"
            }
        WebDavFailure.NETWORK ->
            if (chinese) "网络连接失败或超时" else "Network connection failed or timed out"
        WebDavFailure.TLS ->
            if (chinese) "HTTPS 证书校验失败" else "HTTPS certificate check failed"
        WebDavFailure.INVALID_REMOTE_FILE ->
            if (chinese) {
                "远端 ledger.json 无法解析，已停止同步以免覆盖"
            } else {
                "Remote ledger.json could not be parsed; sync stopped to avoid overwriting it"
            }
        WebDavFailure.NEWER_SCHEMA ->
            if (chinese) "远端数据来自更新版本的应用，请先升级" else "Remote data comes from a newer app version; update first"
        WebDavFailure.PASSWORD_UNAVAILABLE ->
            if (chinese) "已保存的密码无法读取，请重新输入" else "Saved password can't be read; please re-enter it"
    }

    fun webDavSyncSuccess(
        total: Int,
        pulled: Int,
        pushed: Int,
        snapshotName: String?,
    ): String = if (chinese) {
        "同步完成：共 $total 条 · 拉取 $pulled · 推送 $pushed" +
            (snapshotName?.let { " · 快照 $it" } ?: "")
    } else {
        "Sync complete: $total total · $pulled pulled · $pushed pushed" +
            (snapshotName?.let { " · snapshot $it" } ?: "")
    }

    fun webDavSnapshotFailed(message: String): String =
        if (chinese) "快照写入失败：$message" else "Snapshot upload failed: $message"

    fun formatLedgerDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofPattern(
                    if (chinese) "yyyy年M月d日 HH:mm" else "MMM d, yyyy HH:mm",
                    if (chinese) Locale.CHINA else Locale.US,
                ),
            )

    fun ledgerCategory(category: LedgerCategory): String = when (category) {
        LedgerCategory.FOOD -> if (chinese) "餐饮" else "Food"
        LedgerCategory.TRANSPORT -> if (chinese) "交通" else "Transport"
        LedgerCategory.SHOPPING -> if (chinese) "购物" else "Shopping"
        LedgerCategory.HOUSING -> if (chinese) "居住" else "Housing"
        LedgerCategory.ELECTRONICS -> if (chinese) "数码" else "Electronics"
        LedgerCategory.ENTERTAINMENT -> if (chinese) "娱乐" else "Entertainment"
        LedgerCategory.HEALTH -> if (chinese) "健康" else "Health"
        LedgerCategory.EDUCATION -> if (chinese) "教育" else "Education"
        LedgerCategory.SALARY -> if (chinese) "工资" else "Salary"
        LedgerCategory.BONUS -> if (chinese) "奖金" else "Bonus"
        LedgerCategory.INVESTMENT -> if (chinese) "投资" else "Investment"
        LedgerCategory.OTHER -> if (chinese) "其它" else "Other"
    }

    fun formatLedgerMonth(month: YearMonth): String =
        month.format(
            DateTimeFormatter.ofPattern(
                if (chinese) "yyyy年M月" else "MMMM yyyy",
                if (chinese) Locale.CHINA else Locale.US,
            ),
        )

    fun formatLedgerDay(date: LocalDate): String =
        date.format(
            DateTimeFormatter.ofPattern(
                if (chinese) "M月d日 EEE" else "EEE, MMM d",
                if (chinese) Locale.CHINA else Locale.US,
            ),
        )

    fun formatLedgerWeekday(date: LocalDate): String =
        date.format(
            DateTimeFormatter.ofPattern(
                "EEE",
                if (chinese) Locale.CHINA else Locale.US,
            ),
        )

    fun costItemsCount(assets: Int, subscriptions: Int): String =
        if (chinese) {
            "长期资产 $assets 项 · 订阅 $subscriptions 项"
        } else {
            "$assets asset${if (assets == 1) "" else "s"} · $subscriptions subscription${if (subscriptions == 1) "" else "s"}"
        }

    fun usedDaysTarget(usedDays: Int, targetDays: Int): String =
        if (chinese) "已用 $usedDays / 目标 $targetDays 天" else "Used $usedDays of $targetDays target days"

    fun cycleName(
        cycle: BillingCycle,
        customDays: Int,
        customUnit: CycleUnit = CycleUnit.DAYS,
    ): String = when (cycle) {
        BillingCycle.WEEKLY -> cycleWeekly
        BillingCycle.MONTHLY -> cycleMonthly
        BillingCycle.QUARTERLY -> cycleQuarterly
        BillingCycle.SEMI_ANNUAL -> cycleSemiAnnual
        BillingCycle.YEARLY -> cycleYearly
        BillingCycle.CUSTOM_DAYS -> if (chinese) {
            "每 $customDays${cycleUnitName(customUnit)}"
        } else {
            "Every $customDays ${cycleUnitName(customUnit)}"
        }
    }

    fun cycleUnitName(unit: CycleUnit): String = when (unit) {
        CycleUnit.DAYS -> if (chinese) "天" else "days"
        CycleUnit.WEEKS -> if (chinese) "周" else "weeks"
        CycleUnit.MONTHS -> if (chinese) "月" else "months"
        CycleUnit.YEARS -> if (chinese) "年" else "years"
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
    val sharedUidTraffic: String
        get() = if (chinese) "共享 UID，无法归属" else "Shared UID: cannot attribute"
    val sharedUidTrafficNote: String
        get() = if (chinese) {
            "总流量包含无法归属到单个应用的 UID 流量；这些流量不计入应用和分类明细。"
        } else {
            "Totals include UID traffic that cannot be assigned to one app; app and category details exclude it."
        }
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
        ToolboxModule.LEDGER -> if (chinese) "日常收支、大件摊销与订阅日均成本" else "Expenses, asset amortization & subscriptions"
        ToolboxModule.FX -> if (chinese) "欧洲央行参考汇率" else "ECB reference rates"
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

    val cpuCoreName: String
        get() = if (chinese) "CPU 核心" else "CPU core"
    val currentFrequencyLabel: String
        get() = if (chinese) "实时主频" else "Current frequency"
    val cpuMaxFrequency: String
        get() = if (chinese) "最高主频" else "Max frequency"

    fun usageTimeRange(range: UsageTimeRange): String =
        if (chinese) range.nameZh else range.nameEn

    fun usageSortMode(mode: UsageSortMode): String =
        if (chinese) mode.nameZh else mode.nameEn

    fun appCategory(category: AppCategory): String =
        if (chinese) category.nameZh else category.nameEn

    fun duration(millis: Long): String {
        if (millis < 60_000L) {
            val secs = millis / 1_000L
            return if (chinese) {
                if (secs > 0) "$secs 秒" else "< 1 秒"
            } else {
                if (secs > 0) "$secs s" else "< 1 s"
            }
        }
        val mins = millis / 60_000L
        val hours = mins / 60L
        val remMins = mins % 60L
        return if (chinese) {
            if (hours > 0) "${hours}小时 ${remMins}分" else "$remMins 分钟"
        } else {
            if (hours > 0) "${hours}h ${remMins}m" else "$remMins min"
        }
    }

    fun appUsageCopyText(
        label: String,
        packageName: String,
        category: AppCategory,
        screenDuration: String,
        cellular: String,
        wifi: String,
        total: String,
        lastUsed: String,
    ): String = if (chinese) {
        buildString {
            appendLine("应用：$label ($packageName)")
            appendLine("分类：${category.emoji} ${category.nameZh}")
            appendLine("屏幕使用时长：$screenDuration")
            appendLine("移动蜂窝流量：$cellular")
            appendLine("WLAN 流量：$wifi")
            appendLine("总消耗流量：$total")
            append("最后使用时间：$lastUsed")
        }
    } else {
        buildString {
            appendLine("App: $label ($packageName)")
            appendLine("Category: ${category.emoji} ${category.nameEn}")
            appendLine("Screen time: $screenDuration")
            appendLine("Mobile data: $cellular")
            appendLine("Wi-Fi data: $wifi")
            appendLine("Total data: $total")
            append("Last used: $lastUsed")
        }
    }

    fun moonPhaseName(phase: MoonPhase): String =
        if (chinese) phase.nameZh else phase.nameEn

    fun bodyName(body: CelestialBodyVisibility): String =
        if (chinese) body.nameZh else body.nameEn

    fun compassDirection(azimuth: Double): String {
        val norm = (azimuth % 360 + 360) % 360
        val index = (((norm + 22.5) % 360) / 45).toInt()
        return if (chinese) {
            listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")[index]
        } else {
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[index]
        }
    }

    val legendVisiblePeriod: String
        get() = if (chinese) "可见时段" else "Visible"
    val legendCurrentTime: String
        get() = if (chinese) "当前时刻" else "Now"
    val riseSetLabel: String
        get() = if (chinese) "升落" else "Rise/Set"
    val copyLabelRiseSet: String
        get() = if (chinese) "升落时刻" else "Rise & set times"
    val copyLabelMaxAltitude: String
        get() = if (chinese) "最高仰角" else "Max altitude"
    val copyLabelAltAz: String
        get() = if (chinese) "当前高度方位" else "Current alt/az"
    val exportReadyToShare: String
        get() = if (chinese) "图片已生成，可分享" else "Image ready to share"
    val generatedByToolbox: String
        get() = if (chinese) "由 Toolbox 生成" else "Generated by Toolbox"
    val dateLabel: String
        get() = if (chinese) "日期" else "Date"

    val pendingSyncRecords: String
        get() = if (chinese) "待推送增量记录 (PENDING_PUSH)" else "Pending sync records (PENDING_PUSH)"
    val oneTimeAssetMode: String
        get() = if (chinese) "一次性大件折旧" else "One-time asset"
    val subscriptionMode: String
        get() = if (chinese) "周期订阅" else "Subscription"
    val retireAction: String
        get() = if (chinese) "设为已退役" else "Retire"
    val reactivateAction: String
        get() = if (chinese) "恢复服役" else "Reactivate"
    val stopSubscriptionAction: String
        get() = if (chinese) "停订…" else "Stop…"
    val resumeSubscriptionAction: String
        get() = if (chinese) "恢复订阅" else "Resume"
    val editAction: String
        get() = if (chinese) "编辑" else "Edit"
    val lifespanActualDays: String
        get() = if (chinese) "按实际持有天数" else "Actual days held"
    val deleteEntryConfirm: String
        get() = if (chinese) {
            "删除后该账目将从列表移除，并在同步时作为删除标记保留。确定删除？"
        } else {
            "Delete this entry? It will be removed from the list and kept as a deletion marker for sync."
        }
    val amountInvalidError: String
        get() = if (chinese) "请输入大于 0 的金额" else "Enter an amount greater than 0"
    fun lifespanYearsPreset(years: Int, days: Int): String =
        if (chinese) "${years}年(${days}天)" else "$years yr${if (years == 1) "" else "s"} (${days}d)"

    fun oneTimeHeldInfo(
        price: String,
        salvage: String?,
        daysHeld: Int,
        targetDays: Int?,
        actualDaily: String?,
    ): String = if (chinese) {
        buildString {
            append("购入价 $price")
            salvage?.let { append(" · 残值 $it") }
            append(" · 已持有 $daysHeld 天")
            if (targetDays != null) {
                append("（目标 $targetDays 天")
                actualDaily?.let { append("，至今实际 $it/天") }
                append("）")
            }
        }
    } else {
        buildString {
            append("Price $price")
            salvage?.let { append(" · Salvage $it") }
            append(" · Held ${daysHeld}d")
            if (targetDays != null) {
                append(" (target ${targetDays}d")
                actualDaily?.let { append(", actual $it/day") }
                append(")")
            }
        }
    }


    // Tags
    val manageTagsMenu: String
        get() = if (chinese) "管理标签" else "Manage tags"
    val tagFilterAction: String
        get() = if (chinese) "筛选" else "Filter"
    val filterMatchAny: String
        get() = if (chinese) "任一标签" else "Any tag"
    val filterMatchAll: String
        get() = if (chinese) "全部标签" else "All tags"
    val clearFilter: String
        get() = if (chinese) "清除" else "Clear"
    val filteredBadge: String
        get() = if (chinese) "已筛选" else "Filtered"
    val breakdownByTags: String
        get() = if (chinese) "按标签" else "By tag"
    val untaggedLabel: String
        get() = if (chinese) "未标记" else "Untagged"
    val multiTagNote: String
        get() = if (chinese) {
            "一笔记录有多个标签时会分别计入"
        } else {
            "Entries with multiple tags count under each of them"
        }
    val newTagChip: String
        get() = if (chinese) "＋ 新建标签" else "+ New tag"
    val tagNameLabel: String
        get() = if (chinese) "标签名" else "Tag name"
    val tagNameRequired: String
        get() = if (chinese) "请输入标签名" else "Enter a tag name"
    val tagNameDuplicate: String
        get() = if (chinese) "已有同名标签" else "A tag with this name already exists"
    val tagColorLabel: String
        get() = if (chinese) "颜色" else "Color"
    val tagEmojiLabel: String
        get() = if (chinese) "表情（选填，最多2字符）" else "Emoji (optional, max 2 chars)"
    val addTagAction: String
        get() = if (chinese) "添加" else "Add"
    fun tagEntriesCount(count: Int): String =
        if (chinese) "$count 条记录" else "$count entries"
    fun deleteTagConfirm(count: Int): String = if (chinese) {
        "该标签下有 $count 条记录，删除后这些记录将不再显示此标签。确定删除？"
    } else {
        "$count entries will no longer show this tag. Delete it?"
    }
    val tagsEmptyHint: String
        get() = if (chinese) "暂无标签" else "No tags yet"

    // Renewals & linked entries
    val pendingRenewalsTitle: String
        get() = if (chinese) "待确认的周期收支" else "Pending recurring items"
    fun renewalDatesSummary(count: Int, dates: String): String =
        "$count${if (chinese) " 次 · " else "x · "}$dates"
    val recordAction: String
        get() = if (chinese) "记录" else "Record"
    val recordAllAction: String
        get() = if (chinese) "全部记录" else "Record all"
    val skipAction: String
        get() = if (chinese) "跳过" else "Skip"
    val skipRenewalsConfirm: String
        get() = if (chinese) {
            "跳过后这些续费不会计入支出，也不会再提示。确定跳过？"
        } else {
            "Skipped renewals won't count as expenses and won't be asked again. Skip them?"
        }
    fun renewalsRecorded(count: Int): String =
        if (chinese) "已记录 $count 次续费" else "Recorded $count renewals"
    fun renewalRateMissing(count: Int): String = if (chinese) {
        "$count 次续费无法获取当日汇率"
    } else {
        "Couldn't fetch the day's rate for $count renewals"
    }
    fun useNearestCachedRate(date: String, source: String): String = if (chinese) {
        "使用最近缓存汇率（$date · $source）"
    } else {
        "Use nearest cached rate ($date · $source)"
    }
    val renewalTagLabel: String
        get() = if (chinese) "续费" else "Renewal"
    val saleTagLabel: String
        get() = if (chinese) "卖出" else "Sale"
    fun renewalOrdinalInfo(index: Int): String =
        if (chinese) "订阅续费 · 第 $index 次" else "Subscription renewal · #$index"
    fun saleLinkedInfo(parentTitle: String): String =
        if (chinese) "二手卖出 · 关联：$parentTitle" else "Resale · linked to $parentTitle"
    val recurringIncomeOption: String
        get() = if (chinese) "固定收入（周期性）" else "Recurring income"
    val fixedIncomeSection: String
        get() = if (chinese) "固定收入" else "Fixed income"
    fun incomeMinusCosts(monthly: String): String = if (chinese) {
        "固定收入 − 固定支出 = 每月 $monthly"
    } else {
        "Fixed income − fixed costs = $monthly/mo"
    }
    fun accumulatedPayments(count: Int, total: String): String = if (chinese) {
        "累计支付 $count 次 · 合计 $total"
    } else {
        "$count payments · $total total"
    }
    fun accumulatedIncome(count: Int, total: String): String = if (chinese) {
        "累计收款 $count 次 · 合计 $total"
    } else {
        "$count payments received · $total total"
    }
    val nextPaydayLabel: String
        get() = if (chinese) "下次收款" else "Next payday"
    fun nextRenewalLabel(date: String, daysUntil: Int): String = if (chinese) {
        "下次续费 $date（${daysUntil}天后）"
    } else {
        "Next renewal $date (in ${daysUntil}d)"
    }

    // Accounts & transfers
    val accountsTabLabel: String
        get() = if (chinese) "账户" else "Accounts"
    val defaultAccountName: String
        get() = if (chinese) "默认账户" else "Default account"
    val transferTypeLabel: String
        get() = if (chinese) "转账" else "Transfer"
    val adjustmentTypeLabel: String
        get() = if (chinese) "余额调整" else "Balance adjustment"
    val adjustmentEditorHint: String
        get() = if (chinese) {
            "由「校准余额」生成的差额，不计入收支，也不在明细中显示"
        } else {
            "Created by Reconcile; affects the account balance only and is " +
                "not listed under Transactions"
        }
    val accountFieldLabel: String
        get() = if (chinese) "账户" else "Account"
    val transferFromLabel: String
        get() = if (chinese) "转出账户" else "From"
    val transferToLabel: String
        get() = if (chinese) "转入账户" else "To"
    val sameAccountError: String
        get() = if (chinese) "转入与转出账户不能相同" else "Accounts must differ"
    fun accountAmountLabel(action: String, currency: String): String = if (chinese) {
        "账户${action}金额（$currency）"
    } else {
        "Account $action amount ($currency)"
    }
    val accountDebitWord: String
        get() = if (chinese) "扣款" else "debit"
    val accountCreditWord: String
        get() = if (chinese) "入账" else "credit"
    val totalAssetsLabel: String
        get() = if (chinese) "总资产" else "Total assets"
    val partialConversionNote: String
        get() = if (chinese) "部分账户未折算" else "Some accounts not converted"
    val newAccountAction: String
        get() = if (chinese) "＋ 新建账户" else "+ New account"
    val accountBalanceLabel: String
        get() = if (chinese) "余额" else "Balance"
    val reconcileAction: String
        get() = if (chinese) "校准余额" else "Reconcile"
    val reconcilePrompt: String
        get() = if (chinese) "输入账户的实际余额" else "Enter the actual balance"
    val balanceMatchesToast: String
        get() = if (chinese) "余额一致" else "Balance matches"
    val adjustmentTitleText: String
        get() = if (chinese) "余额调整" else "Balance adjustment"
    val archiveAction: String
        get() = if (chinese) "归档" else "Archive"
    val unarchiveAction: String
        get() = if (chinese) "取消归档" else "Unarchive"
    val archivedBadge: String
        get() = if (chinese) "已归档" else "Archived"
    val accountNameLabel: String
        get() = if (chinese) "账户名称" else "Account name"
    val accountCurrencyLabel: String
        get() = if (chinese) "币种" else "Currency"
    val openingBalanceLabel: String
        get() = if (chinese) "初始余额" else "Opening balance"
    val openingDateLabel: String
        get() = if (chinese) "期初日期" else "Opening date"
    val currencyLockedHasEntries: String
        get() = if (chinese) "已有记录，币种不可修改" else "Currency locked (has entries)"
    val accountDeleteBlockedHint: String
        get() = if (chinese) "该账户有关联记录，无法删除，可先归档" else "Has entries — archive instead of deleting"
    val accountTransactionsLabel: String
        get() = if (chinese) "交易明细" else "Transactions"
    val accountEmptyHint: String
        get() = if (chinese) "还没有账户，点下方新建" else "No accounts yet"
    fun deleteAccountConfirm(name: String): String =
        if (chinese) "删除账户「$name」？仅当它没有记录时可用。" else "Delete \"$name\"? Only when unused."
    fun totalAssetsText(amount: String, partial: Boolean): String = if (chinese) {
        "总资产 ≈ $amount" + if (partial) "（部分账户未折算）" else ""
    } else {
        "Total assets ≈ $amount" + if (partial) " (partial)" else ""
    }
    fun transferLine(from: String, to: String): String = "$from → $to"
    fun signedAmount(amount: String, negative: Boolean): String =
        (if (negative) "-" else "+") + amount

    // Tag management v2
    val tagLabel: String
        get() = if (chinese) "标签" else "Tags"
    fun tagRowCaption(count: Int, expenseText: String, incomeText: String?): String =
        if (chinese) {
            "$count 笔 · 累计 $expenseText" + (incomeText?.let { " · 收 $it" } ?: "")
        } else {
            "$count entries · $expenseText total" + (incomeText?.let { " · in $it" } ?: "")
        }
    val mergeIntoAction: String
        get() = if (chinese) "合并到…" else "Merge into…"
    fun mergeTagConfirm(from: String, to: String, count: Int): String =
        if (chinese) {
            "「$from」的 $count 笔记录将改为「$to」，「$from」将被删除"
        } else {
            "$count entries move from \"$from\" to \"$to\"; \"$from\" is deleted"
        }
    val mergeTargetLabel: String
        get() = if (chinese) "选择要合并到的标签" else "Merge into which tag?"
    val thisMonthLabel: String
        get() = if (chinese) "本月" else "This month"
    val totalLabel: String
        get() = if (chinese) "累计" else "All time"
    val recommendedLabel: String
        get() = if (chinese) "推荐" else "Suggestions"
    val manageLabel: String
        get() = if (chinese) "管理" else "Manage"
    val manageTagsChip: String
        get() = if (chinese) "管理标签" else "Tags"
    fun selectedCountLabel(count: Int): String =
        if (chinese) "已选 $count" else "$count selected"
    val addTagsAction: String
        get() = if (chinese) "添加标签" else "Add tag"
    val removeTagsAction: String
        get() = if (chinese) "移除标签" else "Remove tag"
    val copyAction: String
        get() = if (chinese) "复制" else "Copy"
    val tagGroupTech: String
        get() = if (chinese) "科技与订阅" else "Tech & subscriptions"
    val tagGroupLife: String
        get() = if (chinese) "生活" else "Daily life"
    val tagGroupIncome: String
        get() = if (chinese) "收入" else "Income"

    /** Preset tag suggestions (emoji, name) by group — localized. */
    val tagPresets: List<Pair<String, List<Pair<String, String>>>>
        get() = if (chinese) {
            listOf(
                "科技与订阅" to listOf(
                    "🖥️" to "VPS", "🤖" to "AI", "🌐" to "网络", "☁️" to "云服务",
                    "🔑" to "域名", "📡" to "话费流量", "💻" to "软件", "📱" to "数码",
                    "🎧" to "外设", "🎬" to "视频会员", "🎵" to "音乐", "🎮" to "游戏",
                ),
                "生活" to listOf(
                    "🍜" to "餐饮", "☕" to "咖啡", "🛒" to "日用", "🛍️" to "购物",
                    "🏠" to "房租", "💡" to "水电", "🚇" to "交通", "🚗" to "汽车",
                    "✈️" to "旅行", "💊" to "医疗", "📚" to "学习", "👕" to "服饰",
                    "🐱" to "宠物", "🎁" to "礼物",
                ),
                "收入" to listOf(
                    "💼" to "工资", "💰" to "奖金", "📈" to "投资", "🧧" to "红包",
                    "🪙" to "副业",
                ),
            )
        } else {
            listOf(
                "Tech & subscriptions" to listOf(
                    "🖥️" to "VPS", "🤖" to "AI", "🌐" to "Network", "☁️" to "Cloud",
                    "🔑" to "Domain", "📡" to "Phone plan", "💻" to "Software",
                    "📱" to "Gadgets", "🎧" to "Peripherals", "🎬" to "Streaming",
                    "🎵" to "Music", "🎮" to "Games",
                ),
                "Daily life" to listOf(
                    "🍜" to "Food", "☕" to "Coffee", "🛒" to "Essentials",
                    "🛍️" to "Shopping", "🏠" to "Rent", "💡" to "Utilities",
                    "🚇" to "Transit", "🚗" to "Car", "✈️" to "Travel",
                    "💊" to "Health", "📚" to "Learning", "👕" to "Clothes",
                    "🐱" to "Pets", "🎁" to "Gifts",
                ),
                "Income" to listOf(
                    "💼" to "Salary", "💰" to "Bonus", "📈" to "Investing",
                    "🧧" to "Red envelope", "🪙" to "Side job",
                ),
            )
        }

    /** Emoji-only quick picks for account emoji fields. */
    val accountEmojiPresets: List<String>
        get() = listOf("🏦", "💳", "💴", "💵", "💶", "👛", "🐷", "📱", "🪙", "💰")

    fun monthBalanceLabel(netText: String): String =
        if (chinese) "本月结余" else "Net this month"

    fun inOutCaption(expenseText: String, incomeText: String): String =
        if (chinese) "支 $expenseText · 收 $incomeText" else "Out $expenseText · In $incomeText"

    // Disposal
    val endUseAction: String
        get() = if (chinese) "结束使用…" else "End use…"
    val sellOption: String
        get() = if (chinese) "卖出" else "Sell"
    val scrapOption: String
        get() = if (chinese) "报废 · 停用" else "Scrap · retire"
    val disposalDateLabel: String
        get() = if (chinese) "结束日期" else "End date"
    val saleAmountLabel: String
        get() = if (chinese) "卖出金额" else "Sale amount"
    fun soldLine(date: String, amount: String): String =
        if (chinese) "已卖出 $date · $amount" else "Sold $date · $amount"
    fun scrappedLine(date: String): String =
        if (chinese) "已报废 $date" else "Scrapped $date"
    fun soldTitlePrefix(title: String): String =
        if (chinese) "卖出：$title" else "Sold: $title"
    val undoDisposalAction: String
        get() = if (chinese) "撤销" else "Undo"
    val undoDisposalConfirm: String
        get() = if (chinese) {
            "撤销后资产恢复在用，卖出记录会被删除。确定撤销？"
        } else {
            "Undo restores the asset; the sale record is removed. Continue?"
        }
    fun disposalPreview(days: Int, finalCost: String, daily: String): String = if (chinese) {
        "持有 $days 天 · 最终成本 $finalCost · 日均 $daily"
    } else {
        "Held $days days · final cost $finalCost · $daily/day"
    }
    val stopSubscriptionTitle: String
        get() = if (chinese) "停订日期" else "Stop date"
    fun stoppedOnLine(date: String): String =
        if (chinese) "已停订 $date" else "Stopped $date"

    // FX sources
    fun fxSourceName(sourceId: String?): String = when (sourceId) {
        "frankfurter" -> if (chinese) "Frankfurter（欧洲央行）" else "Frankfurter (ECB)"
        "ecb" -> if (chinese) "欧洲央行官网" else "ECB website"
        "currency-api" -> if (chinese) {
            "currency-api（社区数据）"
        } else {
            "currency-api (community)"
        }
        "manual" -> if (chinese) "手动输入" else "Manual"
        else -> ""
    }
    val fxFallbackNote: String
        get() = if (chinese) "备用来源" else "Fallback source"
    val fxCommunityNote: String
        get() = if (chinese) {
            "数值可能与欧洲央行略有差异"
        } else {
            "Values may differ slightly from ECB rates"
        }
    fun fxAttemptLine(source: String, reason: String): String = "$source：$reason"
    val fxAllSourcesFailed: String
        get() = if (chinese) "所有汇率来源均不可用" else "All rate sources failed"
    val csvTagsHeader: String
        get() = if (chinese) "标签" else "Tags"

    fun averageCostPreview(daily: String, monthly: String, yearly: String): String =
        if (chinese) {
            "平均成本折算：$daily$perDayUnit  ·  $monthly$perMonthUnit  ·  $yearly$perYearUnit"
        } else {
            "Average cost: $daily$perDayUnit  ·  $monthly$perMonthUnit  ·  $yearly$perYearUnit"
        }
}
