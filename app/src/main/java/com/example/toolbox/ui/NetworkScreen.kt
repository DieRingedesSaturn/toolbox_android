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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.toolbox.network.LanDevice
import com.example.toolbox.network.LanFlag
import com.example.toolbox.network.LanScan
import com.example.toolbox.network.LanScanPhase
import com.example.toolbox.network.LanScanProgress
import com.example.toolbox.network.LanScanResult
import com.example.toolbox.network.LanScanner
import com.example.toolbox.network.LanServiceSource
import com.example.toolbox.network.LanSuspicion
import com.example.toolbox.network.NetworkInfo
import com.example.toolbox.network.NetworkInfoReader
import com.example.toolbox.network.PublicIpDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NetworkScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val reader = remember(context) { NetworkInfoReader(context) }
    val scope = rememberCoroutineScope()
    var networkInfo by remember { mutableStateOf<NetworkInfo?>(null) }
    var isLoadingLocal by remember { mutableStateOf(false) }
    var isLoadingPublicIp by remember { mutableStateOf(false) }
    var publicIpRequested by remember { mutableStateOf(false) }
    var publicIpDetails by remember { mutableStateOf<PublicIpDetails?>(null) }
    val lanScanner = remember(context) { LanScanner(context) }
    var lanScanJob by remember { mutableStateOf<Job?>(null) }
    var lanProgress by remember { mutableStateOf<LanScanProgress?>(null) }
    var lanResult by remember { mutableStateOf<LanScanResult?>(null) }
    var lanNoNetwork by remember { mutableStateOf(false) }
    var lanFailed by remember { mutableStateOf(false) }

    fun refreshLocal() {
        if (isLoadingLocal) return
        isLoadingLocal = true
        scope.launch {
            networkInfo = runCatching {
                withContext(Dispatchers.IO) { reader.readLocal() }
            }.getOrNull()
            isLoadingLocal = false
        }
    }

    fun queryPublicIp() {
        if (isLoadingPublicIp) return
        publicIpRequested = true
        isLoadingPublicIp = true
        scope.launch {
            publicIpDetails = runCatching {
                reader.readPublicIpDetails()
            }.getOrNull()
            isLoadingPublicIp = false
        }
    }

    fun startLanScan() {
        if (lanScanJob != null) {
            lanScanJob?.cancel()
            lanScanJob = null
            return
        }
        lanProgress = LanScanProgress(LanScanPhase.DISCOVERING_HOSTS)
        lanNoNetwork = false
        lanFailed = false
        lanScanJob = scope.launch {
            val result = try {
                lanScanner.scan { progress -> lanProgress = progress }
            } catch (_: kotlinx.coroutines.CancellationException) {
                lanScanJob = null
                return@launch
            } catch (_: Exception) {
                lanScanJob = null
                lanFailed = true
                return@launch
            }
            lanScanJob = null
            if (result == null) {
                lanNoNetwork = true
            } else {
                lanResult = result
            }
        }
    }

    LaunchedEffect(reader) { refreshLocal() }

    Scaffold(
        topBar = {
            ToolboxTopBar(
                title = strings.network,
                onBack = onBack,
                backLabel = strings.back,
                actions = {
                    TextButton(onClick = ::refreshLocal, enabled = !isLoadingLocal) {
                        Text(if (isLoadingLocal) strings.refreshing else strings.refresh)
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
                InfoCard(strings.network) {
                    Text(strings.networkDescription, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                NetworkDetailsCard(
                    context = context,
                    strings = strings,
                    info = networkInfo,
                )
            }
            item {
                InfoCard(strings.publicIpTitle) {
                    val ipValue = when {
                        isLoadingPublicIp -> strings.loading
                        !publicIpRequested -> strings.publicIpNotRequested
                        publicIpDetails != null -> publicIpDetails!!.ip
                        else -> strings.notAvailable
                    }
                    CopyableNetworkRow(context, strings, strings.publicIp, ipValue)
                    if (publicIpDetails != null) {
                        publicIpDetails?.formatLocation()?.let { loc ->
                            CopyableNetworkRow(context, strings, strings.publicIpLocation, loc)
                        }
                        publicIpDetails?.isp?.let { isp ->
                            CopyableNetworkRow(context, strings, strings.publicIpIsp, isp)
                        }
                    }
                    Text(
                        text = strings.publicIpDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = ::queryPublicIp,
                        enabled = !isLoadingPublicIp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoadingPublicIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(if (isLoadingPublicIp) strings.loading else strings.queryPublicIp)
                    }
                }
            }
            item {
                LanScanCard(
                    strings = strings,
                    running = lanScanJob != null,
                    progress = lanProgress,
                    result = lanResult,
                    noNetwork = lanNoNetwork,
                    failed = lanFailed,
                    onScan = ::startLanScan,
                )
            }
            items(lanResult?.devices.orEmpty(), key = { it.ip }) { device ->
                LanDeviceCard(context, strings, device)
            }
            item {
                InfoCard(strings.networkPermissionTitle) {
                    Text(
                        strings.networkPermissionDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDetailsCard(
    context: Context,
    strings: ToolboxStrings,
    info: NetworkInfo?,
) {
    InfoCard(strings.networkConnectionTitle) {
        if (info == null) {
            Text(strings.loading, style = MaterialTheme.typography.bodyMedium)
            return@InfoCard
        }
        CopyableNetworkRow(
            context,
            strings,
            strings.networkConnection,
            if (info.isConnected) strings.connected else strings.disconnected,
        )
        CopyableNetworkRow(
            context,
            strings,
            strings.networkTransport,
            info.transports.takeUnless { it.isEmpty() }
                ?.joinToString(", ") { strings.networkTransport(it) }
                ?: strings.notAvailable,
        )
        CopyableNetworkRow(context, strings, strings.networkInterface, info.interfaceName ?: strings.notAvailable)
        CopyableNetworkRow(
            context,
            strings,
            strings.networkMetered,
            info.isMetered?.let { if (it) strings.yes else strings.no } ?: strings.notAvailable,
        )
        CopyableNetworkRow(
            context,
            strings,
            strings.localAddresses,
            info.localAddresses.takeUnless { it.isEmpty() }?.joinToString("\n") ?: strings.notAvailable,
        )
        CopyableNetworkRow(
            context,
            strings,
            strings.dnsServers,
            info.dnsServers.takeUnless { it.isEmpty() }?.joinToString("\n") ?: strings.notAvailable,
        )
        CopyableNetworkRow(
            context,
            strings,
            strings.gateways,
            info.gateways.takeUnless { it.isEmpty() }?.joinToString("\n") ?: strings.notAvailable,
        )
    }
}

@Composable
private fun CopyableNetworkRow(
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

@Composable
private fun LanScanCard(
    strings: ToolboxStrings,
    running: Boolean,
    progress: LanScanProgress?,
    result: LanScanResult?,
    noNetwork: Boolean,
    failed: Boolean,
    onScan: () -> Unit,
) {
    InfoCard(strings.lanScanTitle) {
        Text(
            strings.lanScanHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val phaseText = when {
            !running && failed -> strings.lanScanFailed
            !running && noNetwork -> strings.lanScanNoNetwork
            !running && result != null ->
                strings.lanScanFound(result.devices.size, result.subnet)
            !running -> strings.lanScanNotRun
            progress?.phase == LanScanPhase.SCANNING_PORTS ->
                strings.lanScanPortProgress(progress.scannedHosts, progress.foundHosts)
            progress?.phase == LanScanPhase.LISTENING_SERVICES -> strings.lanScanListening
            else -> strings.lanScanDiscovering
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(phaseText, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) strings.lanScanStop else strings.lanScanAction)
        }
    }
}

@Composable
private fun LanDeviceCard(
    context: Context,
    strings: ToolboxStrings,
    device: LanDevice,
) {
    val suspicion = LanScan.suspicion(device)
    val flags = LanScan.flagsFor(device)
    val vendor = LanScan.cameraVendorForMac(device.mac)
    val title = buildString {
        append(device.ip)
        if (device.isSelf) append(" · ${strings.lanDeviceSelf}")
        if (device.isGateway) append(" · ${strings.lanDeviceGateway}")
    }
    InfoCard(title) {
        CopyableNetworkRow(context, strings, "IP", device.ip)
        device.mac?.let { CopyableNetworkRow(context, strings, strings.lanDeviceMac, it) }
        device.hostname?.let {
            CopyableNetworkRow(context, strings, strings.lanDeviceHostname, it)
        }
        vendor?.let { CopyableNetworkRow(context, strings, strings.lanDeviceVendor, it) }
        if (device.openPorts.isNotEmpty()) {
            CopyableNetworkRow(
                context,
                strings,
                strings.lanDeviceOpenPorts,
                device.openPorts.joinToString("  ") { port -> portLabel(port) },
            )
        }
        if (device.services.isNotEmpty()) {
            CopyableNetworkRow(
                context,
                strings,
                strings.lanDeviceServices,
                device.services.joinToString("\n") { service ->
                    val source = when (service.source) {
                        LanServiceSource.MDNS -> "mDNS"
                        LanServiceSource.SSDP -> "SSDP"
                        LanServiceSource.ONVIF_WS_DISCOVERY -> "ONVIF"
                    }
                    listOfNotNull(
                        source,
                        service.type,
                        service.name,
                        service.port?.toString(),
                    ).joinToString(" ")
                },
            )
        }
        flags.forEach { flag ->
            Text(
                flagText(strings, flag, device, vendor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (suspicion) {
            LanSuspicion.LIKELY_CAMERA -> Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    strings.lanSuspicionLikely,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LanSuspicion.NOTEWORTHY -> Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    strings.lanSuspicionNoteworthy,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LanSuspicion.NONE -> Unit
        }
    }
}

private fun portLabel(port: Int): String = when {
    port in LanScan.RTSP_PORTS -> "$port·RTSP"
    LanScan.CAMERA_VENDOR_PORTS[port] != null ->
        "$port·${LanScan.CAMERA_VENDOR_PORTS[port]}"
    else -> port.toString()
}

private fun flagText(
    strings: ToolboxStrings,
    flag: LanFlag,
    device: LanDevice,
    vendor: String?,
): String = when (flag) {
    LanFlag.ONVIF_SERVICE -> strings.lanReasonOnvif
    LanFlag.RTSP_PORT -> strings.lanReasonRtsp(
        device.openPorts.filter { it in LanScan.RTSP_PORTS }.joinToString("/"),
    )
    LanFlag.CAMERA_VENDOR_PORT -> strings.lanReasonCameraPort(
        device.openPorts.mapNotNull { port ->
            LanScan.CAMERA_VENDOR_PORTS[port]?.let { "$port·$it" }
        }.joinToString(", "),
    )
    LanFlag.CAMERA_VENDOR_NIC -> strings.lanReasonVendor(vendor ?: "")
    LanFlag.WEB_ADMIN_ONLY -> strings.lanReasonWebAdmin
}
