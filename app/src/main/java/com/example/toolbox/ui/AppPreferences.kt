package com.example.toolbox.ui

import android.content.Context
import androidx.core.content.edit
import com.example.toolbox.fx.FxPinnedCurrencies
import org.json.JSONArray
import org.json.JSONObject
import com.example.toolbox.location.MockLocationConfig
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper

private const val PREFERENCES_NAME = "toolbox_preferences"
private const val LANGUAGE_KEY = "language"
private const val THEME_KEY = "theme"
private const val ACCENT_KEY = "accent"
private const val CUSTOM_ACCENT_RGB_KEY = "custom_accent_rgb"
private const val MONITOR_CPU_DISPLAY_MODE_KEY = "monitor_cpu_display_mode"
private const val MONITOR_OVERLAY_FIXED_KEY = "monitor_overlay_fixed"
private const val MONITOR_OVERLAY_THEME_KEY = "monitor_overlay_theme"
private const val MONITOR_OVERLAY_CUSTOM_COLOR_KEY = "monitor_overlay_custom_color"
private const val MONITOR_OVERLAY_OPACITY_KEY = "monitor_overlay_opacity"
private const val LEDGER_LAST_ACCOUNT_KEY = "ledger_last_account"
private const val LEDGER_OVERVIEW_ORDER_KEY = "ledger_overview_order"
private const val HOME_MODULE_ORDER_KEY = "home_module_order"
private const val HOME_SUMMARY_ENABLED_KEY = "home_summary_enabled"
private const val HOME_SUMMARY_ITEMS_KEY = "home_summary_items"
private const val HOME_SUMMARY_CUSTOM_TEXT_KEY = "home_summary_custom_text"
private const val FX_PINNED_CURRENCIES_KEY = "fx_pinned_currencies"
private const val MOCK_LOCATION_ACCURACY_KEY = "mock_location_accuracy"
private const val BACKGROUND_STRENGTH_KEY = "background_strength"
private const val BACKGROUND_BLUR_ENABLED_KEY = "background_blur_enabled"
private const val BACKGROUND_BLUR_STRENGTH_KEY = "background_blur_strength"
private const val BACKGROUND_SCALE_KEY = "background_scale"
private const val BACKGROUND_OFFSET_X_KEY = "background_offset_x"
private const val BACKGROUND_OFFSET_Y_KEY = "background_offset_y"
private const val BACKGROUND_SEED_RGB_KEY = "background_seed_rgb"

/** Retired accent presets seed the custom accent instead of reverting. */
private val LEGACY_ACCENT_SEEDS = mapOf(
    "BLUE" to 0x415F91,
    "GREEN" to 0x386A3F,
    "ORANGE" to 0x8B5000,
    "PURPLE" to 0x735184,
)

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun language(): AppLanguage = runCatching {
        AppLanguage.valueOf(preferences.getString(LANGUAGE_KEY, null) ?: AppLanguage.ENGLISH.name)
    }.getOrDefault(AppLanguage.ENGLISH)

    fun themeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.getString(THEME_KEY, null) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    fun accentColor(): AccentColor {
        val saved = preferences.getString(ACCENT_KEY, null)
            ?: return AccentColor.DYNAMIC
        runCatching { AccentColor.valueOf(saved) }.getOrNull()?.let { return it }
        // Retired presets become a custom accent seeded with their color.
        val legacySeed = LEGACY_ACCENT_SEEDS[saved] ?: return AccentColor.DYNAMIC
        if (!preferences.contains(CUSTOM_ACCENT_RGB_KEY)) {
            saveCustomAccentRgb(legacySeed)
        }
        return AccentColor.CUSTOM
    }

    fun customAccentRgb(): Int =
        preferences.getInt(CUSTOM_ACCENT_RGB_KEY, DEFAULT_CUSTOM_ACCENT_RGB)

    fun saveCustomAccentRgb(value: Int) {
        preferences.edit { putInt(CUSTOM_ACCENT_RGB_KEY, value and 0x00FFFFFF) }
    }

    /** How strongly the picked background image shows through; 0 hides it entirely. */
    fun backgroundStrength(): Float =
        preferences.getFloat(BACKGROUND_STRENGTH_KEY, DEFAULT_BACKGROUND_STRENGTH)
            .coerceIn(0f, MAX_BACKGROUND_STRENGTH)

    fun saveBackgroundStrength(value: Float) {
        preferences.edit { putFloat(BACKGROUND_STRENGTH_KEY, value.coerceIn(0f, MAX_BACKGROUND_STRENGTH)) }
    }

    /** Accent seed sampled from the background image; 0 = never sampled. */
    fun backgroundSeedRgb(): Int = preferences.getInt(BACKGROUND_SEED_RGB_KEY, 0)

    fun saveBackgroundSeedRgb(value: Int) {
        preferences.edit { putInt(BACKGROUND_SEED_RGB_KEY, value and 0x00FFFFFF) }
    }

    fun backgroundBlurEnabled(): Boolean = preferences.getBoolean(BACKGROUND_BLUR_ENABLED_KEY, true)

    fun saveBackgroundBlurEnabled(value: Boolean) {
        preferences.edit { putBoolean(BACKGROUND_BLUR_ENABLED_KEY, value) }
    }

    fun backgroundBlurStrength(): Float =
        preferences.getFloat(BACKGROUND_BLUR_STRENGTH_KEY, DEFAULT_BACKGROUND_BLUR_STRENGTH)
            .coerceIn(0f, 1f)

    fun saveBackgroundBlurStrength(value: Float) {
        preferences.edit { putFloat(BACKGROUND_BLUR_STRENGTH_KEY, value.coerceIn(0f, 1f)) }
    }

    fun saveBackgroundTransform(scale: Float, offsetX: Float, offsetY: Float) {
        preferences.edit {
            putFloat(BACKGROUND_SCALE_KEY, scale.coerceIn(1f, 3f))
            putFloat(BACKGROUND_OFFSET_X_KEY, offsetX.coerceIn(-1f, 1f))
            putFloat(BACKGROUND_OFFSET_Y_KEY, offsetY.coerceIn(-1f, 1f))
        }
    }

    fun backgroundScale(): Float =
        preferences.getFloat(BACKGROUND_SCALE_KEY, 1f).coerceIn(1f, 3f)

    fun saveBackgroundScale(value: Float) {
        preferences.edit { putFloat(BACKGROUND_SCALE_KEY, value.coerceIn(1f, 3f)) }
    }

    fun backgroundOffsetX(): Float =
        preferences.getFloat(BACKGROUND_OFFSET_X_KEY, 0f).coerceIn(-1f, 1f)

    fun saveBackgroundOffsetX(value: Float) {
        preferences.edit { putFloat(BACKGROUND_OFFSET_X_KEY, value.coerceIn(-1f, 1f)) }
    }

    fun backgroundOffsetY(): Float =
        preferences.getFloat(BACKGROUND_OFFSET_Y_KEY, 0f).coerceIn(-1f, 1f)

    fun saveBackgroundOffsetY(value: Float) {
        preferences.edit { putFloat(BACKGROUND_OFFSET_Y_KEY, value.coerceIn(-1f, 1f)) }
    }

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

    /** User-defined order of the Overview cards, with missing cards appended. */
    fun ledgerOverviewOrder(): List<LedgerOverviewCard> =
        LedgerOverviewOrder.parse(preferences.getString(LEDGER_OVERVIEW_ORDER_KEY, null))

    fun saveLedgerOverviewOrder(value: List<LedgerOverviewCard>) {
        preferences.edit { putString(LEDGER_OVERVIEW_ORDER_KEY, LedgerOverviewOrder.format(value)) }
    }

    /** User-defined order of the Home tool modules, with newly added tools appended. */
    fun homeModuleOrder(): List<ToolboxModule> =
        HomeModuleOrder.parse(preferences.getString(HOME_MODULE_ORDER_KEY, null))

    fun saveHomeModuleOrder(value: List<ToolboxModule>) {
        preferences.edit { putString(HOME_MODULE_ORDER_KEY, HomeModuleOrder.format(value)) }
    }

    fun homeSummaryEnabled(): Boolean =
        preferences.getBoolean(HOME_SUMMARY_ENABLED_KEY, true)

    fun saveHomeSummaryEnabled(value: Boolean) {
        preferences.edit { putBoolean(HOME_SUMMARY_ENABLED_KEY, value) }
    }

    internal fun homeSummaryItems(): Set<HomeSummaryItem> =
        HomeSummaryConfig.parseItems(preferences.getString(HOME_SUMMARY_ITEMS_KEY, null))

    internal fun saveHomeSummaryItems(value: Set<HomeSummaryItem>) {
        preferences.edit { putString(HOME_SUMMARY_ITEMS_KEY, HomeSummaryConfig.formatItems(value)) }
    }

    fun homeSummaryCustomText(): String =
        preferences.getString(HOME_SUMMARY_CUSTOM_TEXT_KEY, "").orEmpty()

    fun saveHomeSummaryCustomText(value: String) {
        preferences.edit { putString(HOME_SUMMARY_CUSTOM_TEXT_KEY, value.take(200)) }
    }

    fun fxPinnedCurrencies(): List<String> =
        FxPinnedCurrencies.parse(preferences.getString(FX_PINNED_CURRENCIES_KEY, null))

    fun saveFxPinnedCurrencies(value: List<String>) {
        preferences.edit { putString(FX_PINNED_CURRENCIES_KEY, FxPinnedCurrencies.format(value)) }
    }

    fun mockLocationAccuracyText(): String {
        val saved = preferences.getString(MOCK_LOCATION_ACCURACY_KEY, null)
            ?: return MockLocationConfig.DEFAULT_ACCURACY_TEXT
        return saved.takeIf { MockLocationConfig.parseAccuracyMeters(it) != null }
            ?: MockLocationConfig.DEFAULT_ACCURACY_TEXT
    }

    fun saveMockLocationAccuracyText(value: String) {
        val trimmed = value.trim()
        if (MockLocationConfig.parseAccuracyMeters(trimmed) == null) return
        preferences.edit { putString(MOCK_LOCATION_ACCURACY_KEY, trimmed) }
    }
    /** Explicit allowlist: credentials, tokens, ledger records and device grants never travel. */
    fun backupSettings(): JSONObject = JSONObject().apply {
        preferences.all.forEach { (key, value) ->
            if (key in BACKUP_KEYS) {
                put(key, if (value is Set<*>) JSONArray(value.filterIsInstance<String>().sorted()) else value)
            }
        }
    }

    fun restoreSettings(json: JSONObject) {
        val values = validateBackupSettings(json)
        val editor = preferences.edit()
        BACKUP_KEYS.forEach { editor.remove(it) }
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Couldn't save restored settings" }
    }

    companion object {
        private const val DEFAULT_BACKGROUND_STRENGTH = 0.35f
        private const val MAX_BACKGROUND_STRENGTH = 1.0f
        private const val DEFAULT_BACKGROUND_BLUR_STRENGTH = 0.6f

        private val STRING_KEYS = setOf(LANGUAGE_KEY, THEME_KEY, ACCENT_KEY, MONITOR_CPU_DISPLAY_MODE_KEY,
            MONITOR_OVERLAY_THEME_KEY, LEDGER_LAST_ACCOUNT_KEY, LEDGER_OVERVIEW_ORDER_KEY,
            HOME_MODULE_ORDER_KEY, HOME_SUMMARY_ITEMS_KEY, HOME_SUMMARY_CUSTOM_TEXT_KEY,
            FX_PINNED_CURRENCIES_KEY, MOCK_LOCATION_ACCURACY_KEY)
        private val INT_KEYS = setOf(CUSTOM_ACCENT_RGB_KEY, MONITOR_OVERLAY_CUSTOM_COLOR_KEY,
            BACKGROUND_SEED_RGB_KEY)
        private val BACKUP_KEYS = STRING_KEYS + INT_KEYS + setOf(MONITOR_OVERLAY_FIXED_KEY,
            HOME_SUMMARY_ENABLED_KEY, MONITOR_OVERLAY_OPACITY_KEY, BACKGROUND_STRENGTH_KEY,
            BACKGROUND_BLUR_ENABLED_KEY, BACKGROUND_BLUR_STRENGTH_KEY, BACKGROUND_SCALE_KEY,
            BACKGROUND_OFFSET_X_KEY, BACKGROUND_OFFSET_Y_KEY, "monitor_metrics")

        internal fun sanitizedBackupSettings(json: JSONObject): JSONObject = JSONObject().apply {
            validateBackupSettings(json).forEach { (key, value) ->
                put(key, if (value is Set<*>) JSONArray(value.toList()) else value)
            }
        }

        internal fun validateBackupSettings(json: JSONObject): Map<String, Any> = buildMap {
            json.keys().forEach { key ->
                if (key !in BACKUP_KEYS) return@forEach
                val raw = json.get(key)
                val value: Any = when (key) {
                    in STRING_KEYS -> { require(raw is String); raw }
                    in INT_KEYS -> {
                        require(raw is Number && raw.toDouble() == raw.toInt().toDouble())
                        raw.toInt()
                    }
                    MONITOR_OVERLAY_FIXED_KEY, BACKGROUND_BLUR_ENABLED_KEY, HOME_SUMMARY_ENABLED_KEY -> {
                        require(raw is Boolean); raw
                    }
                    MONITOR_OVERLAY_OPACITY_KEY, BACKGROUND_STRENGTH_KEY, BACKGROUND_BLUR_STRENGTH_KEY -> {
                        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() in 0.0..1.0)
                        raw.toFloat()
                    }
                    BACKGROUND_SCALE_KEY -> {
                        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() in 1.0..3.0)
                        raw.toFloat()
                    }
                    BACKGROUND_OFFSET_X_KEY, BACKGROUND_OFFSET_Y_KEY -> {
                        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() in -1.0..1.0)
                        raw.toFloat()
                    }
                    "monitor_metrics" -> {
                        require(raw is JSONArray && raw.length() <= MonitorMetric.entries.size)
                        (0 until raw.length()).map { index ->
                            val metric = raw.get(index)
                            require(metric is String && MonitorMetric.entries.any { it.name == metric })
                            metric
                        }.toSet()
                    }
                    else -> error("Unsupported setting")
                }
                put(key, value)
            }
        }
    }

}
