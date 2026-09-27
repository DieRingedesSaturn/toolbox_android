package com.example.toolbox.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.toolbox.network.NetworkInfo
import com.example.toolbox.network.NetworkInfoReader
import com.example.toolbox.network.PublicIpDetails
import kotlinx.coroutines.Dispatchers
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
