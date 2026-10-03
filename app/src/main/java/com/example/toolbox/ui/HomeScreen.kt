package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.R
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.device.DeviceInfoReader
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerStore
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ModuleTile(
    val module: ToolboxModule,
    val icon: Int,
)

internal data class HomeStatus(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val storageAvailable: Long,
    val storageTotal: Long,
    val monthExpenseCents: Long,
    val monthIncomeCents: Long,
    val totalAssetsCnyCents: Long?,
    val assetsPartial: Boolean,
)

@Composable
internal fun HomeScreen(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    status: HomeStatus?,
    onStatusChange: (HomeStatus?) -> Unit,
    onOpen: (ToolboxModule) -> Unit,
    onOpenLedgerAccounts: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            currentOnStatusChange(withContext(Dispatchers.IO) {
                runCatching {
                    val quick = DeviceInfoReader.readQuickStatus(context)
                    val store = LedgerStore(context)
                    val entries = store.queryVisibleEntries()
                    val month = YearMonth.now()
                    val summary = LedgerCalculator.monthSummary(entries, month)
                    val (totalCny, allConverted) = LedgerAccounts.totalAssetsCnyCents(
                        store.queryAccounts(includeDeleted = false),
                        entries,
                        FxRateStore(context.applicationContext).latest(),
                    )
                    HomeStatus(
                        batteryPercent = quick.batteryPercent,
                        isCharging = quick.isCharging,
                        storageAvailable = quick.storageAvailable,
                        storageTotal = quick.storageTotal,
                        monthExpenseCents = summary.expenseCents,
                        monthIncomeCents = summary.incomeCents,
                        totalAssetsCnyCents = totalCny,
                        assetsPartial = !allConverted,
                    )
                }.getOrNull()
            })
        }
    }

    Scaffold(
        topBar = {
            ToolboxTopBar(
                title = strings.appName,
                onBack = null,
                backLabel = strings.back,
                actions = {
                    IconButton(onClick = { onOpen(ToolboxModule.ABOUT) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = strings.about,
                        )
                    }
                    IconButton(onClick = { onOpen(ToolboxModule.SETTINGS) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = strings.settings,
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val columns = (
                (maxWidth - 32.dp + 12.dp) / (160.dp + 12.dp)
                ).toInt().coerceIn(2, 4)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HomeSummaryCard(
                        strings = strings,
                        deviceInfo = deviceInfo,
                        status = status,
                        onOpen = onOpen,
                        onOpenLedgerAccounts = onOpenLedgerAccounts,
                    )
                }
                item {
                    Text(
                        text = strings.allToolsLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(moduleTiles.chunked(columns)) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { tile ->
                            ModuleTileCard(
                                strings = strings,
                                tile = tile,
                                deviceInfo = deviceInfo,
                                onClick = { onOpen(tile.module) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
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
private fun HomeSummaryCard(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    status: HomeStatus?,
    onOpen: (ToolboxModule) -> Unit,
    onOpenLedgerAccounts: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = deviceInfo?.let {
                    "${it.identity.manufacturer} ${it.identity.model}"
                } ?: strings.loading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = deviceInfo?.let {
                    "Android ${it.identity.androidVersion} · API ${it.identity.apiLevel}"
                } ?: strings.loading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeStat(
                    label = strings.sectionTitle(DeviceSection.BATTERY),
                    value = if (status == null) {
                        "—"
                    } else {
                        status.batteryPercent?.let { "$it%" } ?: strings.unknown
                    },
                    caption = if (status == null) {
                        "—"
                    } else {
                        status.isCharging?.let {
                            if (it) strings.chargingNow else strings.notCharging
                        } ?: strings.unknown
                    },
                    onClick = { onOpen(ToolboxModule.DEVICE) },
                    modifier = Modifier.weight(1f),
                )
                HomeStat(
                    label = strings.sectionTitle(DeviceSection.STORAGE),
                    value = status?.let { formatBytes(it.storageAvailable) } ?: "—",
                    caption = status?.let {
                        strings.storageOfTotal(formatBytes(it.storageTotal))
                    } ?: "—",
                    onClick = { onOpen(ToolboxModule.DEVICE) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Total assets (all accounts, ≈ CNY) → Accounts tab.
                HomeStat(
                    label = strings.totalAssetsLabel,
                    value = status?.let { s ->
                        s.totalAssetsCnyCents?.let {
                            "≈${LedgerCalculator.formatCurrency(it / 100.0)}"
                        } ?: "—"
                    } ?: "—",
                    caption = status?.let { s ->
                        if (s.assetsPartial) strings.partialConversionNote else "CNY"
                    } ?: "—",
                    onClick = onOpenLedgerAccounts,
                    modifier = Modifier.weight(1f),
                )
                // This month's net (income − expense) → Ledger.
                HomeStat(
                    label = strings.monthBalanceLabel(""),
                    value = status?.let { s ->
                        val net = s.monthIncomeCents - s.monthExpenseCents
                        (if (net < 0) "−" else "+") +
                            LedgerCalculator.formatCurrency(
                                kotlin.math.abs(net) / 100.0,
                            )
                    } ?: "—",
                    caption = status?.let { s ->
                        strings.inOutCaption(
                            LedgerCalculator.formatCurrency(s.monthExpenseCents / 100.0),
                            LedgerCalculator.formatCurrency(s.monthIncomeCents / 100.0),
                        )
                    } ?: "—",
                    onClick = { onOpen(ToolboxModule.LEDGER) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeStat(
    label: String,
    value: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModuleTileCard(
    strings: ToolboxStrings,
    tile: ModuleTile,
    deviceInfo: DeviceInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (badgeColor, badgeIconColor) = moduleBadgeColors(tile.module)
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tile.icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = badgeIconColor,
                )
            }
            Text(
                text = strings.moduleTitle(tile.module),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings.moduleSummary(tile.module, deviceInfo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun moduleBadgeColors(module: ToolboxModule): Pair<Color, Color> =
    with(MaterialTheme.colorScheme) {
        when (module) {
            ToolboxModule.LEDGER, ToolboxModule.LOCATION ->
                tertiaryContainer to onTertiaryContainer
            ToolboxModule.ASTRONOMY, ToolboxModule.DEVICE, ToolboxModule.FX ->
                secondaryContainer to onSecondaryContainer
            else -> primaryContainer to onPrimaryContainer
        }
    }

private val moduleTiles = listOf(
    ModuleTile(ToolboxModule.MONITOR, R.drawable.ic_module_monitor),
    ModuleTile(ToolboxModule.LEDGER, R.drawable.ic_module_ledger),
    ModuleTile(ToolboxModule.FX, R.drawable.ic_module_fx),
    ModuleTile(ToolboxModule.ASTRONOMY, R.drawable.ic_module_astronomy),
    ModuleTile(ToolboxModule.USAGE, R.drawable.ic_module_usage),
    ModuleTile(ToolboxModule.DEVICE, R.drawable.ic_module_device),
    ModuleTile(ToolboxModule.LOCATION, R.drawable.ic_module_location),
    ModuleTile(ToolboxModule.NETWORK, R.drawable.ic_module_network),
)
