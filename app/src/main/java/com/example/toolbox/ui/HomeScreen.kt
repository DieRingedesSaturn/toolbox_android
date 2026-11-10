package com.example.toolbox.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ModuleTile(
    val module: ToolboxModule,
    val icon: Int,
)

internal object HomeModuleOrder {
    val DEFAULT: List<ToolboxModule> = listOf(
        ToolboxModule.MONITOR,
        ToolboxModule.LEDGER,
        ToolboxModule.TOTP,
        ToolboxModule.FX,
        ToolboxModule.ASTRONOMY,
        ToolboxModule.USAGE,
        ToolboxModule.DEVICE,
        ToolboxModule.LOCATION,
        ToolboxModule.NETWORK,
    )

    /** Keeps known tool modules, drops duplicates, and appends newly added modules. */
    fun parse(raw: String?): List<ToolboxModule> {
        val stored = raw.orEmpty()
            .split(',')
            .mapNotNull { name ->
                DEFAULT.firstOrNull { it.name == name.trim() }
            }
            .distinct()
        return stored + DEFAULT.filterNot { it in stored }
    }

    fun format(order: List<ToolboxModule>): String =
        order.joinToString(",") { it.name }
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val preferences = remember(context) { AppPreferences(context) }
    var moduleOrder by remember { mutableStateOf(preferences.homeModuleOrder()) }
    var showOrderDialog by rememberSaveable { mutableStateOf(false) }
    val orderedTiles = remember(moduleOrder) {
        val map = moduleTiles.associateBy { it.module }
        moduleOrder.mapNotNull { map[it] }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun updateModuleOrder(next: List<ToolboxModule>) {
        moduleOrder = next
        preferences.saveHomeModuleOrder(next)
    }

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToolboxTopBar(
                title = strings.appName,
                onBack = null,
                backLabel = strings.back,
                scrollBehavior = scrollBehavior,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.allToolsLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                        ) {
                            TextButton(
                                onClick = { showOrderDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = strings.homeModulesOrderAction,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                items(orderedTiles.chunked(columns)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { tile ->
                            ModuleTileCard(
                                strings = strings,
                                tile = tile,
                                onClick = { onOpen(tile.module) },
                                onLongClick = { showOrderDialog = true },
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

    if (showOrderDialog) {
        HomeModuleOrderDialog(
            strings = strings,
            order = moduleOrder,
            onMove = { index, offset ->
                val target = (index + offset).coerceIn(0, moduleOrder.lastIndex)
                if (target != index) {
                    val mutable = moduleOrder.toMutableList()
                    val moved = mutable.removeAt(index)
                    mutable.add(target, moved)
                    updateModuleOrder(mutable)
                }
            },
            onReset = { updateModuleOrder(HomeModuleOrder.DEFAULT) },
            onDismiss = { showOrderDialog = false },
        )
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
    val tabularTitle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    val tabularCaption = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
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
            style = tabularTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = caption,
            style = tabularCaption,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleTileCard(
    strings: ToolboxStrings,
    tile: ModuleTile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (badgeColor, badgeIconColor) = moduleBadgeColors(tile.module)
    Card(
        modifier = modifier
            .height(64.dp)
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tile.icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = badgeIconColor,
                )
            }
            Text(
                text = strings.moduleShortTitle(tile.module),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val HomeOrderRowHeight = 52.dp

@Composable
private fun HomeModuleOrderDialog(
    strings: ToolboxStrings,
    order: List<ToolboxModule>,
    onMove: (index: Int, offset: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { HomeOrderRowHeight.toPx() }
    val iconByModule = remember { moduleTiles.associate { it.module to it.icon } }
    var draggingFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val visualOffset = draggingFrom?.let { from ->
        dragOffset.coerceIn(
            -from * rowHeightPx,
            (order.lastIndex - from) * rowHeightPx,
        )
    } ?: 0f
    val targetIndex = draggingFrom?.let { from ->
        (from + (visualOffset / rowHeightPx).roundToInt()).coerceIn(0, order.lastIndex)
    }

    fun finishDrag() {
        val from = draggingFrom
        if (from != null) {
            val clamped = dragOffset.coerceIn(
                -from * rowHeightPx,
                (order.lastIndex - from) * rowHeightPx,
            )
            val to = (from + (clamped / rowHeightPx).roundToInt())
                .coerceIn(0, order.lastIndex)
            if (to != from) onMove(from, to - from)
        }
        draggingFrom = null
        dragOffset = 0f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.homeModulesOrderTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.homeModulesOrderHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    order.forEachIndexed { index, module ->
                        val isDragging = draggingFrom == index
                        val from = draggingFrom
                        val target = targetIndex
                        val shift = when {
                            from == null || target == null -> 0f
                            index == from -> visualOffset
                            target > from && index in (from + 1)..target -> -rowHeightPx
                            target < from && index in target until from -> rowHeightPx
                            else -> 0f
                        }
                        val animatedShift by animateFloatAsState(
                            targetValue = if (isDragging) 0f else shift,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "homeModuleOrderRowShift",
                        )
                        val rowOffset = if (isDragging) visualOffset else animatedShift
                        val (badgeColor, badgeIconColor) = moduleBadgeColors(module)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HomeOrderRowHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset { IntOffset(0, rowOffset.roundToInt()) }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .pointerInput(order) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingFrom = index
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = { finishDrag() },
                                        onDragCancel = {
                                            draggingFrom = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                                .semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction(
                                                    strings.ledgerOverviewMoveUp,
                                                ) {
                                                    onMove(index, -1)
                                                    true
                                                },
                                            )
                                        }
                                        if (index < order.lastIndex) {
                                            add(
                                                CustomAccessibilityAction(
                                                    strings.ledgerOverviewMoveDown,
                                                ) {
                                                    onMove(index, 1)
                                                    true
                                                },
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            iconByModule[module]?.let { iconRes ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = badgeIconColor,
                                    )
                                }
                            }
                            Text(
                                text = strings.moduleShortTitle(module),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = strings.ledgerOverviewOrderDrag,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.doneAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(strings.ledgerOverviewOrderReset)
            }
        },
    )
}

@Composable
private fun moduleBadgeColors(module: ToolboxModule): Pair<Color, Color> =
    with(MaterialTheme.colorScheme) {
        when (module) {
            ToolboxModule.LEDGER, ToolboxModule.LOCATION, ToolboxModule.TOTP ->
                tertiaryContainer to onTertiaryContainer
            ToolboxModule.ASTRONOMY, ToolboxModule.DEVICE, ToolboxModule.FX ->
                secondaryContainer to onSecondaryContainer
            else -> primaryContainer to onPrimaryContainer
        }
    }

private val moduleTiles = listOf(
    ModuleTile(ToolboxModule.MONITOR, R.drawable.ic_module_monitor),
    ModuleTile(ToolboxModule.LEDGER, R.drawable.ic_module_ledger),
    ModuleTile(ToolboxModule.TOTP, R.drawable.ic_module_totp),
    ModuleTile(ToolboxModule.FX, R.drawable.ic_module_fx),
    ModuleTile(ToolboxModule.ASTRONOMY, R.drawable.ic_module_astronomy),
    ModuleTile(ToolboxModule.USAGE, R.drawable.ic_module_usage),
    ModuleTile(ToolboxModule.DEVICE, R.drawable.ic_module_device),
    ModuleTile(ToolboxModule.LOCATION, R.drawable.ic_module_location),
    ModuleTile(ToolboxModule.NETWORK, R.drawable.ic_module_network),
)
