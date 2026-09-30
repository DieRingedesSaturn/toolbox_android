package com.example.toolbox.usage

enum class UsageTimeRange(val nameZh: String, val nameEn: String) {
    TODAY("今天", "Today"),
    YESTERDAY("昨天", "Yesterday"),
    PAST_7_DAYS("近7天", "Past 7 Days"),
    PAST_30_DAYS("近30天", "Past 30 Days"),
}

enum class UsageSortMode(val nameZh: String, val nameEn: String) {
    DURATION("使用时长", "Duration"),
    TOTAL_DATA("总流量", "Total Data"),
    CELLULAR_DATA("蜂窝流量", "Mobile Data"),
    WIFI_DATA("WLAN流量", "Wi-Fi Data"),
}

data class AppUsageItem(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val foregroundDurationMillis: Long,
    val lastTimeUsedMillis: Long,
    val wifiRxBytes: Long?,
    val wifiTxBytes: Long?,
    val cellularRxBytes: Long?,
    val cellularTxBytes: Long?,
    val sharedUidTraffic: Boolean = false,
) {
    val totalWifiBytes: Long? get() = wifiRxBytes?.let { rx -> wifiTxBytes?.let { rx + it } }
    val totalCellularBytes: Long? get() = cellularRxBytes?.let { rx -> cellularTxBytes?.let { rx + it } }
    val totalBytes: Long? get() = totalWifiBytes?.let { wifi -> totalCellularBytes?.let { wifi + it } }
    val hasUsage: Boolean get() = foregroundDurationMillis > 0 ||
        (totalWifiBytes ?: 0L) > 0L || (totalCellularBytes ?: 0L) > 0L
}

data class CategoryUsageSummary(
    val category: AppCategory,
    val totalDurationMillis: Long,
    val totalWifiBytes: Long?,
    val totalCellularBytes: Long?,
    val appCount: Int,
) {
    val totalBytes: Long? get() = totalWifiBytes?.let { wifi -> totalCellularBytes?.let { wifi + it } }
}

data class UsageReport(
    val timeRange: UsageTimeRange,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalScreenDurationMillis: Long,
    val totalWifiBytes: Long?,
    val totalCellularBytes: Long?,
    val hasUnattributedTraffic: Boolean,
    val items: List<AppUsageItem>,
    val categorySummaries: List<CategoryUsageSummary>,
) {
    val totalBytes: Long? get() = totalWifiBytes?.let { wifi -> totalCellularBytes?.let { wifi + it } }
}

data class UsageEventRecord(
    val eventType: Int,
    val packageName: String?,
    val timestamp: Long,
)

data class ProcessedUsageEvents(
    val measuredScreenDurationMillis: Long,
    val appDurations: Map<String, Long>,
    val appLastUsed: Map<String, Long>,
)
