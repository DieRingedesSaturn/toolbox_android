package com.example.toolbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.toolbox.R
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.ledger.LedgerCalculator
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ModuleTile(
    val module: ToolboxModule,
    val icon: Int,
)

private data class HomeGridDrop(
    val row: Int,
    val col: Int,
)

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
    ModuleTile(ToolboxModule.PRIVACY, R.drawable.ic_module_privacy),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeModuleGrid(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    status: HomeStatus?,
    layout: HomeGridLayout,
    columns: Int,
    cellWidth: Dp,
    spacing: Dp,
    iconSize: Dp,
    availableHeightPx: Int,
    summaryItems: Set<HomeSummaryItem>,
    summaryCustomText: String,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onPersistLayout: (HomeGridLayout) -> Unit,
    onOpen: (ToolboxModule) -> Unit,
    onOpenLedgerAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tilesByModule = remember { moduleTiles.associateBy { it.module } }
    val gridScope = rememberCoroutineScope()
    val measurement = remember { HomeGridMeasurement() }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var dragFromCell by remember { mutableStateOf<HomeGridCell?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var flashModules by remember { mutableStateOf<Set<ToolboxModule>>(emptySet()) }
    var flashGeneration by remember { mutableIntStateOf(0) }
    var summaryDragging by remember { mutableStateOf(false) }
    var summaryDragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(editMode) {
        if (!editMode) {
            dragFromCell = null
            dragOffset = Offset.Zero
            summaryDragging = false
            summaryDragOffset = 0f
        }
    }

    val dragStepX = with(density) { (cellWidth + spacing).toPx() }.coerceAtLeast(1f)
    val dragStepY = with(density) { (64.dp + spacing).toPx() }.coerceAtLeast(1f)
    fun rowAt(from: Int, offsetY: Float): Int =
        measurement.geometry?.targetRow(from, offsetY)
            ?: (from + (offsetY / dragStepY).roundToInt()).coerceAtLeast(0)

    val rawDragRow = dragFromCell?.let { rowAt(it.row, dragOffset.y) }
    val gridRows = maxOf(layout.rows, (rawDragRow ?: -1) + 1)
    val drop = dragFromCell?.let { from ->
        toolDropTarget(
            from = from,
            offset = dragOffset,
            dragStepX = dragStepX,
            gridRows = gridRows,
            columns = columns,
            summaryRow = layout.summaryRow,
            rowAt = ::rowAt,
        )
    }

    fun flashCards(modules: Collection<ToolboxModule>) {
        if (modules.isEmpty()) return
        val generation = ++flashGeneration
        flashModules = modules.toSet()
        gridScope.launch {
            delay(450)
            if (generation == flashGeneration) flashModules = emptySet()
        }
    }

    fun enterEditMode() {
        if (editMode) return
        onEditModeChange(true)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun exitEditMode() {
        if (!editMode) return
        onEditModeChange(false)
    }

    fun moveCell(cell: HomeGridCell, rowDelta: Int, colDelta: Int): Boolean {
        val row = cell.row + rowDelta
        val col = cell.col + colDelta
        if (row < 0 || col < 0 || col >= columns || row >= layout.rows) return false
        if (row == layout.summaryRow) return false
        onPersistLayout(layout.moveTo(cell.module, row, col))
        return true
    }

    fun moveSummary(rowDelta: Int): Boolean {
        val from = layout.summaryRow ?: return false
        val next = layout.withSummaryRow(from + rowDelta)
        if (next == layout) return false
        onPersistLayout(next)
        return true
    }

    val trimmedCustomText = summaryCustomText.trim()
    val blankRows = (0 until gridRows).filterTo(mutableSetOf()) { row ->
        row != layout.summaryRow && layout.cells.none { it.row == row }
    }

    HomeGridRows(
        blankRows = blankRows,
        settledRowCount = layout.rows,
        availableHeight = availableHeightPx,
        measurement = measurement,
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(gridRows) { row ->
            val summaryRow = layout.summaryRow
            val draggingRow = dragFromCell?.row == row ||
                (summaryDragging && summaryRow != null && row == summaryRow)
            Row(
                modifier = if (draggingRow) Modifier.zIndex(1f) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (summaryRow != null && row == summaryRow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                if (summaryDragging) {
                                    IntOffset(0, summaryDragOffset.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .then(
                                if (editMode) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (editMode) {
                                    Modifier.pointerInput(summaryRow, dragStepY) {
                                        detectDragGestures(
                                            onDragStart = {
                                                summaryDragging = true
                                                summaryDragOffset = 0f
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                summaryDragOffset += amount.y
                                            },
                                            onDragEnd = {
                                                val target = rowAt(summaryRow, summaryDragOffset)
                                                summaryDragging = false
                                                summaryDragOffset = 0f
                                                onPersistLayout(
                                                    layout.withSummaryRow(
                                                        target.coerceIn(0, layout.rows),
                                                    ),
                                                )
                                            },
                                            onDragCancel = {
                                                summaryDragging = false
                                                summaryDragOffset = 0f
                                            },
                                        )
                                    }
                                } else {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(onLongPress = { enterEditMode() })
                                    }
                                },
                            )
                            .semantics {
                                if (editMode) {
                                    customActions = listOf(
                                        CustomAccessibilityAction(strings.homeMoveUp) {
                                            moveSummary(-1)
                                        },
                                        CustomAccessibilityAction(strings.homeMoveDown) {
                                            moveSummary(1)
                                        },
                                    )
                                }
                            },
                    ) {
                        HomeSummaryCard(
                            strings = strings,
                            deviceInfo = deviceInfo,
                            status = status,
                            visibleItems = summaryItems,
                            customText = trimmedCustomText,
                            interactionEnabled = !editMode,
                            onOpen = onOpen,
                            onOpenLedgerAccounts = onOpenLedgerAccounts,
                        )
                    }
                } else {
                    repeat(columns) { col ->
                        val cell = layout.cellAt(row, col)
                        val isTarget = drop != null && row == drop.row && col == drop.col
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .heightIn(min = 64.dp),
                        ) {
                            if (isTarget && cell == null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        ),
                                )
                            }
                            val tile = cell?.let { tilesByModule[it.module] }
                            if (cell != null && tile != null) {
                                val dragging = cell.module == dragFromCell?.module
                                val flashing = cell.module in flashModules
                                val interaction = if (editMode) {
                                    Modifier
                                        .clickable {
                                            exitEditMode()
                                            onOpen(tile.module)
                                        }
                                        .pointerInput(
                                            cell.module,
                                            layout,
                                            dragStepX,
                                            dragStepY,
                                        ) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragFromCell = cell
                                                    dragOffset = Offset.Zero
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.LongPress,
                                                    )
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount
                                                },
                                                onDragEnd = {
                                                    val from = dragFromCell
                                                    val offset = dragOffset
                                                    dragFromCell = null
                                                    dragOffset = Offset.Zero
                                                    if (from != null) {
                                                        val rows = maxOf(
                                                            layout.rows,
                                                            rowAt(from.row, offset.y) + 1,
                                                        )
                                                        val target = toolDropTarget(
                                                            from = from,
                                                            offset = offset,
                                                            dragStepX = dragStepX,
                                                            gridRows = rows,
                                                            columns = columns,
                                                            summaryRow = layout.summaryRow,
                                                            rowAt = ::rowAt,
                                                        )
                                                        val occupant = layout.cellAt(
                                                            target.row,
                                                            target.col,
                                                        )?.module
                                                        val next = layout.moveTo(
                                                            from.module,
                                                            target.row,
                                                            target.col,
                                                        )
                                                        if (next != layout) {
                                                            onPersistLayout(next)
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.LongPress,
                                                            )
                                                            flashCards(
                                                                listOfNotNull(
                                                                    from.module,
                                                                    occupant?.takeIf {
                                                                        it != from.module
                                                                    },
                                                                ),
                                                            )
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragFromCell = null
                                                    dragOffset = Offset.Zero
                                                },
                                            )
                                        }
                                } else {
                                    Modifier.combinedClickable(
                                        onClick = { onOpen(tile.module) },
                                        onLongClick = { enterEditMode() },
                                    )
                                }
                                ModuleTileCard(
                                    strings = strings,
                                    tile = tile,
                                    iconSize = iconSize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (dragging) Modifier.zIndex(1f) else Modifier,
                                        )
                                        .offset {
                                            if (dragging) {
                                                IntOffset(
                                                    dragOffset.x.roundToInt(),
                                                    dragOffset.y.roundToInt(),
                                                )
                                            } else {
                                                IntOffset.Zero
                                            }
                                        }
                                        .graphicsLayer {
                                            val scale = when {
                                                dragging -> 1.04f
                                                flashing -> 1.03f
                                                else -> 1f
                                            }
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .then(
                                            when {
                                                flashing -> Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = MaterialTheme.shapes.medium,
                                                )
                                                editMode -> Modifier.border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                        .copy(alpha = 0.45f),
                                                    shape = MaterialTheme.shapes.medium,
                                                )
                                                else -> Modifier
                                            },
                                        )
                                        .then(interaction)
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(
                                                    strings.homeMoveLeft,
                                                ) { moveCell(cell, 0, -1) },
                                                CustomAccessibilityAction(
                                                    strings.homeMoveRight,
                                                ) { moveCell(cell, 0, 1) },
                                                CustomAccessibilityAction(
                                                    strings.homeMoveUp,
                                                ) { moveCell(cell, -1, 0) },
                                                CustomAccessibilityAction(
                                                    strings.homeMoveDown,
                                                ) { moveCell(cell, 1, 0) },
                                            )
                                        },
                                )
                            }
                            if (isTarget && cell != null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.medium,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toolDropTarget(
    from: HomeGridCell,
    offset: Offset,
    dragStepX: Float,
    gridRows: Int,
    columns: Int,
    summaryRow: Int?,
    rowAt: (Int, Float) -> Int,
): HomeGridDrop {
    val maxRow = (gridRows - 1).coerceAtLeast(0)
    return HomeGridDrop(
        row = adjustedTargetRow(
            raw = rowAt(from.row, offset.y).coerceIn(0, maxRow),
            from = from.row,
            maxRow = maxRow,
            summaryRow = summaryRow,
        ),
        col = (from.col + (offset.x / dragStepX).roundToInt()).coerceIn(0, columns - 1),
    )
}

private fun adjustedTargetRow(
    raw: Int,
    from: Int,
    maxRow: Int,
    summaryRow: Int?,
): Int {
    val row = raw.coerceIn(0, maxRow)
    if (summaryRow == null || row != summaryRow) return row
    val direction = if (row >= from) 1 else -1
    val shifted = (summaryRow + direction).coerceIn(0, maxRow)
    return if (shifted == summaryRow) from.coerceIn(0, maxRow) else shifted
}

private data class HomeStatTile(
    val label: String,
    val value: String,
    val caption: String,
    val onClick: () -> Unit,
)

@Composable
private fun HomeSummaryCard(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    status: HomeStatus?,
    visibleItems: Set<HomeSummaryItem>,
    customText: String,
    interactionEnabled: Boolean,
    onOpen: (ToolboxModule) -> Unit,
    onOpenLedgerAccounts: () -> Unit,
) {
    val statTiles = buildList {
        if (HomeSummaryItem.BATTERY in visibleItems) {
            add(
                HomeStatTile(
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
                ),
            )
        }
        if (HomeSummaryItem.STORAGE in visibleItems) {
            add(
                HomeStatTile(
                    label = strings.sectionTitle(DeviceSection.STORAGE),
                    value = status?.let { formatBytes(it.storageAvailable) } ?: "—",
                    caption = status?.let {
                        strings.storageOfTotal(formatBytes(it.storageTotal))
                    } ?: "—",
                    onClick = { onOpen(ToolboxModule.DEVICE) },
                ),
            )
        }
        if (HomeSummaryItem.MEMORY in visibleItems) {
            val available = status?.memoryAvailable?.takeIf { it > 0 }
                ?: deviceInfo?.memory?.available
            val total = status?.memoryTotal?.takeIf { it > 0 }
                ?: deviceInfo?.memory?.total
            add(
                HomeStatTile(
                    label = strings.sectionTitle(DeviceSection.MEMORY),
                    value = available?.let { formatBytes(it) } ?: "—",
                    caption = total?.let { strings.storageOfTotal(formatBytes(it)) } ?: "—",
                    onClick = { onOpen(ToolboxModule.DEVICE) },
                ),
            )
        }
        if (HomeSummaryItem.DISPLAY in visibleItems) {
            add(
                HomeStatTile(
                    label = strings.sectionTitle(DeviceSection.DISPLAY),
                    value = deviceInfo?.display?.resolution ?: strings.loading,
                    caption = deviceInfo?.display?.refreshRate ?: "—",
                    onClick = { onOpen(ToolboxModule.DEVICE) },
                ),
            )
        }
        if (HomeSummaryItem.NETWORK in visibleItems) {
            add(
                HomeStatTile(
                    label = strings.networkConnectionTitle,
                    value = when {
                        status == null -> "—"
                        !status.networkConnected -> strings.disconnected
                        status.networkTransports.isEmpty() -> strings.connected
                        else -> status.networkTransports.joinToString(" · ") {
                            strings.networkTransport(it)
                        }
                    },
                    caption = status?.networkAddress ?: "—",
                    onClick = { onOpen(ToolboxModule.NETWORK) },
                ),
            )
        }
        if (HomeSummaryItem.TODAY_SPEND in visibleItems) {
            add(
                HomeStatTile(
                    label = strings.homeSummaryItemTodaySpend,
                    value = status?.let { s ->
                        val net = s.todayIncomeCents - s.todayExpenseCents
                        (if (net < 0) "−" else "+") +
                            LedgerCalculator.formatCurrency(kotlin.math.abs(net) / 100.0)
                    } ?: "—",
                    caption = status?.let { s ->
                        strings.inOutCaption(
                            LedgerCalculator.formatCurrency(s.todayExpenseCents / 100.0),
                            LedgerCalculator.formatCurrency(s.todayIncomeCents / 100.0),
                        )
                    } ?: "—",
                    onClick = { onOpen(ToolboxModule.LEDGER) },
                ),
            )
        }
        if (HomeSummaryItem.ASSETS in visibleItems) {
            add(
                HomeStatTile(
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
                ),
            )
        }
        if (HomeSummaryItem.MONTH_BALANCE in visibleItems) {
            add(
                HomeStatTile(
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
                ),
            )
        }
    }

    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (HomeSummaryItem.DATE in visibleItems) {
                Text(
                    text = strings.homeSummaryDate(LocalDate.now()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (HomeSummaryItem.DEVICE_INFO in visibleItems) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                }
            }
            if (customText.isNotEmpty()) {
                Text(
                    text = customText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            statTiles.chunked(2).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTiles.forEach { tile ->
                        HomeStat(
                            label = tile.label,
                            value = tile.value,
                            caption = tile.caption,
                            onClick = tile.onClick,
                            interactionEnabled = interactionEnabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
    interactionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabularTitle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    val tabularCaption = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (interactionEnabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
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

@Composable
private fun ModuleTileCard(
    strings: ToolboxStrings,
    tile: ModuleTile,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val (badgeColor, badgeIconColor) = moduleBadgeColors(tile.module)
    ToolboxCard(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tile.icon),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize * 0.53f),
                    tint = badgeIconColor,
                )
            }
            Text(
                text = strings.moduleShortTitle(tile.module),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
