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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.monitor.CpuCoreFrequency
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayService
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper
import com.example.toolbox.monitor.MonitorReader
import com.example.toolbox.monitor.MonitorSample
import com.example.toolbox.monitor.chartValue
import com.example.toolbox.monitor.frequencyLoadPercent
import com.example.toolbox.monitor.weightedCpuFrequencyPercent
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
    val sampleHistory = remember { mutableStateListOf<MonitorSample>() }
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
                val sample = runCatching {
                    withContext(Dispatchers.IO) { monitorReader.read() }
                }.getOrNull()
                latestSample = sample
                if (sample != null) {
                    sampleHistory.add(sample)
                    val extra = sampleHistory.size - HISTORY_LIMIT
                    if (extra > 0) sampleHistory.removeRange(0, extra)
                }
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToolboxTopBar(
                title = strings.monitor,
                onBack = onBack,
                backLabel = strings.back,
                scrollBehavior = scrollBehavior,
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
                    if (isRunning) {
                        Text(
                            text = strings.monitorRunning(overlayFixed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MonitorLiveMetrics(
                        strings = strings,
                        sample = latestSample,
                        history = sampleHistory,
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
                                maxLines = 2,
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
            if (!overlayGranted ||
                (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ) {
                item {
                    InfoCard(strings.permissionsTitle) {
                        if (!overlayGranted) {
                            IssueActionRow(
                                message = strings.overlayPermissionDescription,
                                action = strings.openOverlaySettings,
                                onAction = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            "package:${context.packageName}".toUri(),
                                        ),
                                    )
                                },
                            )
                        }
                        if (!notificationGranted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            IssueActionRow(
                                message = strings.notificationPermissionHint,
                                action = strings.requestNotificationPermission,
                                onAction = {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            )
                        }
                    }
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

private fun clusterLabel(strings: ToolboxStrings, maxMhz: Int?): String {
    val ghz = (maxMhz ?: 0) / 1000.0
    return when {
        ghz >= 3.2 -> strings.primeCore
        ghz >= 2.6 -> strings.bigCore
        ghz >= 2.0 -> strings.midCore
        else -> strings.littleCore
    }
}

private fun buildClusterSummary(strings: ToolboxStrings, cores: List<CpuCoreFrequency>): String {
    if (cores.isEmpty()) return ""
    val clusters = cores.groupBy { it.maximumFrequencyMhz ?: 0 }.toSortedMap()
    return clusters.entries.joinToString(" · ") { (maxMhz, group) ->
        val ghz = maxMhz / 1000.0
        val freqStr = if (ghz >= 1.0) {
            String.format(Locale.US, "%.2f GHz", ghz)
        } else {
            "$maxMhz MHz"
        }
        "${group.size}×${clusterLabel(strings, maxMhz)} $freqStr"
    }
}

private fun formatCoreGhz(mhz: Int?): String {
    val value = mhz ?: return "—"
    return String.format(Locale.US, "%.2f", value / 1000.0)
}

private fun coreFillFraction(core: CpuCoreFrequency, displayMode: CpuTopologyDisplayMode): Float {
    val curMhz = core.currentFrequencyMhz
    val isOffline = curMhz == null || curMhz <= 0
    if (isOffline) return 0f
    return when (displayMode) {
        CpuTopologyDisplayMode.FREQUENCY -> {
            val minMhz = core.minimumFrequencyMhz ?: 0
            val maxMhz = core.maximumFrequencyMhz ?: return 0f
            if (maxMhz > minMhz) {
                ((curMhz - minMhz).toFloat() / (maxMhz - minMhz).toFloat()).coerceIn(0f, 1f)
            } else {
                (curMhz.toFloat() / maxMhz.toFloat()).coerceIn(0f, 1f)
            }
        }
        CpuTopologyDisplayMode.USAGE -> (core.usagePercent ?: 0f).div(100f).coerceIn(0f, 1f)
    }
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
        if (clusterSummary.isNotBlank()) {
            Text(
                text = clusterSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (cores.isEmpty()) {
            Text(
                text = strings.waitingForData,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = displayMode == CpuTopologyDisplayMode.FREQUENCY,
                    onClick = {
                        displayMode = CpuTopologyDisplayMode.FREQUENCY
                        viewType = CpuTopologyViewType.MATRIX
                    },
                    label = { Text(strings.frequencyMode) },
                )
                FilterChip(
                    selected = displayMode == CpuTopologyDisplayMode.USAGE,
                    onClick = {
                        displayMode = CpuTopologyDisplayMode.USAGE
                        viewType = CpuTopologyViewType.MATRIX
                    },
                    label = { Text(strings.usageMode) },
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    viewType = if (viewType == CpuTopologyViewType.MATRIX) {
                        CpuTopologyViewType.GRID
                    } else {
                        CpuTopologyViewType.MATRIX
                    }
                }) {
                    Text(
                        if (viewType == CpuTopologyViewType.MATRIX) {
                            strings.gridView
                        } else {
                            strings.matrixView
                        },
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
                        val copyText = buildString {
                            appendLine("${strings.cpuCoreName}: C${core.index} (${clusterLabel(strings, maxMhz)})")
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
                CpuCoreTable(
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
    val groups = remember(cores) {
        cores.groupBy { it.maximumFrequencyMhz ?: 0 }.toSortedMap().values.toList()
    }
    val fillColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Spacer(modifier = Modifier.width(8.dp))
            }
            group.forEach { core ->
                val isOffline = core.currentFrequencyMhz == null || core.currentFrequencyMhz <= 0
                val animatedFraction by animateFloatAsState(
                    targetValue = coreFillFraction(core, displayMode),
                    animationSpec = tween(durationMillis = 220),
                    label = "Core${core.index}Fill",
                )
                val liveLabel = when {
                    isOffline -> "—"
                    displayMode == CpuTopologyDisplayMode.USAGE ->
                        "${core.usagePercent?.roundToInt() ?: 0}%"
                    else -> formatCoreGhz(core.currentFrequencyMhz)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onCoreClick(core) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(trackColor),
                    ) {
                        if (animatedFraction > 0.008f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFraction)
                                    .align(Alignment.BottomCenter)
                                    .background(fillColor.copy(alpha = if (isOffline) 0.25f else 0.92f)),
                            )
                        }
                    }
                    Text(
                        text = strings.cpuCore(core.index),
                        modifier = Modifier.padding(top = 6.dp),
                        style = labelStyle,
                        color = if (isOffline) muted.copy(alpha = 0.55f) else muted,
                        maxLines = 1,
                    )
                    Text(
                        text = liveLabel,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun CpuCoreTable(
    strings: ToolboxStrings,
    cores: List<CpuCoreFrequency>,
) {
    val tabular = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    cores.forEach { core ->
        val offline = core.currentFrequencyMhz == null || core.currentFrequencyMhz <= 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.cpuCore(core.index),
                modifier = Modifier.width(32.dp),
                style = tabular,
            )
            Text(
                text = clusterLabel(strings, core.maximumFrequencyMhz),
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = if (offline) {
                    strings.offlineCore
                } else {
                    "${core.currentFrequencyMhz} MHz"
                },
                modifier = Modifier.weight(1f),
                style = tabular,
                maxLines = 1,
            )
            Text(
                text = core.maximumFrequencyMhz?.let { "${strings.maximumFrequency} $it" } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            core.usagePercent?.let { usage ->
                Text(
                    text = "${usage.roundToInt()}%",
                    style = tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonitorLiveMetrics(
    strings: ToolboxStrings,
    sample: MonitorSample?,
    history: List<MonitorSample>,
) {
    val color = MaterialTheme.colorScheme.primary
    MonitorMetric.entries.forEach { metric ->
        val series = history.mapNotNull { it.chartValue(metric) }
        MonitorMetricRow(
            label = liveMetricLabel(strings, metric),
            value = formatLiveMetric(sample, metric, strings),
            values = series,
            color = color,
        )
    }
}

@Composable
private fun MonitorMetricRow(
    label: String,
    value: String,
    values: List<Float>,
    color: Color,
) {
    val tabular = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.width(124.dp),
            style = tabular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MonitorSparkline(
            values = values,
            color = color,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        )
    }
}

@Composable
private fun MonitorSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
) {
    Canvas(modifier = modifier) {
        val baseline = size.height - 1.dp.toPx()
        drawLine(
            color = trackColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        if (values.size < 2) return@Canvas
        val lastIndex = (values.lastIndex).coerceAtLeast(1)
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { index, raw ->
            val x = size.width * index / lastIndex
            val y = size.height * (1f - raw.coerceIn(0f, 100f) / 100f)
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, color = color.copy(alpha = 0.12f))
        drawPath(line, color = color, style = stroke)
    }
}

private fun liveMetricLabel(strings: ToolboxStrings, metric: MonitorMetric): String = when (metric) {
    MonitorMetric.CPU -> "CPU"
    MonitorMetric.GPU -> "GPU"
    MonitorMetric.MEMORY -> if (strings.language == AppLanguage.CHINESE) "内存" else "Mem"
    MonitorMetric.BATTERY -> if (strings.language == AppLanguage.CHINESE) "电量" else "Batt"
    MonitorMetric.FPS -> "FPS"
}

internal fun formatLiveMetric(
    sample: MonitorSample?,
    metric: MonitorMetric,
    strings: ToolboxStrings,
): String {
    if (sample == null) return strings.waitingForData
    return when (metric) {
        MonitorMetric.CPU -> sample.chartValue(metric)?.let {
            "${if (sample.cpuUsagePercent == null) "≈" else ""}${it.roundToInt()}%"
        }
            ?: strings.notAvailable
        MonitorMetric.GPU -> sample.gpuUsagePercent?.let { "${it.roundToInt()}%" }
            ?: sample.frequencyLoadPercent(MonitorMetric.GPU)?.let { "≈${it.roundToInt()}%" }
            ?: strings.notAvailable
        MonitorMetric.MEMORY -> sample.memoryUsagePercent?.let { "${it.roundToInt()}%" }
            ?: strings.notAvailable
        MonitorMetric.BATTERY -> {
            val level = sample.batteryLevelPercent?.let { "${it.roundToInt()}%" }
                ?: return strings.notAvailable
            val power = sample.batteryPowerMw?.let { " ${it.roundToInt()} mW" }.orEmpty()
            level + power
        }
        MonitorMetric.FPS -> sample.displayRefreshRateHz?.let { "${it.roundToInt()} Hz" }
            ?: strings.notAvailable
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
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                        )
                    }

                    MonitorSparkline(
                        values = PREVIEW_SPARK,
                        color = accentColor.swatch(customAccentRgb),
                        trackColor = Color(colors.gridColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )
                }
            }
        }
    }
}

private const val SAMPLE_INTERVAL_MILLIS = 1000L
private const val HISTORY_LIMIT = 60
private val PREVIEW_SPARK = listOf(
    42f, 38f, 55f, 48f, 62f, 44f, 58f, 51f, 47f, 53f, 49f, 56f,
)
