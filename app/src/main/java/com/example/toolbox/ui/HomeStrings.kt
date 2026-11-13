package com.example.toolbox.ui

import com.example.toolbox.device.DeviceInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ToolboxStrings.moduleTitle(module: ToolboxModule): String = when (module) {
    ToolboxModule.HOME -> appName
    ToolboxModule.DEVICE -> device
    ToolboxModule.LOCATION -> location
    ToolboxModule.NETWORK -> network
    ToolboxModule.MONITOR -> monitor
    ToolboxModule.ASTRONOMY -> astronomy
    ToolboxModule.USAGE -> appUsage
    ToolboxModule.LEDGER -> ledger
    ToolboxModule.FX -> if (chinese) "汇率换算" else "Currency converter"
    ToolboxModule.TOTP -> if (chinese) "动态令牌 (TOTP)" else "TOTP Authenticator"
    ToolboxModule.PRIVACY -> if (chinese) "隐私" else "Privacy"
}

fun ToolboxStrings.moduleShortTitle(module: ToolboxModule): String = when (module) {
    ToolboxModule.HOME -> appName
    ToolboxModule.DEVICE -> if (chinese) "设备" else "Device"
    ToolboxModule.LOCATION -> if (chinese) "位置" else "Location"
    ToolboxModule.NETWORK -> if (chinese) "网络" else "Network"
    ToolboxModule.MONITOR -> if (chinese) "监控" else "Monitor"
    ToolboxModule.ASTRONOMY -> if (chinese) "天文" else "Astronomy"
    ToolboxModule.USAGE -> if (chinese) "统计" else "Usage"
    ToolboxModule.LEDGER -> if (chinese) "记账" else "Ledger"
    ToolboxModule.FX -> if (chinese) "汇率" else "FX"
    ToolboxModule.TOTP -> if (chinese) "令牌" else "TOTP"
    ToolboxModule.PRIVACY -> if (chinese) "隐私" else "Privacy"
}

val ToolboxStrings.homeGridReset: String
    get() = if (chinese) "重置布局" else "Reset layout"

val ToolboxStrings.homeOpenSettings: String
    get() = if (chinese) "打开设置" else "Open settings"

val ToolboxStrings.homeAccessibilityTitle: String
    get() = if (chinese) "首页" else "Home"

fun ToolboxStrings.homePageIndicator(page: Int, total: Int): String =
    if (chinese) "第 $page 页，共 $total 页" else "Page $page of $total"

val ToolboxStrings.homeGridResetMessage: String
    get() = if (chinese) {
        "清空自定义位置和留白，恢复默认排列？"
    } else {
        "Clear custom positions and blank rows and restore the default arrangement?"
    }

val ToolboxStrings.homeGridTopBlank: String
    get() = if (chinese) "顶部空行" else "Blank rows above"

val ToolboxStrings.homeGridBottomBlank: String
    get() = if (chinese) "底部空行" else "Blank rows below"

val ToolboxStrings.homeEditHint: String
    get() = if (chinese) {
        "拖动卡片调整位置，完成后点击右下角“完成”。"
    } else {
        "Drag cards to move them, then tap Done at the bottom right."
    }

val ToolboxStrings.homeMoveLeft: String
    get() = if (chinese) "左移" else "Move left"

val ToolboxStrings.homeMoveRight: String
    get() = if (chinese) "右移" else "Move right"

val ToolboxStrings.homeMoveUp: String
    get() = if (chinese) "上移" else "Move up"

val ToolboxStrings.homeMoveDown: String
    get() = if (chinese) "下移" else "Move down"

val ToolboxStrings.homeSummaryConfigAction: String
    get() = if (chinese) "主卡片" else "Card"

val ToolboxStrings.homeSummaryConfigTitle: String
    get() = if (chinese) "自定义首页主卡片" else "Customize summary card"

val ToolboxStrings.homeSummaryEnabledLabel: String
    get() = if (chinese) "显示首页主卡片" else "Show summary card"

val ToolboxStrings.homeSummaryCustomTextLabel: String
    get() = if (chinese) "自定义文字" else "Custom note"

val ToolboxStrings.homeSummaryCustomTextHint: String
    get() = if (chinese) "可选，例如备忘、提示或签名" else "Optional note, reminder, or tagline"

val ToolboxStrings.homeSummaryItemsLabel: String
    get() = if (chinese) "展示信息项" else "Visible items"

val ToolboxStrings.homeSummaryItemDeviceInfo: String
    get() = if (chinese) "设备型号与系统版本" else "Device model & OS version"

val ToolboxStrings.homeSummaryItemDate: String
    get() = if (chinese) "今日日期" else "Today's date"

val ToolboxStrings.homeSummaryItemBattery: String
    get() = if (chinese) "电池电量" else "Battery"

val ToolboxStrings.homeSummaryItemStorage: String
    get() = if (chinese) "存储空间" else "Storage"

val ToolboxStrings.homeSummaryItemMemory: String
    get() = if (chinese) "内存" else "Memory"

val ToolboxStrings.homeSummaryItemDisplay: String
    get() = if (chinese) "屏幕" else "Display"

val ToolboxStrings.homeSummaryItemNetwork: String
    get() = if (chinese) "网络连接" else "Network"

val ToolboxStrings.homeSummaryItemAssets: String
    get() = if (chinese) "总资产" else "Total assets"

val ToolboxStrings.homeSummaryItemMonthBalance: String
    get() = if (chinese) "本月结余" else "Month balance"

val ToolboxStrings.homeSummaryItemTodaySpend: String
    get() = if (chinese) "今日收支" else "Today's in/out"

internal fun ToolboxStrings.homeSummaryItemLabel(item: HomeSummaryItem): String = when (item) {
    HomeSummaryItem.DATE -> homeSummaryItemDate
    HomeSummaryItem.DEVICE_INFO -> homeSummaryItemDeviceInfo
    HomeSummaryItem.BATTERY -> homeSummaryItemBattery
    HomeSummaryItem.STORAGE -> homeSummaryItemStorage
    HomeSummaryItem.MEMORY -> homeSummaryItemMemory
    HomeSummaryItem.DISPLAY -> homeSummaryItemDisplay
    HomeSummaryItem.NETWORK -> homeSummaryItemNetwork
    HomeSummaryItem.TODAY_SPEND -> homeSummaryItemTodaySpend
    HomeSummaryItem.MONTH_BALANCE -> homeSummaryItemMonthBalance
    HomeSummaryItem.ASSETS -> homeSummaryItemAssets
}

fun ToolboxStrings.homeSummaryDate(date: LocalDate): String {
    val locale = if (chinese) Locale.CHINA else Locale.US
    val pattern = if (chinese) "M月d日 · EEEE" else "EEEE, MMM d"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

val ToolboxStrings.homeSummaryEmptyHint: String
    get() = if (chinese) "未选择任何内容，主卡片将自动隐藏。" else "Nothing selected; the card will stay hidden."

val ToolboxStrings.homeSettingsTitle: String
    get() = if (chinese) "首页" else "Home"

fun ToolboxStrings.moduleSummary(module: ToolboxModule, info: DeviceInfo?): String = when (module) {
    ToolboxModule.DEVICE -> info?.let { "${it.cpu.name} · ${it.cpu.cores} ${if (chinese) "核心" else "cores"}" } ?: loading
    ToolboxModule.LOCATION -> if (chinese) "坐标、海拔与卫星雷达" else "Coordinates, elevation, and sky view"
    ToolboxModule.NETWORK -> if (chinese) "本地连接与公网 IP" else "Local connection and public IP"
    ToolboxModule.MONITOR -> if (chinese) "悬浮窗实时性能监控" else "Floating live performance monitor"
    ToolboxModule.ASTRONOMY -> if (chinese) "行星可见时段与月相" else "Planet visibility & moon phase"
    ToolboxModule.USAGE -> if (chinese) "使用时长与分网流量" else "Screen time & cellular/Wi-Fi data"
    ToolboxModule.LEDGER -> if (chinese) "日常收支、大件摊销与订阅日均成本" else "Expenses, asset amortization & subscriptions"
    ToolboxModule.FX -> if (chinese) "欧洲央行参考汇率" else "ECB reference rates"
    ToolboxModule.TOTP -> if (chinese) "双因素动态验证码与加密备份" else "2FA dynamic codes & encrypted backup"
    ToolboxModule.PRIVACY -> if (chinese) "被系统藏起的 Google 与系统设置" else "Google and system settings some ROMs hide"
    else -> ""
}

val ToolboxStrings.chargingNow: String
    get() = if (chinese) "充电中" else "Charging"

val ToolboxStrings.notCharging: String
    get() = if (chinese) "未充电" else "Not charging"

fun ToolboxStrings.storageOfTotal(total: String): String =
    if (chinese) "共 $total" else "of $total"

fun ToolboxStrings.inOutCaption(expenseText: String, incomeText: String): String =
    if (chinese) "支 $expenseText · 收 $incomeText" else "Out $expenseText · In $incomeText"

// Disposal
