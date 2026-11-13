package com.example.toolbox.ui

import com.example.toolbox.usage.AppCategory
import com.example.toolbox.usage.UsageSortMode
import com.example.toolbox.usage.UsageTimeRange

val ToolboxStrings.categoryBreakdownMonthTitle: String
    get() = if (chinese) "本月实际支出分类" else "Actual outlays by category"

val ToolboxStrings.appUsage: String
    get() = if (chinese) "应用与流量统计" else "App Usage & Data"

val ToolboxStrings.usagePermissionTitle: String
    get() = if (chinese) "使用情况访问权限" else "Usage access permission"

val ToolboxStrings.usagePermissionDescription: String
    get() = if (chinese) {
        "用于读取各应用的前台屏幕使用时间与分网段（蜂窝/WLAN）流量消耗，并汇总热点、USB、蓝牙或 P2P 共享给其他设备的流量。数据由系统底层记录，完全离线计算，零后台耗电。"
    } else {
        "Used to read screen time and Cellular/Wi-Fi traffic per app, plus traffic shared with other devices over hotspot, USB, Bluetooth, or P2P. Calculated offline from OS logs with zero background battery usage."
    }

val ToolboxStrings.openUsageSettings: String
    get() = if (chinese) "前往系统设置授权" else "Grant in System Settings"

val ToolboxStrings.totalScreenTime: String
    get() = if (chinese) "总屏幕时长" else "Total Screen Time"

val ToolboxStrings.totalCellularData: String
    get() = if (chinese) "移动流量" else "Mobile Data"

val ToolboxStrings.totalWifiData: String
    get() = if (chinese) "WLAN 流量" else "Wi-Fi Data"

val ToolboxStrings.totalTraffic: String
    get() = if (chinese) "总消耗流量" else "Total Traffic"

val ToolboxStrings.sharedUidTraffic: String
    get() = if (chinese) "共享 UID，无法归属" else "Shared UID: cannot attribute"

val ToolboxStrings.sharedNetworkData: String
    get() = if (chinese) "共享网络流量" else "Tethered / P2P data"

val ToolboxStrings.sharedNetworkDataNote: String
    get() = if (chinese) {
        "包含热点、USB、蓝牙或 P2P 共享给其他设备的流量，无法归属到本机单个应用；部分厂商或机型可能不公开 P2P 本地侧计数。"
    } else {
        "Includes traffic shared with other devices over hotspot, USB, Bluetooth, or P2P; it cannot be assigned to one local app. Some vendors may not expose the P2P local-side counter."
    }

val ToolboxStrings.sharedUidTrafficNote: String
    get() = if (chinese) {
        "总流量包含无法归属到单个应用的 UID 流量；这些流量不计入应用和分类明细。"
    } else {
        "Totals include UID traffic that cannot be assigned to one app; app and category details exclude it."
    }

val ToolboxStrings.categoryBreakdown: String
    get() = if (chinese) "分类时间分布" else "Category Breakdown"

val ToolboxStrings.noUsageData: String
    get() = if (chinese) "该时间段内无应用使用与流量记录" else "No usage records found for this period"

fun ToolboxStrings.usageTimeRange(range: UsageTimeRange): String =
    if (chinese) range.nameZh else range.nameEn

fun ToolboxStrings.usageSortMode(mode: UsageSortMode): String =
    if (chinese) mode.nameZh else mode.nameEn

fun ToolboxStrings.appCategory(category: AppCategory): String =
    if (chinese) category.nameZh else category.nameEn

fun ToolboxStrings.duration(millis: Long): String {
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

fun ToolboxStrings.appUsageCopyText(
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
