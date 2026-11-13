package com.example.toolbox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import com.example.toolbox.monitor.MonitorMetric
import com.example.toolbox.monitor.MonitorSample
import com.example.toolbox.monitor.chartValue
import com.example.toolbox.monitor.frequencyLoadPercent
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThermalCard(
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
internal fun FpsCard(
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
internal fun CopyableMonitorRow(
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

internal fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

@Composable
internal fun MonitorLiveMetrics(
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
internal fun MonitorMetricRow(
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
internal fun MonitorSparkline(
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

internal fun liveMetricLabel(strings: ToolboxStrings, metric: MonitorMetric): String = when (metric) {
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
