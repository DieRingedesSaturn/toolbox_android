package com.example.toolbox.ui


val ToolboxStrings.languageTitle: String
    get() = if (chinese) "语言" else "Language"

val ToolboxStrings.themeTitle: String
    get() = if (chinese) "外观" else "Appearance"

val ToolboxStrings.accentTitle: String
    get() = if (chinese) "主题颜色" else "Theme color"

val ToolboxStrings.system: String
    get() = if (chinese) "跟随系统" else "System"

val ToolboxStrings.light: String
    get() = if (chinese) "浅色" else "Light"

val ToolboxStrings.dark: String
    get() = if (chinese) "深色" else "Dark"

val ToolboxStrings.dynamic: String
    get() = if (chinese) "动态" else "Dynamic"

val ToolboxStrings.everforest: String
    get() = "Everforest"

val ToolboxStrings.sage: String
    get() = if (chinese) "鼠尾草" else "Sage"

val ToolboxStrings.custom: String
    get() = if (chinese) "自定义" else "Custom"

val ToolboxStrings.accentDynamicHint: String
    get() = if (chinese) {
        "跟随系统壁纸取色（需要 Android 12+）"
    } else {
        "Colors follow your wallpaper (requires Android 12+)"
    }

val ToolboxStrings.accentHexLabel: String
    get() = if (chinese) "色值" else "Hex color"

val ToolboxStrings.accentHexError: String
    get() = if (chinese) "请输入 6 位十六进制色值" else "Enter 6 hex digits"

val ToolboxStrings.accentBackground: String
    get() = if (chinese) "背景" else "Background"

val ToolboxStrings.backgroundTitle: String
    get() = if (chinese) "背景图片" else "Background image"

val ToolboxStrings.backgroundHint: String
    get() = if (chinese) {
        "选择一张图片铺满应用；卡片保持不透明，文字仍然清晰。"
    } else {
        "A picture behind the app. Cards stay solid so text stays readable."
    }

val ToolboxStrings.backgroundPick: String
    get() = if (chinese) "选择图片" else "Choose image"

val ToolboxStrings.backgroundChange: String
    get() = if (chinese) "更换" else "Change"

val ToolboxStrings.backgroundRemove: String
    get() = if (chinese) "移除" else "Remove"

val ToolboxStrings.backgroundStrengthLabel: String
    get() = if (chinese) "背景强度" else "Background strength"

val ToolboxStrings.backgroundBlurLabel: String
    get() = if (chinese) "卡片高斯模糊" else "Card blur"

val ToolboxStrings.backgroundBlurStrengthLabel: String
    get() = if (chinese) "模糊强度" else "Blur strength"

val ToolboxStrings.backgroundPositionTitle: String
    get() = if (chinese) "调整背景" else "Adjust background"

val ToolboxStrings.backgroundAdjustPosition: String
    get() = if (chinese) "调整位置" else "Adjust position"

val ToolboxStrings.backgroundGestureHint: String
    get() = if (chinese) "单指拖动，双指缩放；保存后生效。" else "Drag to move, pinch to zoom. Save to apply."

val ToolboxStrings.backgroundPositionHint: String
    get() = if (chinese) "点击预览图，进入全屏位置与缩放调整" else "Tap the preview to adjust position and zoom full screen"

val ToolboxStrings.backgroundResetTransform: String
    get() = if (chinese) "重置位置" else "Reset position"

val ToolboxStrings.backgroundAccentHint: String
    get() = if (chinese) {
        "主题色已切换为「背景」，取色自这张图片。"
    } else {
        "Accent switched to \"Background\", sampled from this image."
    }

val ToolboxStrings.backgroundPickFailed: String
    get() = if (chinese) "无法读取这张图片" else "Couldn't read that image"

val ToolboxStrings.settingsAppearanceTitle: String
    get() = if (chinese) "外观" else "Appearance"

val ToolboxStrings.settingsAppearanceSummary: String
    get() = if (chinese) "语言、主题、强调色与背景" else "Language, theme, accent, and wallpaper"

val ToolboxStrings.settingsHomeSummary: String
    get() = if (chinese) "主卡片、空行与布局" else "Summary card, blank rows, and layout"

val ToolboxStrings.settingsAboutSummary: String
    get() = if (chinese) "版本与功能说明" else "Version and features"
