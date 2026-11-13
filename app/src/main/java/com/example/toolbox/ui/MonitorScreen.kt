package com.example.toolbox.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.monitor.MonitorCpuDisplayMode
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorOverlayService
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorSample
import com.example.toolbox.monitor.MonitorSession

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
    val session by MonitorSession.state.collectAsStateWithLifecycle()
    val isRunning = session.overlayRunning
    val latestSample = session.sample
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
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            MonitorSession.observeForeground(context)
        }
    }
    LaunchedEffect(latestSample?.timestampMillis) {
        latestSample?.let { sample ->
            if (sampleHistory.lastOrNull()?.timestampMillis != sample.timestampMillis) {
                sampleHistory.add(sample)
                val extra = sampleHistory.size - HISTORY_LIMIT
                if (extra > 0) sampleHistory.removeRange(0, extra)
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
    }

    fun stopMonitoring() {
        MonitorOverlayService.stop(context)
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
                            ToolboxFilterChip(
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
                            ToolboxFilterChip(
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

private const val SAMPLE_INTERVAL_MILLIS = 1000L
private const val HISTORY_LIMIT = 60
