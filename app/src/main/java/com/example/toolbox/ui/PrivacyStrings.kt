package com.example.toolbox.ui


val ToolboxStrings.privacyIntro: String
    get() = if (chinese) {
        "部分系统藏起来的 Google 与系统设置。灰色项未导出，长按可复制 adb 命令。"
    } else {
        "Google and system settings some ROMs hide. Greyed rows aren't exported; long-press to copy an adb command."
    }

val ToolboxStrings.privacyNotFound: String
    get() = if (chinese) "此设备上未找到" else "Not found on this device"

val ToolboxStrings.privacyLoading: String
    get() = if (chinese) "正在检查可用的系统设置…" else "Checking available system settings…"

val ToolboxStrings.privacyLoadFailed: String
    get() = if (chinese) "读取系统设置列表失败。" else "Could not read the system settings list."

val ToolboxStrings.privacyEmpty: String
    get() = if (chinese) "此设备上没有可用的快捷方式。" else "No shortcuts are available on this device."

val ToolboxStrings.privacyNotExported: String
    get() = if (chinese) "未导出，普通应用无法打开" else "Not exported"

val ToolboxStrings.privacyLaunchFailed: String
    get() = if (chinese) "无法打开该设置" else "Couldn't open that setting"

val ToolboxStrings.privacyCopyAdb: String
    get() = if (chinese) "复制 adb 命令" else "Copy adb command"

val ToolboxStrings.privacyCopiedAdb: String
    get() = if (chinese) "已复制 adb 命令" else "adb command copied"

val ToolboxStrings.privacyPinShortcut: String
    get() = if (chinese) "创建主屏幕快捷方式" else "Home-screen shortcut"

val ToolboxStrings.privacyPinRequested: String
    get() = if (chinese) {
        "已发送创建请求，请在系统弹窗中确认"
    } else {
        "Shortcut request sent; confirm it in the system dialog"
    }

val ToolboxStrings.privacyPinUnsupported: String
    get() = if (chinese) {
        "当前桌面不支持固定快捷方式，或未授予创建快捷方式权限"
    } else {
        "This launcher does not support pinned shortcuts, or the shortcut permission is not granted"
    }

val ToolboxStrings.privacyPinPermissionDenied: String
    get() = if (chinese) {
        "系统拒绝创建快捷方式，请检查桌面或快捷方式权限"
    } else {
        "The system denied the shortcut request; check launcher or shortcut permissions"
    }

val ToolboxStrings.privacyPinFailed: String
    get() = if (chinese) "无法创建快捷方式" else "Couldn't create the shortcut"
