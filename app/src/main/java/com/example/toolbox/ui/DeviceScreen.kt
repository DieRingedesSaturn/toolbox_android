package com.example.toolbox.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.toolbox.device.DeviceInfo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    isLoading: Boolean,
    lastUpdated: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val columns = if (isExpandedWidth()) 2 else 1
    val sections = DeviceSection.entries.toList().chunked(columns)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.device) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        Text(if (isLoading) strings.refreshing else strings.refresh)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (deviceInfo == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    InfoCard(strings.device) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(strings.loading, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lastUpdated != null) {
                    item {
                        Text(
                            text = strings.lastUpdated + lastUpdated,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(sections) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { section ->
                            DeviceInfoCard(
                                context = context,
                                strings = strings,
                                section = section,
                                deviceInfo = deviceInfo,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    context: Context,
    strings: ToolboxStrings,
    section: DeviceSection,
    deviceInfo: DeviceInfo,
    modifier: Modifier = Modifier,
) {
    InfoCard(
        title = strings.sectionTitle(section),
        modifier = modifier,
    ) {
        when (section) {
            DeviceSection.IDENTITY -> {
                LocalizedInfoRow(context, strings, DeviceLabel.MANUFACTURER, deviceInfo.identity.manufacturer)
                LocalizedInfoRow(context, strings, DeviceLabel.MODEL, deviceInfo.identity.model)
                LocalizedInfoRow(context, strings, DeviceLabel.DEVICE_NAME, deviceInfo.identity.deviceName)
                LocalizedInfoRow(context, strings, DeviceLabel.ANDROID, deviceInfo.identity.androidVersion)
                LocalizedInfoRow(context, strings, DeviceLabel.API_LEVEL, deviceInfo.identity.apiLevel.toString())
                LocalizedInfoRow(context, strings, DeviceLabel.KERNEL, deviceInfo.identity.kernel)
            }

            DeviceSection.CPU -> {
                LocalizedInfoRow(context, strings, DeviceLabel.SOC_CPU, deviceInfo.cpu.name)
                LocalizedInfoRow(context, strings, DeviceLabel.CORES, deviceInfo.cpu.cores.toString())
                LocalizedInfoRow(context, strings, DeviceLabel.ABI, deviceInfo.cpu.abi)
                LocalizedInfoRow(context, strings, DeviceLabel.MAX_FREQUENCY, deviceInfo.cpu.maxFrequency)
            }

            DeviceSection.GPU -> {
                LocalizedInfoRow(context, strings, DeviceLabel.OPENGL_ES, deviceInfo.gpu.openGlEs)
                LocalizedInfoRow(context, strings, DeviceLabel.VULKAN, deviceInfo.gpu.vulkan)
            }

            DeviceSection.MEMORY -> {
                LocalizedInfoRow(context, strings, DeviceLabel.TOTAL, formatBytes(deviceInfo.memory.total))
                LocalizedInfoRow(context, strings, DeviceLabel.AVAILABLE, formatBytes(deviceInfo.memory.available))
                LocalizedInfoRow(
                    context,
                    strings,
                    DeviceLabel.USED,
                    formatBytes(deviceInfo.memory.total - deviceInfo.memory.available),
                )
            }

            DeviceSection.STORAGE -> {
                LocalizedInfoRow(context, strings, DeviceLabel.TOTAL, formatBytes(deviceInfo.storage.total))
                LocalizedInfoRow(context, strings, DeviceLabel.AVAILABLE, formatBytes(deviceInfo.storage.available))
                LocalizedInfoRow(
                    context,
                    strings,
                    DeviceLabel.USED,
                    formatBytes(deviceInfo.storage.total - deviceInfo.storage.available),
                )
            }

            DeviceSection.DISPLAY -> {
                LocalizedInfoRow(context, strings, DeviceLabel.RESOLUTION, deviceInfo.display.resolution)
                LocalizedInfoRow(context, strings, DeviceLabel.DENSITY, deviceInfo.display.density)
                LocalizedInfoRow(context, strings, DeviceLabel.REFRESH_RATE, deviceInfo.display.refreshRate)
                LocalizedInfoRow(context, strings, DeviceLabel.HDR, deviceInfo.display.hdr)
            }

            DeviceSection.BATTERY -> {
                LocalizedInfoRow(context, strings, DeviceLabel.LEVEL, deviceInfo.battery.level)
                LocalizedInfoRow(context, strings, DeviceLabel.CHARGING, deviceInfo.battery.charging)
                LocalizedInfoRow(context, strings, DeviceLabel.TEMPERATURE, deviceInfo.battery.temperature)
                LocalizedInfoRow(context, strings, DeviceLabel.VOLTAGE, deviceInfo.battery.voltage)
            }
        }
    }
}

@Composable
private fun LocalizedInfoRow(
    context: Context,
    strings: ToolboxStrings,
    label: DeviceLabel,
    value: String,
) {
    val localizedLabel = strings.label(label)
    InfoRow(
        label = localizedLabel,
        value = strings.localizeValue(value),
        copyAction = strings.copy,
    ) {
        copyToClipboard(
            context = context,
            label = localizedLabel,
            value = value,
            copiedMessage = strings.copied(localizedLabel),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
