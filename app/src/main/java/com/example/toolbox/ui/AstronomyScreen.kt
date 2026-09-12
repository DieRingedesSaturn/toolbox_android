package com.example.toolbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.astronomy.AstronomyCalculator
import com.example.toolbox.astronomy.AstronomyImageExporter
import com.example.toolbox.astronomy.CelestialBodyType
import com.example.toolbox.astronomy.CelestialBodyVisibility
import com.example.toolbox.astronomy.ExportThemePalette
import com.example.toolbox.astronomy.MoonPhaseInfo
import com.example.toolbox.astronomy.NightTimeline
import com.example.toolbox.location.LocationInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstronomyScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationReader = remember(context) { LocationInfoReader(context) }

    var latitude by remember { mutableStateOf(39.9042) }
    var longitude by remember { mutableStateOf(116.4074) }
    var isUsingDefaultLocation by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var referenceTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val colorScheme = MaterialTheme.colorScheme

    fun refreshAstronomy() {
        isRefreshing = true
        referenceTimeMillis = System.currentTimeMillis()
        if (locationReader.hasLocationPermission()) {
            runCatching {
                val lm = context.getSystemService(android.location.LocationManager::class.java)
                @SuppressLint("MissingPermission")
                val loc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: run {
                        @SuppressLint("MissingPermission")
                        lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    isUsingDefaultLocation = false
                }
            }
        }
        isRefreshing = false
    }

    LaunchedEffect(locationReader) {
        if (locationReader.hasLocationPermission()) {
            runCatching {
                val lm = context.getSystemService(android.location.LocationManager::class.java)
                @SuppressLint("MissingPermission")
                val loc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: run {
                        @SuppressLint("MissingPermission")
                        lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    isUsingDefaultLocation = false
                }
            }
        }
    }

    val timeline = remember(latitude, longitude, referenceTimeMillis) {
        AstronomyCalculator.generateNightTimeline(
            referenceDateMillis = referenceTimeMillis,
            latitude = latitude,
            longitude = longitude,
        )
    }

    fun handleExportImage() {
        if (isExporting) return
        isExporting = true

        val palette = ExportThemePalette(
            backgroundColor = colorScheme.background.toArgb(),
            cardBackgroundColor = colorScheme.surfaceContainer.toArgb(),
            cardInnerBackgroundColor = colorScheme.surfaceVariant.toArgb(),
            textPrimaryColor = colorScheme.onSurface.toArgb(),
            textSecondaryColor = colorScheme.onSurfaceVariant.toArgb(),
            primaryColor = colorScheme.primary.toArgb(),
            secondaryColor = colorScheme.secondary.toArgb(),
            outlineColor = colorScheme.outlineVariant.toArgb(),
            errorColor = colorScheme.error.toArgb(),
        )

        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                AstronomyImageExporter.exportNightTimelineImage(
                    context = context,
                    strings = strings,
                    timeline = timeline,
                    palette = palette,
                )
            }
            isExporting = false
            if (uri != null) {
                Toast.makeText(context, strings.exportSuccess, Toast.LENGTH_SHORT).show()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, strings.shareImage))
            } else {
                Toast.makeText(context, strings.exportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.astronomy,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val latStr = "%.2f°N".format(Locale.US, latitude)
                        val lonStr = "%.2f°E".format(Locale.US, longitude)
                        val note = if (isUsingDefaultLocation) " (${strings.defaultLocationNote})" else ""
                        Text(
                            text = "$latStr, $lonStr$note",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::refreshAstronomy,
                        enabled = !isRefreshing,
                    ) {
                        Text(if (isRefreshing) strings.refreshing else strings.refresh)
                    }
                    TextButton(
                        onClick = ::handleExportImage,
                        enabled = !isExporting,
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(strings.exportImage)
                        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Hero Moon Phase Card
            item {
                MoonPhaseHeroCard(
                    strings = strings,
                    moonPhase = timeline.moonPhase,
                )
            }

            // 2. Tonight's Visible Timeline Gantt Chart (Unified Row-by-Row Centered Layout)
            item {
                NightTimelineShowcaseCard(
                    strings = strings,
                    timeline = timeline,
                    onExport = ::handleExportImage,
                )
            }

            // 3. Header for Celestial Bodies
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.celestialBodiesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val visibleCount = timeline.bodies.count { it.isVisibleTonight }
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "$visibleCount / ${timeline.bodies.size} ${strings.visibleTonight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // 4. Planet & Moon Cards
            items(timeline.bodies) { body ->
                CelestialBodyCard(
                    context = context,
                    strings = strings,
                    body = body,
                )
            }
        }
    }
}

@Composable
private fun MoonPhaseHeroCard(
    strings: ToolboxStrings,
    moonPhase: MoonPhaseInfo,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Moon Visual with Exact Center Alignment & Radial Aura
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = moonPhase.phase.emoji,
                    fontSize = 42.sp,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }

            // Moon Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = moonPhase.phase.nameZh,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val moonAgeStr = "%.1f".format(Locale.US, moonPhase.moonAgeDays)
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${strings.moonAge} $moonAgeStr ${strings.daysUnit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Dual-tone Illumination Progress Track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        val frac = (moonPhase.illuminationPercent / 100f).coerceIn(0f, 1f)
                        if (frac > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary,
                                            ),
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                    ),
                            )
                        }
                    }
                    Text(
                        text = "${strings.moonIllumination} ${moonPhase.illuminationPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NightTimelineShowcaseCard(
    strings: ToolboxStrings,
    timeline: NightTimeline,
    onExport: () -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val windowStart = timeline.sunsetMillis - (30 * 60000L)
    val windowEnd = timeline.sunriseMillis + (30 * 60000L)
    val totalDuration = (windowEnd - windowStart).coerceAtLeast(1L)
    val duskEndFrac = ((timeline.duskAstronomicalMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
    val dawnStartFrac = ((timeline.dawnAstronomicalMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
    val nowMillis = System.currentTimeMillis()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Bar with Sunset, Deep Night, Sunrise Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TwilightTimeChip(
                    symbol = "🌅",
                    label = strings.sunsetLabel,
                    time = timeFmt.format(Date(timeline.sunsetMillis)),
                    color = Color(0xFFFFB74D),
                )

                TwilightTimeChip(
                    symbol = "🌌",
                    label = strings.darkNightLabel,
                    time = "${timeFmt.format(Date(timeline.duskAstronomicalMillis))} - ${timeFmt.format(Date(timeline.dawnAstronomicalMillis))}",
                    color = MaterialTheme.colorScheme.primary,
                )

                TwilightTimeChip(
                    symbol = "🌄",
                    label = strings.sunriseLabel,
                    time = timeFmt.format(Date(timeline.sunriseMillis)),
                    color = Color(0xFFFFB74D),
                )
            }

            // Timeline Grid Container: 100% Unified Row-by-Row Layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Top Time Ruler Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(76.dp))
                    NightTimeRulerCanvas(
                        windowStart = windowStart,
                        totalDuration = totalDuration,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }

                // 8 Celestial Body Rows (Every Row has Guaranteed Vertical Centering)
                timeline.bodies.forEach { body ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Left Column: Celestial Body Label with Centered Circle
                        Row(
                            modifier = Modifier.width(70.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        color = Color(body.body.colorRgb).copy(alpha = 0.25f),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = body.body.symbol,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                )
                            }
                            Text(
                                text = body.nameZh,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }

                        // Right Column: Single Row Gantt Track Canvas
                        SingleBodyGanttRowCanvas(
                            body = body,
                            windowStart = windowStart,
                            totalDuration = totalDuration,
                            duskEndFrac = duskEndFrac,
                            dawnStartFrac = dawnStartFrac,
                            nowMillis = nowMillis,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            // Legend & Export Button Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendItem(color = Color(0xFF4CAF50), text = "可见时段")
                    LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, text = "完全暗夜")
                    LegendItem(color = MaterialTheme.colorScheme.error, text = "当前时刻")
                }

                Text(
                    text = strings.exportImage,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onExport)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun NightTimeRulerCanvas(
    windowStart: Long,
    totalDuration: Long,
    modifier: Modifier = Modifier,
) {
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val timePaint = remember(onSurfaceVariantColor) {
        android.graphics.Paint().apply {
            color = onSurfaceVariantColor
            textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Canvas(modifier = modifier) {
        val w = size.width
        val numTicks = 5
        for (i in 0..numTicks) {
            val frac = i.toFloat() / numTicks
            val tickX = w * frac
            val tickTime = windowStart + (totalDuration * frac).toLong()

            drawContext.canvas.nativeCanvas.drawText(
                timeFmt.format(Date(tickTime)),
                tickX,
                size.height - 4f,
                timePaint,
            )
        }
    }
}

@Composable
private fun SingleBodyGanttRowCanvas(
    body: CelestialBodyVisibility,
    windowStart: Long,
    totalDuration: Long,
    duskEndFrac: Float,
    dawnStartFrac: Float,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val twilightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val errorColor = MaterialTheme.colorScheme.error

    val barBadgePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 22f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val trackTop = 2f
        val trackHeight = h - 4f

        // 1. Draw Clean Rectangular Track Background
        drawRect(
            color = trackBgColor,
            topLeft = Offset(0f, trackTop),
            size = Size(w, trackHeight),
        )

        // Dusk & Dawn Twilight Shadow Blocks
        drawRect(
            color = twilightColor,
            topLeft = Offset(0f, trackTop),
            size = Size(w * duskEndFrac, trackHeight),
        )
        drawRect(
            color = twilightColor,
            topLeft = Offset(w * dawnStartFrac, trackTop),
            size = Size(w * (1f - dawnStartFrac), trackHeight),
        )

        // Vertical Guide Grid
        val numTicks = 5
        for (i in 0..numTicks) {
            val frac = i.toFloat() / numTicks
            val tickX = w * frac
            drawLine(
                color = gridColor,
                start = Offset(tickX, trackTop),
                end = Offset(tickX, trackTop + trackHeight),
                strokeWidth = 1f,
            )
        }

        // 2. Draw Rectangular Visibility Bars (Strictly covering full track height)
        val baseColor = Color(body.body.colorRgb)

        body.visibleIntervals.forEach { interval ->
            val startFrac = ((interval.startMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
            val endFrac = ((interval.endMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
            val barLeft = w * startFrac
            val barWidth = (w * (endFrac - startFrac)).coerceAtLeast(10f)

            // Draw Clean Rectangular Bar
            drawRect(
                color = baseColor,
                topLeft = Offset(barLeft, trackTop),
                size = Size(barWidth, trackHeight),
            )

            // Readable Max Altitude Tag inside the bar
            if (barWidth > 60f && body.maxAltitudeDegrees > 0) {
                val badgeText = "%.0f°".format(Locale.US, body.maxAltitudeDegrees)
                drawContext.canvas.nativeCanvas.drawText(
                    badgeText,
                    barLeft + (barWidth / 2f),
                    trackTop + (trackHeight / 2f) + 8f,
                    barBadgePaint,
                )
            }
        }

        // 3. Current Time Needle
        val windowEnd = windowStart + totalDuration
        if (nowMillis in windowStart..windowEnd) {
            val nowFrac = ((nowMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
            val nowX = w * nowFrac

            drawLine(
                color = errorColor,
                start = Offset(nowX, trackTop),
                end = Offset(nowX, trackTop + trackHeight),
                strokeWidth = 2.5f,
            )
        }
    }
}

@Composable
private fun TwilightTimeChip(
    symbol: String,
    label: String,
    time: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = symbol, fontSize = 11.sp)
            Column {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 10.sp,
                )
                Text(
                    text = time,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CelestialBodyCard(
    context: Context,
    strings: ToolboxStrings,
    body: CelestialBodyVisibility,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val baseColor = Color(body.body.colorRgb)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Row: Avatar + Name + Magnitude + Visible Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Perfectly Centered Planet Circle Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(baseColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, baseColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = body.body.symbol,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }

                    Column {
                        Text(
                            text = "${body.nameZh}  ${body.nameEn}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        body.magnitude?.let { mag ->
                            val magStr = "%.1f".format(Locale.US, mag)
                            Text(
                                text = "${strings.visualMagnitude} $magStr mag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Visibility Status Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = if (body.isVisibleTonight) {
                                Color(0xFF2E7D32).copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (body.isVisibleTonight) {
                            val maxStr = "%.0f°".format(Locale.US, body.maxAltitudeDegrees)
                            "● ${strings.visibleTonight} ($maxStr)"
                        } else {
                            "○ ${strings.notVisibleTonight}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (body.isVisibleTonight) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 2x2 Clean Metric Tiles
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val transitStr = body.transitTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
                val riseStr = body.riseTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
                val setStr = body.setTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
                val maxAltStr = "%.1f°".format(Locale.US, body.maxAltitudeDegrees)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = "${strings.riseTime} → ${strings.setTime}",
                        value = "$riseStr → $setStr",
                        onClick = {
                            copyToClipboard(context, "${body.nameZh} 升落时刻", "$riseStr → $setStr", strings.copied(body.nameZh))
                        },
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = strings.maxAltitude,
                        value = "$maxAltStr ($transitStr)",
                        onClick = {
                            copyToClipboard(context, "${body.nameZh} 最高仰角", "$maxAltStr ($transitStr)", strings.copied(body.nameZh))
                        },
                    )
                }

                // Current Altitude and Azimuth Tile
                val altStr = "%.1f°".format(Locale.US, body.currentAltitudeDegrees)
                val azStr = "%.1f°".format(Locale.US, body.currentAzimuthDegrees)
                val directionName = azimuthToDirection(body.currentAzimuthDegrees)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        label = strings.currentAltAz,
                        value = "$altStr / $azStr ($directionName)",
                        onClick = {
                            copyToClipboard(context, "${body.nameZh} 当前高度方位", "$altStr / $azStr ($directionName)", strings.copied(body.nameZh))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun azimuthToDirection(azimuth: Double): String {
    val norm = (azimuth % 360 + 360) % 360
    return when {
        norm < 22.5 || norm >= 337.5 -> "北"
        norm < 67.5 -> "东北"
        norm < 112.5 -> "东"
        norm < 157.5 -> "东南"
        norm < 202.5 -> "南"
        norm < 247.5 -> "西南"
        norm < 292.5 -> "西"
        else -> "西北"
    }
}
