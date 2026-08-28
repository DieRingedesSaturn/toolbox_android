package com.example.toolbox.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.toolbox.usage.AppCategory
import com.example.toolbox.usage.AppUsageItem
import com.example.toolbox.usage.AppUsageReader
import com.example.toolbox.usage.UsageReport
import com.example.toolbox.usage.UsageSortMode
import com.example.toolbox.usage.UsageTimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(AppUsageReader.hasUsagePermission(context)) }
    var timeRange by remember { mutableStateOf(UsageTimeRange.TODAY) }
    var sortMode by remember { mutableStateOf(UsageSortMode.DURATION) }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    // Re-check permission when app resumes from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = AppUsageReader.hasUsagePermission(context)
                hasPermission = granted
                if (granted && report == null) {
                    refreshCounter++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission, timeRange, refreshCounter) {
        if (hasPermission) {
            isLoading = true
            report = AppUsageReader.queryUsageReport(context, timeRange)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.appUsage) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    if (hasPermission) {
                        TextButton(
                            onClick = { refreshCounter++ },
                            enabled = !isLoading,
                        ) {
                            Text(if (isLoading) strings.refreshing else strings.refresh)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!hasPermission) {
            PermissionGuideView(
                strings = strings,
                onGrantClick = { context.startActivity(AppUsageReader.getUsageSettingsIntent()) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            )
        } else {
            val rawItems = report?.items ?: emptyList()
            val filteredItems = remember(rawItems, selectedCategory, sortMode) {
                val byCategory = if (selectedCategory == AppCategory.ALL) {
                    rawItems
                } else {
                    rawItems.filter { it.category == selectedCategory }
                }
                when (sortMode) {
                    UsageSortMode.DURATION -> byCategory.sortedByDescending { it.foregroundDurationMillis }
                    UsageSortMode.TOTAL_DATA -> byCategory.sortedByDescending { it.totalBytes }
                    UsageSortMode.CELLULAR_DATA -> byCategory.sortedByDescending { it.totalCellularBytes }
                    UsageSortMode.WIFI_DATA -> byCategory.sortedByDescending { it.totalWifiBytes }
                }
            }

            val maxMetricValue = remember(filteredItems, sortMode) {
                when (sortMode) {
                    UsageSortMode.DURATION -> filteredItems.maxOfOrNull { it.foregroundDurationMillis } ?: 1L
                    UsageSortMode.TOTAL_DATA -> filteredItems.maxOfOrNull { it.totalBytes } ?: 1L
                    UsageSortMode.CELLULAR_DATA -> filteredItems.maxOfOrNull { it.totalCellularBytes } ?: 1L
                    UsageSortMode.WIFI_DATA -> filteredItems.maxOfOrNull { it.totalWifiBytes } ?: 1L
                }.coerceAtLeast(1L)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Time Range Selector Segment
                item {
                    TimeRangeSegmentedBar(
                        selectedRange = timeRange,
                        onSelectRange = { timeRange = it },
                    )
                }

                // 2. Overview Hero Card
                item {
                    report?.let { rep ->
                        UsageOverviewCard(
                            strings = strings,
                            report = rep,
                        )
                    }
                }

                // 3. Category Filter Chips Row
                item {
                    CategoryFilterRow(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        report = report,
                    )
                }

                // 4. Sort Tabs Row (Duration / Total Traffic / Cellular / Wi-Fi)
                item {
                    SortModeTabs(
                        sortMode = sortMode,
                        onSelectMode = { sortMode = it },
                    )
                }

                // 5. App Usage List
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Text(
                                text = strings.noUsageData,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                } else {
                    items(filteredItems, key = { it.packageName }) { appItem ->
                        AppUsageListItem(
                            context = context,
                            strings = strings,
                            item = appItem,
                            sortMode = sortMode,
                            maxMetricValue = maxMetricValue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGuideView(
    strings: ToolboxStrings,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "📊",
                    fontSize = 48.sp,
                )
                Text(
                    text = strings.usagePermissionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = strings.usagePermissionDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.openUsageSettings)
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSegmentedBar(
    selectedRange: UsageTimeRange,
    onSelectRange: (UsageTimeRange) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UsageTimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    )
                    .clickable { onSelectRange(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = range.nameZh,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageOverviewCard(
    strings: ToolboxStrings,
    report: UsageReport,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 3 Big Metric Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewMetricItem(
                    label = strings.totalScreenTime,
                    value = formatDuration(report.totalScreenDurationMillis),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.2f),
                )
                OverviewMetricItem(
                    label = strings.totalCellularData,
                    value = formatBytes(report.totalCellularBytes),
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetricItem(
                    label = strings.totalWifiData,
                    value = formatBytes(report.totalWifiBytes),
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                )
            }

            // Category Segmented Distribution Bar
            if (report.totalScreenDurationMillis > 0 && report.categorySummaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.categoryBreakdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Multi-color Segmented Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        report.categorySummaries.forEach { summary ->
                            val frac = summary.totalDurationMillis.toFloat() / report.totalScreenDurationMillis
                            if (frac > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .weight(frac)
                                        .fillMaxHeight()
                                        .background(Color(summary.category.colorRgb)),
                                )
                            }
                        }
                    }

                    // Legend Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        report.categorySummaries.take(5).forEach { summary ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(summary.category.colorRgb), CircleShape),
                                )
                                Text(
                                    text = "${summary.category.nameZh} ${formatDuration(summary.totalDurationMillis)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: AppCategory,
    onSelectCategory: (AppCategory) -> Unit,
    report: UsageReport?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            val count = if (category == AppCategory.ALL) {
                report?.items?.size ?: 0
            } else {
                report?.categorySummaries?.find { it.category == category }?.appCount ?: 0
            }

            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = if (category == AppCategory.ALL) {
                            "${category.emoji} ${category.nameZh} ($count)"
                        } else {
                            "${category.emoji} ${category.nameZh} $count"
                        },
                        fontSize = 12.sp,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(category.colorRgb).copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun SortModeTabs(
    sortMode: UsageSortMode,
    onSelectMode: (UsageSortMode) -> Unit,
) {
    TabRow(
        selectedTabIndex = sortMode.ordinal,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        UsageSortMode.entries.forEach { mode ->
            Tab(
                selected = sortMode == mode,
                onClick = { onSelectMode(mode) },
                text = {
                    Text(
                        text = mode.nameZh,
                        fontSize = 12.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun AppUsageListItem(
    context: Context,
    strings: ToolboxStrings,
    item: AppUsageItem,
    sortMode: UsageSortMode,
    maxMetricValue: Long,
) {
    val pm = context.packageManager
    val iconBitmap = remember(item.packageName) {
        runCatching {
            val iconDrawable = pm.getApplicationIcon(item.packageName)
            iconDrawable.toBitmap(width = 80, height = 80).asImageBitmap()
        }.getOrNull()
    }

    val primaryText = when (sortMode) {
        UsageSortMode.DURATION -> formatDuration(item.foregroundDurationMillis)
        UsageSortMode.TOTAL_DATA -> formatBytes(item.totalBytes)
        UsageSortMode.CELLULAR_DATA -> formatBytes(item.totalCellularBytes)
        UsageSortMode.WIFI_DATA -> formatBytes(item.totalWifiBytes)
    }

    val currentMetric = when (sortMode) {
        UsageSortMode.DURATION -> item.foregroundDurationMillis
        UsageSortMode.TOTAL_DATA -> item.totalBytes
        UsageSortMode.CELLULAR_DATA -> item.totalCellularBytes
        UsageSortMode.WIFI_DATA -> item.totalWifiBytes
    }

    val fraction = (currentMetric.toFloat() / maxMetricValue.toFloat()).coerceIn(0.01f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val lastUsed = if (item.lastTimeUsedMillis > 0) timeFmt.format(Date(item.lastTimeUsedMillis)) else "--"
                val copyText = buildString {
                    appendLine("应用：${item.label} (${item.packageName})")
                    appendLine("分类：${item.category.emoji} ${item.category.nameZh}")
                    appendLine("屏幕使用时长：${formatDuration(item.foregroundDurationMillis)}")
                    appendLine("移动蜂窝流量：${formatBytes(item.totalCellularBytes)}")
                    appendLine("WLAN 流量：${formatBytes(item.totalWifiBytes)}")
                    appendLine("总消耗流量：${formatBytes(item.totalBytes)}")
                    append("最后使用时间：$lastUsed")
                }
                copyToClipboard(context, item.label, copyText, strings.copied(item.label))
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // App Icon
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = item.label,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(item.category.colorRgb).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = item.category.emoji, fontSize = 20.sp)
                    }
                }

                // Name + Category Pill
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(item.category.colorRgb).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "${item.category.emoji} ${item.category.nameZh}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(item.category.colorRgb),
                            )
                        }
                    }

                    // Secondary info (Cellular vs Wi-Fi)
                    Text(
                        text = "蜂窝: ${formatBytes(item.totalCellularBytes)}  ·  WLAN: ${formatBytes(item.totalWifiBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Primary Value (Right)
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Relative Usage Progress Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            color = Color(item.category.colorRgb),
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 60000L) {
        val secs = millis / 1000L
        return if (secs > 0) "$secs 秒" else "< 1 秒"
    }
    val mins = millis / 60000L
    val hours = mins / 60L
    val remMins = mins % 60L
    return if (hours > 0) {
        "${hours}小时 ${remMins}分"
    } else {
        "$remMins 分钟"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(Locale.US, gb)
        mb >= 1.0 -> "%.1f MB".format(Locale.US, mb)
        kb >= 1.0 -> "%.0f KB".format(Locale.US, kb)
        else -> "$bytes B"
    }
}
