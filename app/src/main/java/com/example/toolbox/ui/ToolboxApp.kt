package com.example.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.device.DeviceInfoReader
import com.example.toolbox.fx.FxRates
import com.example.toolbox.ledger.ACTION_ADD_LEDGER_ENTRY
import com.example.toolbox.ledger.ACTION_OPEN_LEDGER
import com.example.toolbox.ledger.LedgerWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ToolboxModule {
    HOME,
    DEVICE,
    LOCATION,
    NETWORK,
    MONITOR,
    ASTRONOMY,
    USAGE,
    LEDGER,
    FX,
    SETTINGS,
    ABOUT,
}

@Composable
fun ToolboxApp(
    launchAction: String? = null,
    onLaunchActionConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var languageName by rememberSaveable { mutableStateOf(preferences.language().name) }
    var themeName by rememberSaveable { mutableStateOf(preferences.themeMode().name) }
    var accentName by rememberSaveable { mutableStateOf(preferences.accentColor().name) }
    var customAccentRgb by rememberSaveable { mutableStateOf(preferences.customAccentRgb()) }
    var moduleName by rememberSaveable { mutableStateOf(ToolboxModule.HOME.name) }

    val language = runCatching { AppLanguage.valueOf(languageName) }
        .getOrDefault(AppLanguage.ENGLISH)
    val themeMode = runCatching { ThemeMode.valueOf(themeName) }
        .getOrDefault(ThemeMode.SYSTEM)
    val accentColor = runCatching { AccentColor.valueOf(accentName) }
        .getOrDefault(AccentColor.DYNAMIC)
    val strings = remember(language) { ToolboxStrings(language) }

    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var isDeviceInfoLoading by remember { mutableStateOf(false) }
    var lastDeviceInfoUpdate by rememberSaveable { mutableStateOf<String?>(null) }
    var ledgerEditorRequested by remember { mutableStateOf(false) }
    var ledgerTabRequest by remember { mutableStateOf<Int?>(null) }
    var homeStatus by remember { mutableStateOf<HomeStatus?>(null) }
    var ledgerSnapshot by remember { mutableStateOf<LedgerSnapshot?>(null) }
    var fxRates by remember { mutableStateOf<FxRates?>(null) }
    val pageStates = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    LaunchedEffect(launchAction) {
        when (launchAction) {
            ACTION_OPEN_LEDGER -> moduleName = ToolboxModule.LEDGER.name
            ACTION_ADD_LEDGER_ENTRY -> {
                moduleName = ToolboxModule.LEDGER.name
                ledgerEditorRequested = true
            }
        }
        if (launchAction != null) {
            onLaunchActionConsumed()
        }
    }

    fun refreshDeviceInfo() {
        if (isDeviceInfoLoading) return
        isDeviceInfoLoading = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    DeviceInfoReader.read(context.applicationContext)
                }
            }
            result.onSuccess {
                deviceInfo = it
                lastDeviceInfoUpdate = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            }
            isDeviceInfoLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDeviceInfo()
    }

    val module = runCatching { ToolboxModule.valueOf(moduleName) }
        .getOrDefault(ToolboxModule.HOME)

    fun updateLanguage(value: AppLanguage) {
        languageName = value.name
        preferences.saveLanguage(value)
        scope.launch(Dispatchers.IO) {
            LedgerWidget.refreshAll(context.applicationContext)
        }
    }

    fun updateTheme(value: ThemeMode) {
        themeName = value.name
        preferences.saveThemeMode(value)
    }

    fun updateAccent(value: AccentColor) {
        accentName = value.name
        preferences.saveAccentColor(value)
    }

    fun updateCustomAccent(rgb: Int) {
        customAccentRgb = rgb
        preferences.saveCustomAccentRgb(rgb)
    }

    ToolboxTheme(
        themeMode = themeMode,
        accentColor = accentColor,
        customAccentRgb = customAccentRgb,
    ) {
        if (module != ToolboxModule.HOME) {
            BackHandler { moduleName = ToolboxModule.HOME.name }
        }

        AnimatedContent(
            targetState = module,
            transitionSpec = {
                val entering = targetState != ToolboxModule.HOME
                (
                    fadeIn(tween(240)) + slideInHorizontally(tween(240)) {
                        if (entering) it / 16 else -it / 16
                    }
                ).togetherWith(fadeOut(tween(160)))
            },
            label = "moduleTransition",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { target ->
            pageStates.SaveableStateProvider(target.name) {
                when (target) {
                    ToolboxModule.HOME -> HomeScreen(
                        strings = strings,
                        deviceInfo = deviceInfo,
                        status = homeStatus,
                        onStatusChange = { homeStatus = it },
                        onOpen = { moduleName = it.name },
                        onOpenLedgerAccounts = {
                            ledgerTabRequest = 3
                            moduleName = ToolboxModule.LEDGER.name
                        },
                    )

                    ToolboxModule.DEVICE -> DeviceScreen(
                        strings = strings,
                        deviceInfo = deviceInfo,
                        isLoading = isDeviceInfoLoading,
                        lastUpdated = lastDeviceInfoUpdate,
                        onRefresh = ::refreshDeviceInfo,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.LOCATION -> LocationScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.NETWORK -> NetworkScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.MONITOR -> MonitorScreen(
                        strings = strings,
                        accentColor = accentColor,
                        customAccentRgb = customAccentRgb,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.ASTRONOMY -> AstronomyScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.USAGE -> AppUsageScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.LEDGER -> LedgerScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                        openEditorRequested = ledgerEditorRequested,
                        onEditorRequestHandled = { ledgerEditorRequested = false },
                        tabRequest = ledgerTabRequest,
                        onTabRequestHandled = { ledgerTabRequest = null },
                        cachedSnapshot = ledgerSnapshot,
                        onSnapshot = { ledgerSnapshot = it },
                    )

                    ToolboxModule.FX -> FxScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                        cachedRates = fxRates,
                        onRatesChange = { fxRates = it },
                    )

                    ToolboxModule.SETTINGS -> SettingsScreen(
                        strings = strings,
                        language = language,
                        themeMode = themeMode,
                        accentColor = accentColor,
                        customAccentRgb = customAccentRgb,
                        onLanguageChange = ::updateLanguage,
                        onThemeModeChange = ::updateTheme,
                        onAccentColorChange = ::updateAccent,
                        onCustomAccentChange = ::updateCustomAccent,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )

                    ToolboxModule.ABOUT -> AboutScreen(
                        strings = strings,
                        onBack = { moduleName = ToolboxModule.HOME.name },
                    )
                }
            }
        }
    }
}

fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    copiedMessage: String = "Copied $label",
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
}
