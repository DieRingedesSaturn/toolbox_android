package com.example.toolbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.example.toolbox.monitor.CpuCoreFrequency
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayColors
import com.example.toolbox.monitor.MonitorOverlayService
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper
import com.example.toolbox.monitor.MonitorReader
import com.example.toolbox.monitor.MonitorSample
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun MonitorScreen(
    strings: ToolboxStrings,
    accentColor: AccentColor,
    customAccentRgb: Int,
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
        mutableIntStateOf(preferences.monitorOverlayCustomColor())
    }
    var overlayOpacity by remember {
        mutableFloatStateOf(preferences.monitorOverlayOpacity())
    }
    var isRunning by remember { mutableStateOf(MonitorOverlayService.running) }
    var latestSample by remember { mutableStateOf<MonitorSample?>(null) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var pendingStart by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
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

    LaunchedEffect(monitorReader, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                latestSample = runCatching {
                    withContext(Dispatchers.IO) { monitorReader.read() }
                }.getOrNull()
                isRunning = MonitorOverlayService.running
                delay(SAMPLE_INTERVAL_MILLIS)
            }
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
            accentColor = accentColor.swatch(customAccentRgb).toArgb(),
            overlayTheme = theme,
            overlayCustomColor = customColor,
            overlayOpacity = opacity,
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
        if (pendingStart) {
            pendingStart = false
            startOverlay()
            isRunning = true
        }
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
        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingStart = true
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
            ToolboxTopBar(
                title = strings.monitor,
                onBack = onBack,
                backLabel = strings.back,
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
                    context = context,
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
                                            MonitorCpuDisplayMode.TOPOLOGY_MATRIX -> strings.coreTopologyBars
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
                    customAccentRgb = customAccentRgb,
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

private enum class CpuTopologyDisplayMode {
    FREQUENCY,
    USAGE,
}

private enum class CpuTopologyViewType {
    MATRIX,
    GRID,
}

private fun getCoreClusterColor(maxMhz: Int?): Color {
    val ghz = (maxMhz ?: 0) / 1000.0
    return when {
        ghz >= 3.2 -> Color(0xFFE91E63) // Super Prime / Prime: Crimson Magenta
        ghz >= 2.6 -> Color(0xFFFF7043) // Big Performance: Coral Orange
        ghz >= 2.0 -> Color(0xFFFFB300) // Mid: Golden Amber
        else -> Color(0xFF26A69A)       // Little Efficiency: Mint Teal
    }
}

private fun buildClusterSummary(strings: ToolboxStrings, cores: List<CpuCoreFrequency>): String {
    if (cores.isEmpty()) return ""
    val clusters = cores.groupBy { it.maximumFrequencyMhz ?: 0 }.toSortedMap()
    val parts = clusters.map { (maxMhz, group) ->
        val count = group.size
        val ghz = maxMhz / 1000.0
        val label = when {
            ghz >= 3.2 -> strings.primeCore
            ghz >= 2.6 -> strings.bigCore
            ghz >= 2.0 -> strings.midCore
            else -> strings.littleCore
        }
        val freqStr = if (ghz >= 1.0) "%.2f GHz".format(Locale.US, ghz) else "$maxMhz MHz"
        "${count}x $label ($freqStr)"
    }
    return parts.joinToString(" + ")
}

@Composable
private fun CpuFrequencyCard(
    context: android.content.Context,
    strings: ToolboxStrings,
    sample: MonitorSample?,
) {
    var displayMode by rememberSaveable { mutableStateOf(CpuTopologyDisplayMode.FREQUENCY) }
    var viewType by rememberSaveable { mutableStateOf(CpuTopologyViewType.MATRIX) }
    val cores = sample?.cpuCores.orEmpty()
    val clusterSummary = remember(cores, strings) { buildClusterSummary(strings, cores) }

    InfoCard(strings.cpuTopologyTitle) {
        Text(
            text = strings.cpuTopologyDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (clusterSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = clusterSummary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (cores.isEmpty()) {
            Text(
                text = strings.waitingForData,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // View & Mode Control Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Topology Matrix vs Data Grid toggle
                FilterChip(
                    selected = viewType == CpuTopologyViewType.MATRIX,
                    onClick = { viewType = CpuTopologyViewType.MATRIX },
                    label = { Text(strings.matrixView) },
                )
                FilterChip(
                    selected = viewType == CpuTopologyViewType.GRID,
                    onClick = { viewType = CpuTopologyViewType.GRID },
                    label = { Text(strings.gridView) },
                )

                if (viewType == CpuTopologyViewType.MATRIX) {
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = displayMode == CpuTopologyDisplayMode.FREQUENCY,
                        onClick = { displayMode = CpuTopologyDisplayMode.FREQUENCY },
                        label = { Text(strings.frequencyMode) },
                    )
                    FilterChip(
                        selected = displayMode == CpuTopologyDisplayMode.USAGE,
                        onClick = { displayMode = CpuTopologyDisplayMode.USAGE },
                        label = { Text(strings.usageMode) },
                    )
                }
            }

            if (viewType == CpuTopologyViewType.MATRIX) {
                CpuTopologyMatrix(
                    strings = strings,
                    cores = cores,
                    displayMode = displayMode,
                    onCoreClick = { core ->
                        val maxMhz = core.maximumFrequencyMhz ?: 0
                        val curMhz = core.currentFrequencyMhz
                        val ghz = maxMhz / 1000.0
                        val clusterLabel = when {
                            ghz >= 3.2 -> strings.primeCore
                            ghz >= 2.6 -> strings.bigCore
                            ghz >= 2.0 -> strings.midCore
                            else -> strings.littleCore
                        }
                        val copyText = buildString {
                            appendLine("${strings.cpuCoreName}: C${core.index} ($clusterLabel)")
                            appendLine("${strings.currentFrequencyLabel}: ${curMhz?.let { "$it MHz" } ?: strings.offlineCore}")
                            appendLine("${strings.cpuMaxFrequency}: $maxMhz MHz")
                            if (core.usagePercent != null) {
                                append("${strings.usageMode}: ${core.usagePercent}%")
                            }
                        }
                        copyToClipboard(context, "CPU C${core.index}", copyText, strings.copied("C${core.index}"))
                    },
                )
            } else {
                CpuCoreGrid(
                    strings = strings,
                    cores = cores,
                )
            }
        }
    }
}

@Composable
private fun CpuTopologyMatrix(
    strings: ToolboxStrings,
    cores: List<CpuCoreFrequency>,
    displayMode: CpuTopologyDisplayMode,
    onCoreClick: (CpuCoreFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minMaxMhz = cores.minOfOrNull { it.maximumFrequencyMhz ?: 1000 }?.coerceAtLeast(100) ?: 1000

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cores.forEach { core ->
            val minMhz = core.minimumFrequencyMhz ?: 0
            val maxMhz = core.maximumFrequencyMhz ?: minMaxMhz
            val curMhz = core.currentFrequencyMhz
            val usage = core.usagePercent
            val isOffline = curMhz == null || curMhz <= 0

            // Width weight proportional to max frequency capability
            val weight = (maxMhz.toFloat() / minMaxMhz.toFloat()).coerceIn(1.0f, 2.4f)
            val clusterColor = getCoreClusterColor(maxMhz)

            // Fill fraction from effective baseline (min_freq -> max_freq)
            val targetFraction = when (displayMode) {
                CpuTopologyDisplayMode.FREQUENCY -> {
                    if (isOffline) 0f
                    else if (maxMhz > minMhz) ((curMhz - minMhz).toFloat() / (maxMhz - minMhz).toFloat()).coerceIn(0f, 1f)
                    else (curMhz.toFloat() / maxMhz.toFloat()).coerceIn(0f, 1f)
                }
                CpuTopologyDisplayMode.USAGE -> {
                    if (isOffline || usage == null) 0f
                    else (usage / 100f).coerceIn(0f, 1f)
                }
            }

            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 250),
                label = "Core${core.index}Fill",
            )

            // Individual Core Column Block
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onCoreClick(core) },
            ) {
                // Background Track with 25%, 50%, 75% tick marks
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        )
                    }
                }

                // Vertical Fill Bar from Bottom (Baseline = 0)
                if (!isOffline && animatedFraction > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedFraction)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        clusterColor,
                                        clusterColor.copy(alpha = 0.65f),
                                    ),
                                ),
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                }

                // Content Overlay (Core Header at Top, Live Metric at Bottom)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header: Core Name + Max Freq
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "C${core.index}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                        val maxGhzStr = if (maxMhz >= 1000) "%.1fG".format(Locale.US, maxMhz / 1000.0) else "${maxMhz}M"
                        Text(
                            text = maxGhzStr,
                            fontSize = 8.sp,
                            color = clusterColor.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Footer: Real-time Metric
                    if (isOffline) {
                        Text(
                            text = "—",
                            fontSize = 11.sp,
                        )
                    } else {
                        val liveStr = when (displayMode) {
                            CpuTopologyDisplayMode.FREQUENCY -> {
                                if (curMhz >= 1000) "%.2fG".format(Locale.US, curMhz / 1000.0)
                                else "${curMhz}M"
                            }
                            CpuTopologyDisplayMode.USAGE -> {
                                "${usage?.roundToInt() ?: 0}%"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = liveStr,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuCoreGrid(
    strings: ToolboxStrings,
    cores: List<CpuCoreFrequency>,
) {
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

@Composable
private fun OverlayAppearanceCard(
    strings: ToolboxStrings,
    accentColor: AccentColor,
    customAccentRgb: Int,
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
                        val strokeColor = accentColor.swatch(customAccentRgb)
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
