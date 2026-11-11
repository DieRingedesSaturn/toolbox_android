package com.example.toolbox.privacy

private const val GMS = "com.google.android.gms"
private const val SETTINGS = "com.android.settings"

/**
 * Ordered fallbacks for screens Chinese ROMs often hide.
 * Class names that appear in public GMS or AOSP manifests are listed first.
 * Action-only guesses are later, so a wrong guess cannot hide a real component.
 */
object PrivacyShortcutCatalog {
    val entries: List<PrivacyShortcut> = listOf(
        PrivacyShortcut(
            id = "google_settings",
            titleEn = "Google settings",
            titleZh = "Google 设置",
            descriptionEn = "The Google services list that some ROMs remove from Settings.",
            descriptionZh = "部分系统会从设置里拿掉的 Google 服务列表。",
            candidates = listOf(
                component(GMS, "$GMS.app.settings.GoogleSettingsLink"),
                component(GMS, "$GMS.app.settings.GoogleSettingsIALink"),
                action("com.google.android.gms.app.settings.GOOGLE_SETTINGS"),
                component(GMS, "$GMS.app.settings.GoogleSettingsActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "location_accuracy",
            titleEn = "Google location accuracy",
            titleZh = "Google 定位精确度",
            descriptionEn = "Google Location Accuracy (the LOCATION_ACCURACY screen).",
            descriptionZh = "Google 的定位精确度（LOCATION_ACCURACY）。",
            candidates = listOf(
                action("com.google.android.gms.location.settings.LOCATION_ACCURACY"),
                component(GMS, "$GMS.location.settings.LocationAccuracyActivity"),
                component(GMS, "$GMS.location.settings.GoogleLocationSettingsActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "ads_privacy",
            titleEn = "Google ads",
            titleZh = "Google 广告",
            descriptionEn = "Ads privacy and the advertising ID.",
            descriptionZh = "广告隐私设置和广告 ID。",
            candidates = listOf(
                action("com.google.android.gms.settings.ADS_PRIVACY"),
                component(GMS, "$GMS.adsidentity.settings.AdsIdentitySettingsActivity"),
                component(GMS, "$GMS.ads.settings.AdsSettingsActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "usage_diagnostics",
            titleEn = "Usage & diagnostics",
            titleZh = "使用情况和诊断",
            descriptionEn = "Google usage and diagnostics reporting.",
            descriptionZh = "Google 的使用情况与诊断数据上报。",
            candidates = listOf(
                action("com.google.android.gms.usagereporting.UPLOAD_CONSENT"),
                component(GMS, "$GMS.usagereporting.settings.UsageReportingActivity"),
                component(GMS, "$GMS.usagereporting.ui.UsageReportingDialogActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "phone_number_verification",
            titleEn = "Phone number verification",
            titleZh = "电话号码验证",
            descriptionEn = "Google's phone-number verification (Constellation). It can check this device's number with the carrier. Open it here to review or turn it off.",
            descriptionZh = "Google 的电话号码验证（Constellation）。它可以通过运营商核对本机号码。从这里打开后可查看或关闭。",
            candidates = listOf(
                component(GMS, "$GMS.constellation.ui.ConstellationSettingsActivity"),
                component(GMS, "$GMS.constellation.ui.ConstellationWebSettingsActivity"),
                action("com.google.android.gms.constellation.SETTINGS"),
                action("com.google.android.gms.constellation.action.PHONE_NUMBER_VERIFICATION"),
            ),
        ),
        PrivacyShortcut(
            id = "google_account_sync",
            titleEn = "Google account sync",
            titleZh = "Google 账号同步",
            descriptionEn = "Sync settings for a Google account, or the system sync screen.",
            descriptionZh = "某个 Google 账号的同步设置；看不到账号时打开系统同步页。",
            candidates = listOf(
                action("android.settings.ACCOUNT_SYNC_SETTINGS", requiresGoogleAccount = true),
                action("android.settings.SYNC_SETTINGS"),
                component(SETTINGS, "$SETTINGS.Settings\$AccountSyncSettingsActivity"),
                component(SETTINGS, "$SETTINGS.accounts.AccountSyncSettings"),
            ),
        ),
        PrivacyShortcut(
            id = "gms_app_info",
            titleEn = "Google Play services info",
            titleZh = "Google Play 服务信息",
            descriptionEn = "App info for Google Play services: permissions, notifications, and storage.",
            descriptionZh = "Google Play 服务的应用信息：权限、通知和存储。",
            candidates = listOf(
                PrivacyShortcutCandidate(
                    action = "android.settings.APPLICATION_DETAILS_SETTINGS",
                    dataUri = "package:$GMS",
                ),
            ),
        ),
        PrivacyShortcut(
            id = "developer_options",
            titleEn = "Developer options",
            titleZh = "开发者选项",
            descriptionEn = "Developer options, including the mock-location app picker.",
            descriptionZh = "开发者选项，包括模拟位置应用的选择。",
            candidates = listOf(
                action("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
                component(SETTINGS, "$SETTINGS.Settings\$DevelopmentSettingsDashboardActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "running_services",
            titleEn = "Running services",
            titleZh = "正在运行的服务",
            descriptionEn = "The running-services list many ROMs hide.",
            descriptionZh = "很多系统会藏起来的正在运行的服务。",
            candidates = listOf(
                component(SETTINGS, "$SETTINGS.Settings\$RunningServicesActivity"),
                component(SETTINGS, "$SETTINGS.applications.RunningServices"),
            ),
        ),
        PrivacyShortcut(
            id = "notification_history",
            titleEn = "Notification history",
            titleZh = "通知历史",
            descriptionEn = "Notification history, or the older notification log.",
            descriptionZh = "通知历史，或更早的通知日志。",
            candidates = listOf(
                component(SETTINGS, "$SETTINGS.notification.history.NotificationHistoryActivity"),
                component(SETTINGS, "$SETTINGS.Settings\$NotificationStationActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "apn_settings",
            titleEn = "APN settings",
            titleZh = "APN",
            descriptionEn = "Access point names for mobile data.",
            descriptionZh = "移动数据的接入点名称。",
            candidates = listOf(
                action("android.settings.APN_SETTINGS"),
                component(SETTINGS, "$SETTINGS.Settings\$ApnSettingsActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "overlay_settings",
            titleEn = "Display over other apps",
            titleZh = "显示在其他应用上层",
            descriptionEn = "The list of apps allowed to draw over other apps.",
            descriptionZh = "允许显示在其他应用上层的应用列表。",
            candidates = listOf(
                component(SETTINGS, "$SETTINGS.Settings\$OverlaySettingsActivity"),
                component(SETTINGS, "$SETTINGS.Settings\$AppDrawOverlaySettingsActivity"),
            ),
        ),
        PrivacyShortcut(
            id = "notification_listener",
            titleEn = "Notification access",
            titleZh = "通知使用权",
            descriptionEn = "Apps that can read notifications.",
            descriptionZh = "可以读取通知的应用。",
            candidates = listOf(
                action("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            ),
        ),
        PrivacyShortcut(
            id = "usage_access",
            titleEn = "Usage access",
            titleZh = "使用情况访问",
            descriptionEn = "Apps allowed to read usage statistics.",
            descriptionZh = "可以读取使用情况统计的应用。",
            candidates = listOf(
                action("android.settings.USAGE_ACCESS_SETTINGS"),
            ),
        ),
        PrivacyShortcut(
            id = "battery_optimization",
            titleEn = "Battery optimization",
            titleZh = "电池优化",
            descriptionEn = "The battery-optimization allowlist.",
            descriptionZh = "电池优化白名单。",
            candidates = listOf(
                action("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
            ),
        ),
        PrivacyShortcut(
            id = "special_app_access",
            titleEn = "Special app access",
            titleZh = "特殊应用权限",
            descriptionEn = "Special access such as overlay, usage, and unrestricted data.",
            descriptionZh = "悬浮窗、使用情况、不受限制的数据等特殊权限。",
            candidates = listOf(
                component(SETTINGS, "$SETTINGS.Settings\$SpecialAccessSettingsActivity"),
                component(SETTINGS, "$SETTINGS.applications.specialaccess.SpecialAccessSettings"),
            ),
        ),
        PrivacyShortcut(
            id = "testing_settings",
            titleEn = "Testing settings",
            titleZh = "测试设置",
            descriptionEn = "The hidden testing menu. Many devices do not export it; long-press still copies an adb command.",
            descriptionZh = "隐藏的测试菜单。很多设备不导出它；长按仍可复制 adb 命令。",
            candidates = listOf(
                component(SETTINGS, "$SETTINGS.Settings\$TestingSettingsActivity"),
            ),
        ),
    )

    private fun component(packageName: String, className: String) = PrivacyShortcutCandidate(
        packageName = packageName,
        className = className,
    )

    private fun action(action: String, requiresGoogleAccount: Boolean = false) = PrivacyShortcutCandidate(
        action = action,
        requiresGoogleAccount = requiresGoogleAccount,
    )
}
