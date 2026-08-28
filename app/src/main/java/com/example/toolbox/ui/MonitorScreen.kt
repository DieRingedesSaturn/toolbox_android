package com.example.toolbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayColors
import com.example.toolbox.monitor.MonitorOverlayService
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper
import com.example.toolbox.monitor.MonitorReader
import com.example.toolbox.monitor.MonitorSample
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    strings: ToolboxStrings,
    accentColor: AccentColor,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var selectedNames by remember {
        mutableStateOf(preferences.monitorMetrics().map { it.name }.toSet())
    }
    var cpuDisplayModeName by remember {
        mutableStateOf(preferences.monitorCpuDisplayMode().name)
    }
    var overlayFixed by remember {
        mutableStateOf(preferences.monitorOverlayFixed())
    }
    var overlayTheme by remember {
        mutableStateOf(preferences.monitorOverlayTheme())
    }
    var overlayCustomColor by remember {
        mutableStateOf(preferences.monitorOverlayCustomColor())
    }
    var overlayOpacity by remember {
        mutableStateOf(preferences.monitorOverlayOpacity())
    }
    var isRunning by rememberSaveable { mutableStateOf(MonitorOverlayService.running) }
    var latestSample by remember { mutableStateOf<MonitorSample?>(null) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }
    val lifecycleOwner = context as? LifecycleOwner

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                notificationGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedMetrics = selectedNames.mapNotNull { name ->
        runCatching { MonitorMetric.valueOf(name) }.getOrNull()
    }.toSet().ifEmpty { MonitorMetric.entries.toSet() }
    val cpuDisplayMode = runCatching { MonitorCpuDisplayMode.valueOf(cpuDisplayModeName) }
        .getOrDefault(MonitorCpuDisplayMode.CORE_FREQUENCIES)
    val monitorReader = remember(context) { MonitorReader(context) }

    LaunchedEffect(monitorReader) {
        while (isActive) {
            latestSample = runCatching {
                withContext(Dispatchers.IO) { monitorReader.read() }
            }.getOrNull()
            delay(SAMPLE_INTERVAL_MILLIS)
        }
    }

    fun startOverlay(
        metrics: Set<MonitorMetric> = selectedMetrics,
        displayMode: MonitorCpuDisplayMode = cpuDisplayMode,
        fixedPosition: Boolean = overlayFixed,
        theme: MonitorOverlayTheme = overlayTheme,
        customColor: Int = overlayCustomColor,
        opacity: Float = overlayOpacity,
    ) {
        MonitorOverlayService.start(
            context = context,
            metrics = metrics,
            cpuDisplayMode = displayMode,
            fixedPosition = fixedPosition,
            chinese = strings.language == AppLanguage.CHINESE,
            accentColor = accentColor.swatch().toArgb(),
            overlayTheme = theme,
            overlayCustomColor = customColor,
            overlayOpacity = opacity,
        )
    }

    fun startMonitoring() {
        overlayGranted = Settings.canDrawOverlays(context)
        if (!overlayGranted) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                ),
            )
            return
        }
        notificationGranted = hasNotificationPermission(context)
        if (!notificationGranted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startOverlay()
        isRunning = true
    }

    fun stopMonitoring() {
        MonitorOverlayService.stop(context)
        isRunning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.monitor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard(strings.monitor) {
                    Text(
                        text = strings.monitorDescription,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (isRunning) strings.monitorRunning(overlayFixed) else strings.gpuFallbackHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                CpuFrequencyCard(
                    strings = strings,
                    sample = latestSample,
                )
            }
            item {
                FpsCard(
                    context = context,
                    strings = strings,
                    sample = latestSample,
                )
            }
            item {
                InfoCard(strings.cpuDisplayModeTitle) {
                    Text(
                        text = strings.cpuDisplayModeHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MonitorCpuDisplayMode.entries.forEach { mode ->
                            FilterChip(
                                selected = mode == cpuDisplayMode,
                                onClick = {
                                    cpuDisplayModeName = mode.name
                                    preferences.saveMonitorCpuDisplayMode(mode)
                                    if (isRunning) startOverlay(displayMode = mode)
                                },
                                label = {
                                    Text(
                                        when (mode) {
                                            MonitorCpuDisplayMode.CORE_FREQUENCIES -> strings.coreFrequencies
                                            MonitorCpuDisplayMode.WEIGHTED_USAGE -> strings.weightedCpuUsage
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                InfoCard(strings.overlayBehaviorTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.overlayFixedPosition,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = strings.overlayFixedPositionDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayFixed,
                            onCheckedChange = { checked ->
                                overlayFixed = checked
                                preferences.saveMonitorOverlayFixed(checked)
                                if (isRunning) startOverlay(fixedPosition = checked)
                            },
                        )
                    }
                }
            }
            item {
                OverlayAppearanceCard(
                    strings = strings,
                    accentColor = accentColor,
                    selectedTheme = overlayTheme,
                    customColor = overlayCustomColor,
                    opacity = overlayOpacity,
                    onThemeChange = { theme ->
                        overlayTheme = theme
                        preferences.saveMonitorOverlayTheme(theme)
                        if (isRunning) startOverlay(theme = theme)
                    },
                    onCustomColorChange = { color ->
                        overlayCustomColor = color
                        preferences.saveMonitorOverlayCustomColor(color)
                        if (isRunning) startOverlay(customColor = color)
                    },
                    onOpacityChange = { op ->
                        overlayOpacity = op
                        preferences.saveMonitorOverlayOpacity(op)
                        if (isRunning) startOverlay(opacity = op)
                    },
                )
            }
            item {
                InfoCard(strings.monitoredMetrics) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MonitorMetric.entries.forEach { metric ->
                            FilterChip(
                                selected = metric in selectedMetrics,
                                onClick = {
                                    val updated = selectedMetrics.toMutableSet()
                                    if (metric !in updated || updated.size > 1) {
                                        if (!updated.add(metric)) updated.remove(metric)
                                        selectedNames = updated.map { it.name }.toSet()
                                        preferences.saveMonitorMetrics(updated)
                                        if (isRunning) startOverlay(metrics = updated)
                                    }
                                },
                                label = { Text(strings.monitorMetric(metric)) },
                            )
                        }
                    }
                }
            }
            item {
                ThermalCard(
                    context = context,
                    strings = strings,
                    sample = latestSample,
                )
            }
            item {
                InfoCard(strings.permissionsTitle) {
                    Text(
                        text = strings.overlayPermissionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (overlayGranted) strings.overlayReady else strings.overlayPermissionDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!overlayGranted) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            },
                        ) {
                            Text(strings.openOverlaySettings)
                        }
                    }
                    Text(
                        text = strings.notificationPermissionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (notificationGranted) strings.notificationReady else strings.notificationPermissionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text(strings.requestNotificationPermission)
                        }
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            text = strings.notificationReady,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = strings.foregroundServicePermissionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = strings.foregroundServiceReady,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Button(
                    onClick = if (isRunning) ::stopMonitoring else ::startMonitoring,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRunning) strings.stopMonitoring else strings.startMonitoring)
                }
            }
        }
    }
}

@Composable
private fun ThermalCard(
    context: android.content.Context,
    strings: ToolboxStrings,
    sample: MonitorSample?,
) {
    val batteryTemperature = sample?.batteryTemperatureCelsius?.let {
        String.format(Locale.US, "%.1f °C", it)
    } ?: strings.waitingForData
    val thermalStatus = sample?.thermalStatus?.let(strings::thermalStatus) ?: strings.waitingForData
    val thermalHeadroom = sample?.thermalHeadroom?.let {
        String.format(Locale.US, "%.2f", it)
    } ?: strings.notAvailable

    InfoCard(strings.thermalTitle) {
        CopyableMonitorRow(context, strings, strings.batteryTemperature, batteryTemperature)
        CopyableMonitorRow(context, strings, strings.thermalStatusTitle, thermalStatus)
        CopyableMonitorRow(context, strings, strings.thermalHeadroomTitle, thermalHeadroom)
        Text(
            text = strings.thermalSensorNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FpsCard(
    context: android.content.Context,
    strings: ToolboxStrings,
    sample: MonitorSample?,
) {
    val currentFps = sample?.displayRefreshRateHz?.let {
        String.format(Locale.US, "%.1f Hz", it)
    } ?: strings.waitingForData
    val supportedRates = sample?.supportedRefreshRatesHz
        ?.takeUnless { it.isEmpty() }
        ?.joinToString(", ") { "$it Hz" }
        ?: strings.notAvailable

    InfoCard(strings.fpsTitle) {
        CopyableMonitorRow(context, strings, strings.currentFps, currentFps)
        CopyableMonitorRow(context, strings, strings.supportedRefreshRates, supportedRates)
        Text(
            text = strings.fpsSourceNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CopyableMonitorRow(
    context: android.content.Context,
    strings: ToolboxStrings,
    label: String,
    value: String,
) {
    InfoRow(
        label = label,
        value = value,
        copyAction = strings.copy,
    ) {
        copyToClipboard(
            context = context,
            label = label,
            value = value,
            copiedMessage = strings.copied(label),
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CpuFrequencyCard(
    strings: ToolboxStrings,
    sample: MonitorSample?,
) {
    InfoCard(strings.cpuFrequenciesTitle) {
        Text(
            text = strings.cpuFrequenciesDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val cores = sample?.cpuCores.orEmpty()
        if (cores.isEmpty()) {
            Text(
                text = strings.waitingForData,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            cores.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { core ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = strings.cpuCore(core.index),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = core.currentFrequencyMhz?.let { "$it MHz" }
                                        ?: strings.noFrequencyData,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                core.maximumFrequencyMhz?.let { maximum ->
                                    Text(
                                        text = "${strings.maximumFrequency} $maximum MHz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayAppearanceCard(
    strings: ToolboxStrings,
    accentColor: AccentColor,
    selectedTheme: MonitorOverlayTheme,
    customColor: Int,
    opacity: Float,
    onThemeChange: (MonitorOverlayTheme) -> Unit,
    onCustomColorChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    val colors = remember(selectedTheme, customColor, opacity) {
        MonitorOverlayThemeHelper.resolveColors(selectedTheme, customColor, opacity)
    }

    InfoCard(strings.overlayAppearanceTitle) {
        Text(
            text = strings.overlayAppearanceDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonitorOverlayTheme.entries.forEach { theme ->
                FilterChip(
                    selected = theme == selectedTheme,
                    onClick = { onThemeChange(theme) },
                    label = {
                        Text(
                            when (theme) {
                                MonitorOverlayTheme.DARK -> strings.overlayThemeDark
                                MonitorOverlayTheme.LIGHT -> strings.overlayThemeLight
                                MonitorOverlayTheme.EVERFOREST -> strings.overlayThemeEverforest
                                MonitorOverlayTheme.BLACK -> strings.overlayThemeBlack
                                MonitorOverlayTheme.CUSTOM -> strings.overlayThemeCustom
                            },
                        )
                    },
                    leadingIcon = {
                        val previewColor = when (theme) {
                            MonitorOverlayTheme.DARK -> Color(0xFF1E1E1E)
                            MonitorOverlayTheme.LIGHT -> Color(0xFFFFFFFF)
                            MonitorOverlayTheme.EVERFOREST -> Color(0xFF2D353B)
                            MonitorOverlayTheme.BLACK -> Color(0xFF000000)
                            MonitorOverlayTheme.CUSTOM -> Color(customColor or (0xFF shl 24))
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(previewColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                        )
                    },
                )
            }
        }

        if (selectedTheme == MonitorOverlayTheme.CUSTOM) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.overlayPresetColorsTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MonitorOverlayThemeHelper.PRESET_CUSTOM_COLORS.forEach { colorInt ->
                        val isSelected = (customColor and 0x00FFFFFF) == (colorInt and 0x00FFFFFF)
                        val color = Color(colorInt or (0xFF shl 24))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { onCustomColorChange(colorInt) },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.overlayOpacityTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${(opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.0f..1.0f,
                steps = 19,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings.overlayPreviewTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(colors.backgroundColor))
                    .border(1.dp, Color(colors.gridColor), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Toolbox",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "×",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CPU",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "32%",
                            color = Color(colors.secondaryTextColor),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                    ) {
                        val w = size.width
                        val h = size.height
                        val linePaintColor = Color(colors.gridColor)
                        drawLine(
                            color = linePaintColor,
                            start = androidx.compose.ui.geometry.Offset(0f, h),
                            end = androidx.compose.ui.geometry.Offset(w, h),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            color = linePaintColor,
                            start = androidx.compose.ui.geometry.Offset(0f, h / 2f),
                            end = androidx.compose.ui.geometry.Offset(w, h / 2f),
                            strokeWidth = 1.dp.toPx(),
                        )
                        val strokeColor = accentColor.swatch()
                        val path = Path().apply {
                            moveTo(0f, h * 0.7f)
                            lineTo(w * 0.2f, h * 0.5f)
                            lineTo(w * 0.4f, h * 0.8f)
                            lineTo(w * 0.6f, h * 0.3f)
                            lineTo(w * 0.8f, h * 0.45f)
                            lineTo(w, h * 0.32f)
                        }
                        drawPath(path, color = strokeColor, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

private const val SAMPLE_INTERVAL_MILLIS = 1000L
