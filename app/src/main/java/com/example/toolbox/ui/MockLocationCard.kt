package com.example.toolbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.toolbox.location.PlaceNameReader
import com.example.toolbox.network.NetworkInfoReader
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MockLocationCard(strings: ToolboxStrings) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val endTimeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val preferences = remember(context) { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by MockLocationService.state.collectAsStateWithLifecycle()
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var accuracy by rememberSaveable { mutableStateOf(preferences.mockLocationAccuracyText()) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var placeName by rememberSaveable { mutableStateOf<String?>(null) }
    var namedCoordKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lookedUpCoordKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lookingUpPlace by remember { mutableStateOf(false) }
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
    val editing = !state.busy
    val parsedLatitude = parseMapCoordinate(latitude, -90.0, 90.0)
    val parsedLongitude = parseMapCoordinate(longitude, -180.0, 180.0)
    val coordKey = if (parsedLatitude != null && parsedLongitude != null) {
        "${formatPickedCoordinate(parsedLatitude)},${formatPickedCoordinate(parsedLongitude)}"
    } else {
        null
    }
    val currentCoordKey by rememberUpdatedState(coordKey)
    val placeReader = remember(context) { PlaceNameReader(context) }

    LaunchedEffect(coordKey, lifecycle) {
        val requestKey = coordKey
        if (coordKey == null || parsedLatitude == null || parsedLongitude == null) {
            placeName = null
            namedCoordKey = null
            lookedUpCoordKey = null
            lookingUpPlace = false
            return@LaunchedEffect
        }
        if (namedCoordKey != coordKey) {
            placeName = null
            namedCoordKey = null
        }
        if (lookedUpCoordKey != requestKey) lookedUpCoordKey = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (lookedUpCoordKey == requestKey) return@repeatOnLifecycle
            try {
                delay(400)
                lookingUpPlace = true
                val lookedUp = placeReader.lookup(parsedLatitude, parsedLongitude)
                ensureActive()
                if (currentCoordKey == requestKey) {
                    if (lookedUp != null) placeName = lookedUp
                    namedCoordKey = requestKey
                    lookedUpCoordKey = requestKey
                }
            } finally {
                if (currentCoordKey == requestKey) lookingUpPlace = false
            }
        }
    }

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
            Text(
                strings.mockLocationPhase(state.phase),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        (startFailure ?: state.failure)?.let {
            Text(
                strings.mockLocationFailure(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if ((cleanup && !state.busy) || state.phase == MockLocationPhase.CLEANUP_REQUIRED) {
            Text(
                strings.mockLocationCleanupHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (coordKey != null) {
            MockPlaceLine(
                context = context,
                strings = strings,
                placeName = placeName,
                lookingUp = lookingUpPlace,
            )
        }
        state.config?.let { target ->
            val endLabel = state.endsAtMillis?.let { time ->
                endTimeFormat.format(Date(time))
            }
            val summary = buildString {
                append(formatPickedCoordinate(target.latitude))
                append(", ")
                append(formatPickedCoordinate(target.longitude))
                append(" · ")
                append(formatAccuracyMeters(target.accuracyMeters))
                append(" m")
                if (endLabel != null) {
                    append(" · ")
                    append(endLabel)
                }
            }
            Text(
                text = summary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        copyToClipboard(context, strings.mockLocationTitle, summary, strings.copied(strings.mockLocationTitle))
                    },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
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
        if (editing) {
            if (!access) {
                IssueActionRow(
                    message = strings.mockLocationAccessNeeded,
                    action = strings.mockLocationDeveloperSettings,
                    onAction = { openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                )
            }
            if (!servicesEnabled) {
                IssueActionRow(
                    message = strings.mockLocationServicesNeeded,
                    action = strings.locationOpenSettings,
                    onAction = { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = idle,
                        onClick = { showMapPicker = true },
                    ) { Text(strings.mockLocationMapButton) }
                    TextButton(
                        enabled = idle,
                        onClick = {
                            fetchingIp = true
                            ipFailed = false
                            scope.launch {
                                try {
                                    val result = NetworkInfoReader(context).readPublicIpLocation()
                                    if (result == null) {
                                        ipFailed = true
                                    } else {
                                        val latText = formatPickedCoordinate(result.latitude)
                                        val lonText = formatPickedCoordinate(result.longitude)
                                        latitude = latText
                                        longitude = lonText
                                        placeName = result.place
                                        namedCoordKey = "$latText,$lonText"
                                    }
                                } finally {
                                    fetchingIp = false
                                }
                            }
                        },
                    ) { Text(if (fetchingIp) strings.refreshing else strings.mockLocationIpButton) }
                }
            }
            if (ipFailed) {
                Text(
                    strings.mockLocationIpFailed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MockField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = strings.mockLatitudeLabel,
                    enabled = idle,
                    modifier = Modifier.weight(1.15f),
                )
                MockField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = strings.mockLongitudeLabel,
                    enabled = idle,
                    modifier = Modifier.weight(1.15f),
                )
                MockField(
                    value = accuracy,
                    onValueChange = {
                        accuracy = it
                        persistAccuracy(it)
                    },
                    label = strings.mockLocationAccuracy,
                    enabled = !launching,
                    modifier = Modifier.weight(0.7f),
                    imeAction = ImeAction.Done,
                )
            }
            if ((latitude.isNotBlank() || longitude.isNotBlank()) && config == null) {
                Text(
                    strings.mockLocationInvalid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MockLocationConfig.DURATIONS_MINUTES.forEach { value ->
                        FilterChip(
                            selected = value == minutes,
                            onClick = { minutes = value },
                            enabled = !launching,
                            label = { Text(strings.mockLocationMinutes(value)) },
                        )
                    }
                }
            }
            if (!notifications) {
                IssueActionRow(
                    message = strings.mockLocationNotificationNeeded,
                    action = strings.mockLocationNotificationSettings,
                    onAction = {
                        openSettings(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = config != null && access && servicesEnabled && !cleanup && idle,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        begin()
                    }
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
                showMapPicker = false
            },
            onDismiss = { showMapPicker = false },
        )
    }
}

@Composable
private fun MockPlaceLine(
    context: android.content.Context,
    strings: ToolboxStrings,
    placeName: String?,
    lookingUp: Boolean,
) {
    val text = when {
        !placeName.isNullOrBlank() -> placeName
        lookingUp -> strings.lookingUpPlace
        else -> strings.notAvailable
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                copyToClipboard(context, strings.placeName, text, strings.copied(strings.placeName))
            },
        style = MaterialTheme.typography.titleSmall,
        color = if (placeName.isNullOrBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MockField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontFeatureSettings = "tnum",
    )
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Box(modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "—",
                            style = textStyle.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                        )
                    }
                    innerTextField()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor),
                )
            }
        },
    )
}

private fun formatAccuracyMeters(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

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
