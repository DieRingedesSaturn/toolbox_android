package com.example.toolbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.location.MockLocationConfig
import com.example.toolbox.location.MockLocationController
import com.example.toolbox.location.MockLocationFailure
import com.example.toolbox.location.MockLocationPhase
import com.example.toolbox.location.MockLocationService
import com.example.toolbox.network.NetworkInfoReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MockLocationCard(strings: ToolboxStrings) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by MockLocationService.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var accuracy by rememberSaveable { mutableStateOf(preferences.mockLocationAccuracyText()) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var ipDescription by rememberSaveable { mutableStateOf<String?>(null) }
    var fetchingIp by remember { mutableStateOf(false) }
    var ipFailed by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(false) }
    var cleanup by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    var servicesEnabled by remember { mutableStateOf(false) }
    var startFailure by remember { mutableStateOf<MockLocationFailure?>(null) }
    var launching by remember { mutableStateOf(false) }
    var showMapPicker by rememberSaveable { mutableStateOf(false) }
    val config = MockLocationConfig.parse(latitude, longitude, accuracy, minutes)
    val idle = !state.busy && !launching && !fetchingIp

    fun persistAccuracy(text: String) {
        preferences.saveMockLocationAccuracyText(text)
    }

    LaunchedEffect(lifecycle, state.phase) {
        if (state.phase != MockLocationPhase.STOPPED) launching = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val current = withContext(Dispatchers.IO) {
                val controller = MockLocationController(context)
                Triple(controller.hasAccess(), controller.hasPendingCleanup(),
                    MockLocationService.notificationsAvailable(context) to controller.servicesEnabled())
            }
            access = current.first
            cleanup = current.second
            notifications = current.third.first
            servicesEnabled = current.third.second
            awaitCancellation()
        }
    }

    fun begin() {
        val target = config ?: return
        if (state.busy || launching) return
        persistAccuracy(accuracy)
        startFailure = null
        launching = true
        runCatching { MockLocationService.start(context, target, strings.language == AppLanguage.CHINESE) }.onFailure {
            launching = false
            startFailure = MockLocationFailure.START
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else startFailure = MockLocationFailure.NOTIFICATIONS
    }

    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { startFailure = MockLocationFailure.START }
    }

    InfoCard(strings.mockLocationTitle) {
        if (state.phase != MockLocationPhase.STOPPED) {
            Text(strings.mockLocationPhase(state.phase), style = MaterialTheme.typography.titleSmall)
        }
        (startFailure ?: state.failure)?.let {
            Text(strings.mockLocationFailure(it), color = MaterialTheme.colorScheme.error)
        }
        if ((cleanup && !state.busy) || state.phase == MockLocationPhase.CLEANUP_REQUIRED) {
            Text(strings.mockLocationCleanupHint, color = MaterialTheme.colorScheme.error)
        }
        state.config?.let { target ->
            InfoRow(strings.latitude, target.latitude.toString(), copyAction = strings.copyAction,
                onCopy = { copyToClipboard(context, strings.latitude, it, strings.copied(strings.latitude)) })
            InfoRow(strings.longitude, target.longitude.toString(), copyAction = strings.copyAction,
                onCopy = { copyToClipboard(context, strings.longitude, it, strings.copied(strings.longitude)) })
            InfoRow(strings.mockLocationAccuracy, "${target.accuracyMeters} m", copyAction = strings.copyAction,
                onCopy = { copyToClipboard(context, strings.mockLocationAccuracy, it, strings.copied(strings.mockLocationAccuracy)) })
        }
        state.endsAtMillis?.let { time ->
            val label = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
            InfoRow(strings.mockLocationEndsAt, label, copyAction = strings.copyAction,
                onCopy = { copyToClipboard(context, strings.mockLocationEndsAt, it, strings.copied(strings.mockLocationEndsAt)) })
        }
        if (state.busy || cleanup || state.phase == MockLocationPhase.CLEANUP_REQUIRED) {
            OutlinedButton(
                enabled = state.phase != MockLocationPhase.STOPPING,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching { MockLocationService.stop(context) }.onFailure { startFailure = MockLocationFailure.START }
                },
            ) { Text(if (state.busy) strings.mockLocationStop else strings.mockLocationCleanup) }
        }
        TextButton(onClick = { expanded = !expanded }) { Text(strings.mockLocationConfigure) }
        if (expanded) {
            if (!access) {
                Text(strings.mockLocationAccessNeeded, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) {
                    Text(strings.mockLocationDeveloperSettings)
                }
            }
            if (!servicesEnabled) {
                Text(strings.mockLocationServicesNeeded, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text(strings.locationOpenSettings)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = idle && !state.busy,
                    onClick = { showMapPicker = true },
                ) { Text(strings.mockLocationPickOnMap) }
                OutlinedButton(
                    enabled = idle && !state.busy,
                    onClick = {
                        fetchingIp = true
                        ipFailed = false
                        scope.launch {
                            try {
                                val result = NetworkInfoReader(context).readPublicIpLocation()
                                if (result == null) ipFailed = true else {
                                    latitude = result.latitude.toString()
                                    longitude = result.longitude.toString()
                                    ipDescription = listOfNotNull(result.place, result.source).joinToString(" · ")
                                }
                            } finally { fetchingIp = false }
                        }
                    },
                ) { Text(if (fetchingIp) strings.refreshing else strings.mockLocationIpButton) }
            }
            if (ipFailed) Text(strings.mockLocationIpFailed, color = MaterialTheme.colorScheme.error)
            ipDescription?.let { description ->
                InfoRow(strings.publicIpLocation, description, copyAction = strings.copyAction,
                    onCopy = { copyToClipboard(context, strings.publicIpLocation, it, strings.copied(strings.publicIpLocation)) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it; ipDescription = null },
                    label = { Text(strings.latitude) },
                    singleLine = true,
                    enabled = idle && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it; ipDescription = null },
                    label = { Text(strings.longitude) },
                    singleLine = true,
                    enabled = idle && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = accuracy,
                    onValueChange = {
                        accuracy = it
                        persistAccuracy(it)
                    },
                    label = { Text(strings.mockLocationAccuracy) },
                    singleLine = true,
                    enabled = !state.busy && !launching,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if ((latitude.isNotBlank() || longitude.isNotBlank()) && config == null) {
                Text(strings.mockLocationInvalid, color = MaterialTheme.colorScheme.error)
            }
            Text(strings.mockLocationDuration)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MockLocationConfig.DURATIONS_MINUTES.forEach { value ->
                    FilterChip(selected = value == minutes, onClick = { minutes = value },
                        enabled = !state.busy && !launching, label = { Text(strings.mockLocationMinutes(value)) })
                }
            }
            if (!notifications) {
                Text(strings.mockLocationNotificationNeeded, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(strings.mockLocationNotificationSettings) }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = config != null && access && servicesEnabled && !cleanup && idle && !state.busy,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else begin()
                },
            ) { Text(if (launching) strings.refreshing else strings.mockLocationStart) }
        }
    }
    if (showMapPicker) {
        val startLatitude = parseMapCoordinate(latitude, -90.0, 90.0)
        val startLongitude = parseMapCoordinate(longitude, -180.0, 180.0)
        MockLocationMapDialog(
            strings = strings,
            initialLatitude = if (startLatitude != null && startLongitude != null) startLatitude else null,
            initialLongitude = if (startLatitude != null && startLongitude != null) startLongitude else null,
            onConfirm = { pickedLatitude, pickedLongitude ->
                latitude = formatPickedCoordinate(pickedLatitude)
                longitude = formatPickedCoordinate(pickedLongitude)
                ipDescription = null
                showMapPicker = false
            },
            onDismiss = { showMapPicker = false },
        )
    }
}

@Composable
internal fun MockLocationNotes(strings: ToolboxStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            strings.mockLocationDisclaimer,
            strings.mockLocationDescription,
            strings.mockLocationAccuracyHint,
            strings.mockLocationIpHint,
            strings.mockLocationMapHint,
            strings.mockLocationBackgroundHint,
            strings.mockLocationRecoveryHint,
            strings.mockLocationCleanupNotes,
        ).forEach { paragraph ->
            Text(
                paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
