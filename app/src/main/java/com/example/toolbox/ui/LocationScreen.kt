package com.example.toolbox.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.toolbox.location.GnssConstellation
import com.example.toolbox.location.GnssSkyViewStatus
import com.example.toolbox.location.LocationInfo
import com.example.toolbox.location.LocationInfoReader
import com.example.toolbox.location.LocationReadStatus
import com.example.toolbox.location.PositioningSystemInfo
import com.example.toolbox.location.SatelliteInfo
import com.example.toolbox.location.TerrainElevationInfo
import com.example.toolbox.location.formatDecimalDegrees
import com.example.toolbox.location.formatLatitudeDms
import com.example.toolbox.location.formatLongitudeDms
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reader = remember(context) { LocationInfoReader(context) }
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(reader.hasLocationPermission()) }
    var hasFinePermission by remember { mutableStateOf(reader.hasFineLocationPermission()) }
    var locationInfo by remember { mutableStateOf<LocationInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var terrainElevation by remember { mutableStateOf<TerrainElevationInfo?>(null) }
    var isLoadingElevation by remember { mutableStateOf(false) }
    var elevationRequested by remember { mutableStateOf(false) }

    var isObservingGnss by remember { mutableStateOf(false) }
    var gnssStatus by remember { mutableStateOf<GnssSkyViewStatus?>(null) }
    var stopGnssObservation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun readLocation() {
        if (isLoading) return
        isLoading = true
        terrainElevation = null
        elevationRequested = false
        scope.launch {
            locationInfo = runCatching { reader.readCurrentLocation() }
                .getOrElse { LocationInfo(LocationReadStatus.ERROR, positioningSystems = reader.readPositioningSystems()) }
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = reader.hasLocationPermission()
        hasFinePermission = reader.hasFineLocationPermission()
        if (hasPermission) {
            readLocation()
        } else {
            locationInfo = LocationInfo(LocationReadStatus.PERMISSION_REQUIRED, positioningSystems = reader.readPositioningSystems())
        }
    }

    fun queryElevation(lat: Double, lon: Double) {
        if (isLoadingElevation) return
        elevationRequested = true
        isLoadingElevation = true
        scope.launch {
            terrainElevation = runCatching {
                reader.queryTerrainElevation(lat, lon)
            }.getOrNull()
            isLoadingElevation = false
        }
    }

    fun startGnssObservation() {
        if (!hasFinePermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
            return
        }
        stopGnssObservation?.invoke()
        isObservingGnss = true
        stopGnssObservation = reader.startObservingGnss { status ->
            gnssStatus = status
        }
    }

    fun stopGnssObservation() {
        stopGnssObservation?.invoke()
        stopGnssObservation = null
        isObservingGnss = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopGnssObservation?.invoke()
            stopGnssObservation = null
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = reader.hasLocationPermission()
                hasFinePermission = reader.hasFineLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestOrReadLocation() {
        if (hasPermission) {
            readLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.location) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::requestOrReadLocation,
                        enabled = !isLoading,
                    ) {
                        Text(if (isLoading) strings.refreshing else strings.refresh)
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
                InfoCard(strings.location) {
                    Text(strings.locationDescription, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            isLoading -> strings.locationLoading
                            locationInfo != null -> strings.locationStatus(locationInfo!!.status)
                            else -> strings.notReadYet
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = ::requestOrReadLocation,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(if (isLoading) strings.locationLoading else strings.getCurrentLocation)
                    }
                    if (locationInfo?.status == LocationReadStatus.SERVICES_DISABLED) {
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                        ) {
                            Text(strings.locationOpenSettings)
                        }
                    }
                }
            }
            item {
                InfoCard(strings.locationPermissionTitle) {
                    Text(
                        text = if (hasPermission) strings.locationPermissionGranted else strings.locationPermissionNotGranted,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = strings.locationPermissionDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasPermission) {
                        CopyableLocationRow(
                            context,
                            strings,
                            strings.locationPrecision,
                            if (hasFinePermission) strings.preciseLocation else strings.approximateLocation,
                        )
                    } else {
                        TextButton(onClick = ::requestOrReadLocation) {
                            Text(strings.requestLocationPermission)
                        }
                    }
                }
            }
            item {
                LocationDataCard(
                    context = context,
                    strings = strings,
                    info = locationInfo,
                )
            }
            val valid = locationInfo?.takeIf { it.status == LocationReadStatus.SUCCESS }
            if (valid?.latitude != null && valid.longitude != null) {
                item {
                    TerrainElevationCard(
                        context = context,
                        strings = strings,
                        latitude = valid.latitude,
                        longitude = valid.longitude,
                        elevation = terrainElevation,
                        isQuerying = isLoadingElevation,
                        requested = elevationRequested,
                        onQuery = { queryElevation(valid.latitude, valid.longitude) },
                    )
                }
            }
            item {
                PositioningSystemsCard(
                    context = context,
                    strings = strings,
                    info = locationInfo?.positioningSystems ?: remember(reader) { reader.readPositioningSystems() },
                )
            }
            item {
                GnssSkyViewCard(
                    strings = strings,
                    isObserving = isObservingGnss,
                    status = gnssStatus,
                    onStart = ::startGnssObservation,
                    onStop = ::stopGnssObservation,
                )
            }
        }
    }
}

@Composable
private fun LocationDataCard(
    context: Context,
    strings: ToolboxStrings,
    info: LocationInfo?,
) {
    InfoCard(strings.currentLocationTitle) {
        val valid = info?.takeIf { it.status == LocationReadStatus.SUCCESS }
        if (valid == null) {
            Text(
                text = strings.locationDataUnavailable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@InfoCard
        }

        CopyableLocationRow(
            context,
            strings,
            strings.latitude,
            valid.latitude?.let(::formatDecimalDegrees) ?: strings.notAvailable,
        )
        CopyableLocationRow(
            context,
            strings,
            strings.longitude,
            valid.longitude?.let(::formatDecimalDegrees) ?: strings.notAvailable,
        )
        valid.latitude?.let {
            CopyableLocationRow(context, strings, strings.latitudeDms, formatLatitudeDms(it))
        }
        valid.longitude?.let {
            CopyableLocationRow(context, strings, strings.longitudeDms, formatLongitudeDms(it))
        }
        CopyableLocationRow(
            context,
            strings,
            strings.accuracy,
            valid.accuracyMeters?.let { "%.1f m".format(Locale.US, it) } ?: strings.notAvailable,
        )
        CopyableLocationRow(
            context,
            strings,
            strings.altitude,
            valid.altitudeMeters?.let { "%.1f m".format(Locale.US, it) } ?: strings.notAvailable,
        )
        valid.verticalAccuracyMeters?.let {
            CopyableLocationRow(
                context,
                strings,
                strings.verticalAccuracy,
                "%.1f m".format(Locale.US, it),
            )
        }
        CopyableLocationRow(context, strings, strings.provider, valid.provider ?: strings.notAvailable)
        CopyableLocationRow(
            context,
            strings,
            strings.locationTime,
            valid.timestampMillis?.let { formatLocationTime(it) } ?: strings.notAvailable,
        )
    }
}

@Composable
private fun TerrainElevationCard(
    context: Context,
    strings: ToolboxStrings,
    latitude: Double,
    longitude: Double,
    elevation: TerrainElevationInfo?,
    isQuerying: Boolean,
    requested: Boolean,
    onQuery: () -> Unit,
) {
    InfoCard(strings.terrainElevationTitle) {
        Text(
            text = strings.elevationDifferenceNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (requested) {
            val elevationValue = when {
                isQuerying -> strings.queryingTerrainElevation
                elevation != null -> "%.1f m".format(Locale.US, elevation.elevationMeters)
                else -> strings.notAvailable
            }
            CopyableLocationRow(context, strings, strings.terrainElevationTitle, elevationValue)
            if (elevation != null) {
                CopyableLocationRow(context, strings, strings.terrainElevationSource, elevation.source)
            }
        }
        Button(
            onClick = onQuery,
            enabled = !isQuerying,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isQuerying) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (isQuerying) strings.queryingTerrainElevation else strings.queryTerrainElevation)
        }
    }
}

@Composable
private fun PositioningSystemsCard(
    context: Context,
    strings: ToolboxStrings,
    info: PositioningSystemInfo?,
) {
    InfoCard(strings.positioningSystemsTitle) {
        val hardware = if (info?.isGnssHardwareAvailable == true) {
            strings.gnssHardwareAvailable
        } else {
            strings.gnssHardwareUnavailable
        }
        CopyableLocationRow(context, strings, strings.gnssHardwareStatus, hardware)
        val available = info?.availableProviders?.takeUnless { it.isEmpty() }?.joinToString(", ") ?: strings.notAvailable
        CopyableLocationRow(context, strings, strings.availableProviders, available)
        val enabled = info?.enabledProviders?.takeUnless { it.isEmpty() }?.joinToString(", ") ?: strings.notAvailable
        CopyableLocationRow(context, strings, strings.enabledProviders, enabled)
        val constellations = info?.supportedConstellations?.takeUnless { it.isEmpty() }?.joinToString("\n") ?: strings.notAvailable
        CopyableLocationRow(context, strings, strings.gnssConstellations, constellations)
    }
}

@Composable
private fun CopyableLocationRow(
    context: Context,
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

private fun formatLocationTime(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMillis))

@Composable
private fun GnssSkyViewCard(
    strings: ToolboxStrings,
    isObserving: Boolean,
    status: GnssSkyViewStatus?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    InfoCard(strings.gnssSkyViewTitle) {
        Text(
            text = strings.gnssObservationHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isObserving) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (status != null && status.usedInFixCount > 0) Color(0xFF50B83C) else Color(0xFFF5A623)),
                    )
                    Text(
                        text = when {
                            status == null -> strings.gnssObserving
                            status.usedInFixCount > 0 -> strings.gnssFixStatusLocked
                            else -> strings.gnssFixStatusSearching
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (status != null && status.usedInFixCount > 0) MaterialTheme.colorScheme.primary else Color(0xFFF5A623),
                    )
                }
                Button(
                    onClick = onStop,
                ) {
                    Text(strings.gnssStopObservation)
                }
            }

            if (status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${strings.gnssTotalSatellites}: ${status.totalCount}") },
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "${strings.gnssUsedInFix}: ${status.usedInFixCount}",
                                color = if (status.usedInFixCount > 0) Color(0xFF50B83C) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                if (status.constellationCounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        status.constellationCounts.forEach { (constellation, count) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(constellation.colorRgb()))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "${constellation.shortLabel()}: $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                if (status.totalCount == 0) {
                    Text(
                        text = strings.gnssNoSatellitesHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (status.usedInFixCount == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp),
                        ) {
                            Text(
                                text = "💡 " + strings.gnssIndoorFixHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    GnssRadarView(satellites = status.satellites)

                    Text(
                        text = "🌐 " + strings.gnssEarthCenter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    GnssSignalStrengthList(strings = strings, satellites = status.satellites)
                }
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.gnssStartObservation)
            }
        }
    }
}

@Composable
private fun GnssRadarView(
    satellites: List<SatelliteInfo>,
    modifier: Modifier = Modifier,
) {
    val northPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val cardinalPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 24f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val elevPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 18f
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val svUsedPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val svUnusedPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 18f
            isFakeBoldText = false
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(290.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val discRadius = size.width / 2f - 14f
            val maxRadius = discRadius - 16f

            // 1. Dark circular Sky Dome Disc background
            drawCircle(
                color = Color(0xFF141D24),
                radius = discRadius,
                center = center,
            )
            drawCircle(
                color = Color(0xFF334654),
                radius = discRadius,
                center = center,
                style = Stroke(width = 3f),
            )

            // 2. Concentric Elevation Circles
            drawCircle(color = Color(0xFF3E5466), radius = maxRadius, center = center, style = Stroke(width = 2f))
            drawCircle(color = Color(0xFF283A48), radius = maxRadius * 0.67f, center = center, style = Stroke(width = 1.5f))
            drawCircle(color = Color(0xFF283A48), radius = maxRadius * 0.33f, center = center, style = Stroke(width = 1.5f))

            // 3. Axes
            drawLine(
                color = Color(0xFF3E5466),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color(0xFF3E5466),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.5f,
            )
            val diag = maxRadius * 0.7071f
            drawLine(color = Color(0xFF202C36), start = Offset(center.x - diag, center.y - diag), end = Offset(center.x + diag, center.y + diag), strokeWidth = 1f)
            drawLine(color = Color(0xFF202C36), start = Offset(center.x - diag, center.y + diag), end = Offset(center.x + diag, center.y - diag), strokeWidth = 1f)

            // 4. Cardinal Direction Labels
            drawContext.canvas.nativeCanvas.drawText("北 N", center.x, center.y - maxRadius + 22f, northPaint)
            drawContext.canvas.nativeCanvas.drawText("南 S", center.x, center.y + maxRadius - 8f, cardinalPaint)
            drawContext.canvas.nativeCanvas.drawText("东 E", center.x + maxRadius - 26f, center.y + 8f, cardinalPaint)
            drawContext.canvas.nativeCanvas.drawText("西 W", center.x - maxRadius + 26f, center.y + 8f, cardinalPaint)

            // Elevation tags
            drawContext.canvas.nativeCanvas.drawText("60°", center.x + 6f, center.y - maxRadius * 0.33f + 14f, elevPaint)
            drawContext.canvas.nativeCanvas.drawText("30°", center.x + 6f, center.y - maxRadius * 0.67f + 14f, elevPaint)

            // 5. Center Earth / Observer Graphic
            drawCircle(color = Color(0x4442A5F5), radius = 18f, center = center)
            drawCircle(color = Color(0xFF1E88E5), radius = 10f, center = center)
            drawCircle(color = Color(0xFF90CAF9), radius = 4f, center = center)

            // 6. Draw Satellites
            satellites.forEach { sat ->
                val elevClamped = sat.elevationDegrees.coerceIn(0f, 90f)
                val r = maxRadius * (1f - (elevClamped / 90f))
                val azimuthRad = Math.toRadians((sat.azimuthDegrees - 90f).toDouble())
                val px = center.x + (r * cos(azimuthRad)).toFloat()
                val py = center.y + (r * sin(azimuthRad)).toFloat()

                val satColor = Color(sat.constellation.colorRgb())
                if (sat.usedInFix) {
                    drawCircle(
                        color = satColor,
                        radius = 12f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = Offset(px, py),
                        style = Stroke(width = 3f),
                    )
                } else {
                    drawCircle(
                        color = satColor.copy(alpha = 0.35f),
                        radius = 10f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = satColor,
                        radius = 10f,
                        center = Offset(px, py),
                        style = Stroke(width = 1.5f),
                    )
                }

                val svText = "${sat.constellation.shortLabel().take(1)}${sat.svid}"
                val paint = if (sat.usedInFix) svUsedPaint else svUnusedPaint
                drawContext.canvas.nativeCanvas.drawText(
                    svText,
                    px + 13f,
                    py + 6f,
                    paint,
                )
            }
        }
    }
}

@Composable
private fun GnssSignalStrengthList(
    strings: ToolboxStrings,
    satellites: List<SatelliteInfo>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = strings.gnssSignalStrength,
            style = MaterialTheme.typography.titleSmall,
        )
        satellites.take(20).forEach { sat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(sat.constellation.colorRgb()),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${sat.constellation.shortLabel()}-${sat.svid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }

                Text(
                    text = if (sat.usedInFix) strings.gnssUsedTag else strings.gnssUnusedTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sat.usedInFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp)),
                ) {
                    val progress = (sat.cn0DbHz / 50f).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    color = when {
                                        sat.cn0DbHz >= 30f -> Color(0xFF50B83C)
                                        sat.cn0DbHz >= 20f -> Color(0xFFF5A623)
                                        else -> Color(0xFF888888)
                                    },
                                    shape = RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                }

                Text(
                    text = "%.1f dB-Hz".format(Locale.US, sat.cn0DbHz),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
