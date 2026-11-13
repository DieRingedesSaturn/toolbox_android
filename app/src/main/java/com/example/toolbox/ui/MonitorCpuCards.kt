package com.example.toolbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.example.toolbox.monitor.CpuCoreFrequency
import com.example.toolbox.monitor.MonitorSample
import com.example.toolbox.monitor.fillFraction
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
internal enum class CpuTopologyDisplayMode {
    FREQUENCY,
    USAGE,
}

internal enum class CpuTopologyViewType {
    MATRIX,
    GRID,
}

internal fun clusterLabel(strings: ToolboxStrings, maxMhz: Int?): String {
    val ghz = (maxMhz ?: 0) / 1000.0
    return when {
        ghz >= 3.2 -> strings.primeCore
        ghz >= 2.6 -> strings.bigCore
        ghz >= 2.0 -> strings.midCore
        else -> strings.littleCore
    }
}

internal fun buildClusterSummary(strings: ToolboxStrings, cores: List<CpuCoreFrequency>): String {
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

internal fun formatCoreGhz(mhz: Int?): String {
    val value = mhz ?: return "—"
    return String.format(Locale.US, "%.2f", value / 1000.0)
}

internal fun coreFillFraction(core: CpuCoreFrequency, displayMode: CpuTopologyDisplayMode): Float =
    core.fillFraction(displayMode == CpuTopologyDisplayMode.USAGE)

@Composable
internal fun CpuFrequencyCard(
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
                ToolboxFilterChip(
                    selected = displayMode == CpuTopologyDisplayMode.FREQUENCY,
                    onClick = {
                        displayMode = CpuTopologyDisplayMode.FREQUENCY
                        viewType = CpuTopologyViewType.MATRIX
                    },
                    label = { Text(strings.frequencyMode) },
                )
                ToolboxFilterChip(
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
internal fun CpuTopologyMatrix(
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
internal fun CpuCoreTable(
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
