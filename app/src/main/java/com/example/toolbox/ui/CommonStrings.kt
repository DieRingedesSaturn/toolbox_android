package com.example.toolbox.ui


val ToolboxStrings.aboutMockLocationNote: String
    get() = if (chinese) {
        "模拟定位仅用于学习与合法测试：请先在开发者选项中选中 Toolbox，会话会在选定时间后自动停止，也可随时手动停止；模拟坐标可被其他应用识别。"
    } else {
        "Mock location is for learning and lawful testing only. Select Toolbox in Developer options first; a session stops automatically after the chosen time or when you stop it, and other apps can detect mock coordinates."
    }

val ToolboxStrings.appName: String
    get() = "Toolbox"

val ToolboxStrings.about: String
    get() = if (chinese) "关于" else "About"

val ToolboxStrings.settings: String
    get() = if (chinese) "设置" else "Settings"

val ToolboxStrings.device: String
    get() = if (chinese) "设备" else "Device"

val ToolboxStrings.network: String
    get() = if (chinese) "网络" else "Network"

val ToolboxStrings.monitor: String
    get() = if (chinese) "实时监控" else "Live monitor"

val ToolboxStrings.astronomy: String
    get() = if (chinese) "天文" else "Astronomy"

val ToolboxStrings.loading: String
    get() = if (chinese) "读取中…" else "Reading…"

val ToolboxStrings.refresh: String
    get() = if (chinese) "刷新" else "Refresh"

val ToolboxStrings.refreshing: String
    get() = if (chinese) "刷新中…" else "Refreshing…"

val ToolboxStrings.chooseTool: String
    get() = if (chinese) "选择工具" else "Choose a tool"

val ToolboxStrings.noData: String
    get() = if (chinese) "暂无设备信息" else "No device information yet"

val ToolboxStrings.notReadYet: String
    get() = if (chinese) "尚未读取" else "Not read yet"

val ToolboxStrings.copy: String
    get() = if (chinese) "复制" else "Copy"

val ToolboxStrings.back: String
    get() = if (chinese) "返回" else "Back"

val ToolboxStrings.version: String
    get() = if (chinese) "版本 0.1" else "Version 0.1"

val ToolboxStrings.notAvailable: String
    get() = if (chinese) "不可用" else "Not available"

val ToolboxStrings.aboutTitle: String
    get() = if (chinese) "轻量 Android Toolbox" else "A lightweight Android Toolbox"

val ToolboxStrings.aboutDescription: String
    get() = if (chinese) {
        "Toolbox 是一款本地优先的 Android 工具箱，涵盖设备与性能、位置与网络、应用流量、记账、汇率换算和 TOTP 验证器。"
    } else {
        "Toolbox is a local-first Android toolbox covering device and performance data, location and network tools, app usage, bookkeeping, currency rates, and a TOTP authenticator."
    }

val ToolboxStrings.aboutScopeTitle: String
    get() = if (chinese) "主要功能" else "Key features"

val ToolboxStrings.aboutScope: List<String>
    get() = if (chinese) {
        listOf(
            "设备信息：处理器、图形、内存、存储、显示和电池",
            "实时监控：悬浮窗性能图表、多主题与自定义背景",
            "位置与星空：坐标、定位精度、海拔与卫星分布",
            "模拟定位：仅供合法测试，支持坐标、地图选点与限时会话",
            "网络：连接详情、公网 IP 与局域网设备扫描",
            "应用与流量：屏幕时长、蜂窝/WLAN 与共享网络流量",
            "记账与成本：多账户、摊销、订阅、备份与 WebDAV",
            "汇率换算：Frankfurter/ECB 参考汇率",
            "动态令牌：TOTP 验证码、扫码导入与加密备份",
            "隐私：打开被系统藏起的 Google 与系统设置",
        )
    } else {
        listOf(
            "Device Info: CPU, GPU, memory, storage, display, and battery",
            "Live Monitor: overlay performance graphs, themes, and custom colors",
            "Location & sky: coordinates, accuracy, elevation, and satellite view",
            "Mock location: lawful testing only; coordinates, map picker, and timed sessions",
            "Network: connection details, public IP, and LAN device scan",
            "App usage & data: screen time, cellular/Wi-Fi, and shared-network traffic",
            "Ledger & cost: multiple accounts, amortization, subscriptions, backup, and WebDAV",
            "Currency converter: Frankfurter/ECB reference rates",
            "TOTP authenticator: dynamic codes, QR import, and encrypted backup",
            "Privacy: open Google and system settings that some ROMs hide",
        )
    }

val ToolboxStrings.yes: String
    get() = if (chinese) "是" else "Yes"

val ToolboxStrings.no: String
    get() = if (chinese) "否" else "No"

val ToolboxStrings.exportImage: String
    get() = if (chinese) "导出图片" else "Export image"

val ToolboxStrings.exportSuccess: String
    get() = if (chinese) "已保存至相册 (Pictures/Toolbox)" else "Saved to Pictures/Toolbox"

val ToolboxStrings.exportFailed: String
    get() = if (chinese) "导出图片失败" else "Failed to export image"

val ToolboxStrings.shareImage: String
    get() = if (chinese) "分享观星时段图" else "Share visibility chart"

val ToolboxStrings.ledger: String
    get() = if (chinese) "记账与成本摊销" else "Ledger & Cost Tracker"

val ToolboxStrings.unknown: String
    get() = if (chinese) "未知" else "Unknown"

val ToolboxStrings.doneAction: String
    get() = if (chinese) "完成" else "Done"

val ToolboxStrings.searchAction: String
    get() = if (chinese) "搜索" else "Search"

val ToolboxStrings.searchEmptyResult: String
    get() = if (chinese) "没有匹配的记录" else "No matching entries"

fun ToolboxStrings.searchResultCount(count: Int): String =
    if (chinese) "找到 $count 条记录" else {
        "$count result${if (count == 1) "" else "s"}"
    }

val ToolboxStrings.confirmAction: String
    get() = if (chinese) "确定" else "OK"

val ToolboxStrings.saveAction: String
    get() = if (chinese) "保存" else "Save"

val ToolboxStrings.cancelAction: String
    get() = if (chinese) "取消" else "Cancel"

val ToolboxStrings.deleteAction: String
    get() = if (chinese) "删除" else "Delete"

fun ToolboxStrings.copied(label: String): String = if (chinese) "已复制：$label" else "Copied $label"

val ToolboxStrings.editAction: String
    get() = if (chinese) "编辑" else "Edit"

val ToolboxStrings.clearAction: String
    get() = if (chinese) "清空" else "Clear"
