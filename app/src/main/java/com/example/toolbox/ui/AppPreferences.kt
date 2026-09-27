package com.example.toolbox.ui

import android.content.Context
import androidx.core.content.edit
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper

private const val PREFERENCES_NAME = "toolbox_preferences"
private const val LANGUAGE_KEY = "language"
private const val THEME_KEY = "theme"
private const val ACCENT_KEY = "accent"
private const val MONITOR_CPU_DISPLAY_MODE_KEY = "monitor_cpu_display_mode"
private const val MONITOR_OVERLAY_FIXED_KEY = "monitor_overlay_fixed"
private const val MONITOR_OVERLAY_THEME_KEY = "monitor_overlay_theme"
private const val MONITOR_OVERLAY_CUSTOM_COLOR_KEY = "monitor_overlay_custom_color"
private const val MONITOR_OVERLAY_OPACITY_KEY = "monitor_overlay_opacity"
private const val LEDGER_LAST_ACCOUNT_KEY = "ledger_last_account"

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun language(): AppLanguage = runCatching {
        AppLanguage.valueOf(preferences.getString(LANGUAGE_KEY, null) ?: AppLanguage.ENGLISH.name)
    }.getOrDefault(AppLanguage.ENGLISH)

    fun themeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString(THEME_KEY, null) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    fun accentColor(): AccentColor = runCatching {
        AccentColor.valueOf(preferences.getString(ACCENT_KEY, null) ?: AccentColor.DYNAMIC.name)
    }.getOrDefault(AccentColor.DYNAMIC)

    fun saveLanguage(value: AppLanguage) {
        preferences.edit { putString(LANGUAGE_KEY, value.name) }
    }

    fun saveThemeMode(value: ThemeMode) {
        preferences.edit { putString(THEME_KEY, value.name) }
    }

    fun saveAccentColor(value: AccentColor) {
        preferences.edit { putString(ACCENT_KEY, value.name) }
    }

    fun monitorMetrics(): Set<MonitorMetric> {
        val saved = preferences.getStringSet("monitor_metrics", null)
            ?.mapNotNull { runCatching { MonitorMetric.valueOf(it) }.getOrNull() }
            ?.toSet()
        return saved.takeUnless { it.isNullOrEmpty() } ?: MonitorMetric.entries.toSet()
    }

    fun saveMonitorMetrics(value: Set<MonitorMetric>) {
        preferences.edit { putStringSet("monitor_metrics", value.map { it.name }.toSet()) }
    }

    fun monitorCpuDisplayMode(): MonitorCpuDisplayMode = runCatching {
        MonitorCpuDisplayMode.valueOf(
            preferences.getString(
                MONITOR_CPU_DISPLAY_MODE_KEY,
                null,
            ) ?: MonitorCpuDisplayMode.TOPOLOGY_MATRIX.name,
        )
    }.getOrDefault(MonitorCpuDisplayMode.TOPOLOGY_MATRIX)

    fun saveMonitorCpuDisplayMode(value: MonitorCpuDisplayMode) {
        preferences.edit { putString(MONITOR_CPU_DISPLAY_MODE_KEY, value.name) }
    }

    fun monitorOverlayFixed(): Boolean = preferences.getBoolean(MONITOR_OVERLAY_FIXED_KEY, false)

    fun saveMonitorOverlayFixed(value: Boolean) {
        preferences.edit { putBoolean(MONITOR_OVERLAY_FIXED_KEY, value) }
    }

    fun monitorOverlayTheme(): MonitorOverlayTheme = runCatching {
        MonitorOverlayTheme.valueOf(
            preferences.getString(
                MONITOR_OVERLAY_THEME_KEY,
                null,
            ) ?: MonitorOverlayTheme.DARK.name,
        )
    }.getOrDefault(MonitorOverlayTheme.DARK)

    fun saveMonitorOverlayTheme(value: MonitorOverlayTheme) {
        preferences.edit { putString(MONITOR_OVERLAY_THEME_KEY, value.name) }
    }

    fun monitorOverlayCustomColor(): Int =
        preferences.getInt(
            MONITOR_OVERLAY_CUSTOM_COLOR_KEY,
            MonitorOverlayThemeHelper.DEFAULT_CUSTOM_COLOR_RGB,
        )

    fun saveMonitorOverlayCustomColor(value: Int) {
        preferences.edit { putInt(MONITOR_OVERLAY_CUSTOM_COLOR_KEY, value) }
    }

    fun monitorOverlayOpacity(): Float =
        preferences.getFloat(
            MONITOR_OVERLAY_OPACITY_KEY,
            MonitorOverlayThemeHelper.DEFAULT_OPACITY,
        )

    fun saveMonitorOverlayOpacity(value: Float) {
        preferences.edit { putFloat(MONITOR_OVERLAY_OPACITY_KEY, value) }
    }

    /** Last ledger account picked in the editor; "" = none remembered. */
    fun ledgerLastAccount(): String = preferences.getString(LEDGER_LAST_ACCOUNT_KEY, "") ?: ""

    fun saveLedgerLastAccount(value: String) {
        preferences.edit { putString(LEDGER_LAST_ACCOUNT_KEY, value) }
    }
}
