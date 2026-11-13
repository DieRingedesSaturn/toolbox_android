package com.example.toolbox.ui

import com.example.toolbox.location.LocationReadStatus
import com.example.toolbox.location.MockLocationPhase
import com.example.toolbox.location.MockLocationFailure

val ToolboxStrings.mockLocationTitle: String
    get() = if (chinese) "模拟定位" else "Mock location"

val ToolboxStrings.mockLocationIpButton: String
    get() = if (chinese) "IP 位置" else "From IP"

val ToolboxStrings.mockLocationPickOnMap: String
    get() = if (chinese) "在地图上选择" else "Pick on map"

val ToolboxStrings.mockLocationMapButton: String
    get() = if (chinese) "地图" else "Map"

val ToolboxStrings.mockLocationMapTapToPick: String
    get() = if (chinese) "点按地图选择位置" else "Tap the map to pick a location"

val ToolboxStrings.mockLocationMapLoading: String
    get() = if (chinese) "正在加载地图…" else "Loading map…"

val ToolboxStrings.mockLocationMapFailed: String
    get() = if (chinese) "地图未能加载。请检查网络后重试，或返回手填坐标。" else
        "The map could not load. Check the network and retry, or go back and enter coordinates."

val ToolboxStrings.mockLocationMapRetry: String
    get() = if (chinese) "重试" else "Retry"

val ToolboxStrings.mockLocationMapConfirm: String
    get() = if (chinese) "使用此位置" else "Use this location"

val ToolboxStrings.mockLocationMapAttribution: String
    get() = if (chinese) "地图数据 © OpenStreetMap 贡献者 · OpenFreeMap" else
        "Map data © OpenStreetMap contributors · OpenFreeMap"

val ToolboxStrings.mockLocationConfigure: String
    get() = if (chinese) "设置模拟位置" else "Configure mock location"

val ToolboxStrings.mockLocationDeveloperSettings: String
    get() = if (chinese) "打开开发者选项" else "Open Developer options"

val ToolboxStrings.mockLocationAccessReady: String
    get() = if (chinese) "已选 Toolbox 为模拟位置应用" else "Toolbox is selected as the mock location app"

val ToolboxStrings.mockLocationAccessNeeded: String
    get() = if (chinese) "尚未选中 Toolbox，请先在开发者选项中选择" else "Select Toolbox in Developer options first"

val ToolboxStrings.mockLocationServicesNeeded: String
    get() = if (chinese) "请开启系统位置开关" else "Turn on system location"

val ToolboxStrings.mockLocationAccuracy: String
    get() = if (chinese) "精度 m" else "Acc. m"

val ToolboxStrings.mockLatitudeLabel: String
    get() = if (chinese) "纬度" else "Lat"

val ToolboxStrings.mockLongitudeLabel: String
    get() = if (chinese) "经度" else "Lon"

val ToolboxStrings.mockLocationDuration: String
    get() = if (chinese) "自动停止时长" else "Stop automatically after"

fun ToolboxStrings.mockLocationMinutes(minutes: Int): String = if (chinese) "${minutes}分" else "${minutes}m"

val ToolboxStrings.mockLocationStart: String
    get() = if (chinese) "开始模拟" else "Start mock location"

val ToolboxStrings.mockLocationStop: String
    get() = if (chinese) "停止模拟" else "Stop mock location"

val ToolboxStrings.mockLocationCleanup: String
    get() = if (chinese) "清理上次模拟" else "Clean up previous session"

val ToolboxStrings.mockLocationCleanupHint: String
    get() = if (chinese) "上次模拟可能未清理干净" else "Previous session may still need cleanup"

val ToolboxStrings.mockLocationDataWarning: String
    get() = if (chinese) "当前读到的是模拟位置，不代表设备实际所在位置。" else
        "This is a mock position, not the device's actual location."

val ToolboxStrings.mockLocationInvalid: String
    get() = if (chinese) "坐标或精度无效" else "Invalid coordinates or accuracy"

val ToolboxStrings.mockLocationIpFailed: String
    get() = if (chinese) "未取得有效 IP 坐标，请重试或手填；已有坐标已保留。" else "No valid IP coordinates. Retry or enter manually; existing fields are kept."

val ToolboxStrings.mockLocationNotificationNeeded: String
    get() = if (chinese) "请允许通知，以便在其他应用中看到和停止模拟" else
        "Allow notifications so you can see and stop the session from other apps"

val ToolboxStrings.mockLocationNotificationSettings: String
    get() = if (chinese) "打开通知设置" else "Open notification settings"

val ToolboxStrings.mockLocationRunning: String
    get() = if (chinese) "正在提供模拟位置" else "Providing mock location"

val ToolboxStrings.mockLocationOn: String
    get() = if (chinese) "模拟定位已开启" else "Mock location is on"

val ToolboxStrings.mockLocationEndsAt: String
    get() = if (chinese) "预计结束时间" else "Scheduled end"

fun ToolboxStrings.mockLocationNotification(coordinates: String, remainingMinutes: Int): String = if (chinese) {
    "$coordinates · 还剩 $remainingMinutes 分钟"
} else {
    "$coordinates · $remainingMinutes min left"
}

fun ToolboxStrings.mockLocationPhase(phase: MockLocationPhase): String = when (phase) {
    MockLocationPhase.STOPPED -> if (chinese) "模拟未运行" else "Mock location is stopped"
    MockLocationPhase.STARTING -> if (chinese) "正在启用测试定位源…" else "Starting test providers…"
    MockLocationPhase.RUNNING -> mockLocationRunning
    MockLocationPhase.STOPPING -> if (chinese) "正在停止并清理…" else "Stopping and cleaning up…"
    MockLocationPhase.ERROR -> if (chinese) "模拟已停止，发生错误" else "Session stopped after an error"
    MockLocationPhase.CLEANUP_REQUIRED -> if (chinese) "需要清理" else "Cleanup needed"
}

fun ToolboxStrings.mockLocationFailure(failure: MockLocationFailure): String = when (failure) {
    MockLocationFailure.ACCESS -> mockLocationAccessNeeded
    MockLocationFailure.SERVICES_DISABLED -> mockLocationServicesNeeded
    MockLocationFailure.NOTIFICATIONS -> mockLocationNotificationNeeded
    MockLocationFailure.PROVIDER -> if (chinese) "系统拒绝了测试定位源，请检查开发者选项后重试。" else "The system rejected a test provider. Check Developer options and retry."
    MockLocationFailure.START -> if (chinese) "无法启动模拟服务，请返回此页面重试。" else "Could not start the mock service. Return to this page and retry."
    MockLocationFailure.STOP -> if (chinese) "无法停止模拟服务，请重试停止或清理。" else "Could not stop the mock service. Retry stopping or cleaning up."
}

val ToolboxStrings.location: String
    get() = if (chinese) "位置" else "Location"

val ToolboxStrings.locationDescription: String
    get() = if (chinese) "查看当前设备的地理坐标、定位精度、卫星导航与海拔高程信息。" else "View current coordinates, accuracy, satellite positioning, and elevation."

val ToolboxStrings.locationPermissionTitle: String
    get() = if (chinese) "位置权限" else "Location permission"

val ToolboxStrings.locationPermissionDescription: String
    get() = if (chinese) {
        "用于读取当前设备的经纬度坐标与卫星导航信号。"
    } else {
        "Used to retrieve current coordinates and satellite positioning signals."
    }

val ToolboxStrings.requestLocationPermission: String
    get() = if (chinese) "请求位置权限" else "Request location permission"

val ToolboxStrings.locationPermissionGranted: String
    get() = if (chinese) "位置权限已允许" else "Location permission granted"

val ToolboxStrings.locationPermissionNotGranted: String
    get() = if (chinese) "尚未允许位置权限" else "Location permission not granted"

val ToolboxStrings.locationPrecision: String
    get() = if (chinese) "定位精度" else "Location precision"

val ToolboxStrings.preciseLocation: String
    get() = if (chinese) "精确" else "Precise"

val ToolboxStrings.approximateLocation: String
    get() = if (chinese) "大致" else "Approximate"

val ToolboxStrings.currentLocationTitle: String
    get() = if (chinese) "当前位置" else "Current location"

val ToolboxStrings.placeName: String
    get() = if (chinese) "地点" else "Place"

val ToolboxStrings.lookingUpPlace: String
    get() = if (chinese) "正在解析地点…" else "Looking up place…"

val ToolboxStrings.getCurrentLocation: String
    get() = if (chinese) "获取当前位置" else "Get current location"

val ToolboxStrings.locationLoading: String
    get() = if (chinese) "定位中…" else "Locating…"

val ToolboxStrings.locationOpenSettings: String
    get() = if (chinese) "打开位置设置" else "Open location settings"

val ToolboxStrings.latitude: String
    get() = if (chinese) "纬度" else "Latitude"

val ToolboxStrings.longitude: String
    get() = if (chinese) "经度" else "Longitude"

val ToolboxStrings.latitudeDms: String
    get() = if (chinese) "纬度（DMS）" else "Latitude (DMS)"

val ToolboxStrings.longitudeDms: String
    get() = if (chinese) "经度（DMS）" else "Longitude (DMS)"

val ToolboxStrings.accuracy: String
    get() = if (chinese) "水平精度" else "Horizontal accuracy"

val ToolboxStrings.verticalAccuracy: String
    get() = if (chinese) "垂直精度" else "Vertical accuracy"

val ToolboxStrings.altitude: String
    get() = if (chinese) "提供方高度" else "Provider altitude"

val ToolboxStrings.provider: String
    get() = if (chinese) "提供方" else "Provider"

val ToolboxStrings.locationTime: String
    get() = if (chinese) "定位时间" else "Location time"

val ToolboxStrings.locationDataUnavailable: String
    get() = if (chinese) "尚未成功获取位置数据。" else "No successful location data yet."

val ToolboxStrings.positioningSystemsTitle: String
    get() = if (chinese) "定位系统与提供方" else "Positioning systems & providers"

val ToolboxStrings.availableProviders: String
    get() = if (chinese) "可用定位提供方" else "Available providers"

val ToolboxStrings.enabledProviders: String
    get() = if (chinese) "已启用提供方" else "Enabled providers"

val ToolboxStrings.gnssConstellations: String
    get() = if (chinese) "已观测的卫星导航系统" else "Observed GNSS constellations"

val ToolboxStrings.gnssHardwareStatus: String
    get() = if (chinese) "GNSS 芯片硬件" else "GNSS hardware"

val ToolboxStrings.gnssHardwareAvailable: String
    get() = if (chinese) "已具备独立硬件支持" else "Hardware supported"

val ToolboxStrings.gnssHardwareUnavailable: String
    get() = if (chinese) "未检测到独立 GNSS 硬件" else "No dedicated GNSS hardware"

val ToolboxStrings.terrainElevationTitle: String
    get() = if (chinese) "地表地形海拔 (DEM)" else "Terrain elevation (DEM)"

val ToolboxStrings.queryTerrainElevation: String
    get() = if (chinese) "查询地表地形海拔" else "Query terrain elevation"

val ToolboxStrings.queryingTerrainElevation: String
    get() = if (chinese) "查询海拔中…" else "Querying elevation…"

val ToolboxStrings.terrainElevationSource: String
    get() = if (chinese) "高程数据源" else "Elevation source"

val ToolboxStrings.elevationDifferenceNote: String
    get() = if (chinese) {
        "GNSS 高度由卫星芯片几何计算；地表地形海拔由公开高程模型（DEM）按经纬度查询，二者互为参考。"
    } else {
        "GNSS altitude is calculated by GPS hardware; terrain elevation is queried from public DEM elevation models."
    }

val ToolboxStrings.gnssSkyViewTitle: String
    get() = if (chinese) "卫星雷达与信号强度" else "Satellite sky view & signals"

val ToolboxStrings.gnssObserving: String
    get() = if (chinese) "正在观测卫星…" else "Observing satellites…"

val ToolboxStrings.gnssStartObservation: String
    get() = if (chinese) "开启卫星观测" else "Start observation"

val ToolboxStrings.gnssStopObservation: String
    get() = if (chinese) "停止观测" else "Stop observation"

val ToolboxStrings.gnssObservationHint: String
    get() = if (chinese) {
        "实时观测天空中的卫星分布、信号强度与 3D 定位状态。"
    } else {
        "Real-time observation of satellite distribution, signal strength, and 3D fix status."
    }

val ToolboxStrings.gnssNoSatellitesHint: String
    get() = if (chinese) {
        "尚未搜到卫星信号。室内或密集建筑遮挡可能影响搜星，建议移步开阔室外。"
    } else {
        "No satellite signals detected yet. Indoor or obstructed environments may block signals; try moving outdoors."
    }

val ToolboxStrings.gnssTotalSatellites: String
    get() = if (chinese) "搜星总数" else "Visible satellites"

val ToolboxStrings.gnssUsedInFix: String
    get() = if (chinese) "参解卫星" else "Used in fix"

val ToolboxStrings.gnssSignalStrength: String
    get() = if (chinese) "信号强度 (C/N0)" else "Signal strength (C/N0)"

val ToolboxStrings.gnssSkyRadar: String
    get() = if (chinese) "星空雷达天空图" else "Sky view radar"

val ToolboxStrings.gnssElevation: String
    get() = if (chinese) "仰角" else "Elevation"

val ToolboxStrings.gnssAzimuth: String
    get() = if (chinese) "方位角" else "Azimuth"

val ToolboxStrings.gnssUsedTag: String
    get() = if (chinese) "已参解" else "Used"

val ToolboxStrings.gnssUnusedTag: String
    get() = if (chinese) "未锁定" else "Unused"

val ToolboxStrings.gnssFixStatus: String
    get() = if (chinese) "定位解状态" else "Fix status"

val ToolboxStrings.gnssFixStatusSearching: String
    get() = if (chinese) "搜星阶段（未完成 3D 定位）" else "Searching (No 3D fix)"

val ToolboxStrings.gnssFixStatusLocked: String
    get() = if (chinese) "已锁定 3D 定位" else "3D fix locked"

val ToolboxStrings.gnssIndoorFixHint: String
    get() = if (chinese) {
        "已捕获卫星信号但参解为 0，说明当前处于搜星/同步星历阶段。室内或窗边信号衰减严重无法完成 3D 定位解算，移至开阔室外可立即锁定。"
    } else {
        "Satellites detected but 0 used in fix: the chip is synchronizing ephemeris. Indoor attenuation prevents 3D fix calculation; move outdoors for a rapid fix."
    }

val ToolboxStrings.gnssLegendTitle: String
    get() = if (chinese) "图例" else "Legend"

val ToolboxStrings.gnssEarthCenter: String
    get() = if (chinese) "中心：手机观测点 (90° 天顶)" else "Center: Observer (90° Zenith)"

fun ToolboxStrings.locationStatus(status: LocationReadStatus): String = when (status) {
    LocationReadStatus.SUCCESS -> if (chinese) "定位成功" else "Location received"
    LocationReadStatus.PERMISSION_REQUIRED -> locationPermissionNotGranted
    LocationReadStatus.SERVICES_DISABLED -> if (chinese) "位置服务未开启" else "Location services are disabled"
    LocationReadStatus.TIMEOUT -> if (chinese) "定位超时，请重试" else "Location request timed out; try again"
    LocationReadStatus.UNAVAILABLE -> if (chinese) "位置服务不可用" else "Location service unavailable"
    LocationReadStatus.ERROR -> if (chinese) "定位失败" else "Location request failed"
}
