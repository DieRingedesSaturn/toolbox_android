package com.example.toolbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.R
import com.example.toolbox.device.DeviceInfo
import com.example.toolbox.device.HomeStatusReader
import com.example.toolbox.device.HomeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        ToolboxModule.PRIVACY,
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

internal enum class HomeSummaryItem {
    DATE,
    DEVICE_INFO,
    BATTERY,
    STORAGE,
    MEMORY,
    DISPLAY,
    NETWORK,
    TODAY_SPEND,
    MONTH_BALANCE,
    ASSETS,
}

internal object HomeSummaryConfig {
    val DEFAULT_ITEMS: Set<HomeSummaryItem> = setOf(
        HomeSummaryItem.DEVICE_INFO,
        HomeSummaryItem.BATTERY,
        HomeSummaryItem.STORAGE,
        HomeSummaryItem.ASSETS,
        HomeSummaryItem.MONTH_BALANCE,
    )

    fun parseItems(raw: String?): Set<HomeSummaryItem> {
        if (raw == null) return DEFAULT_ITEMS
        return raw.split(',')
            .mapNotNull { token ->
                val trimmed = token.trim()
                HomeSummaryItem.entries.firstOrNull { it.name == trimmed }
            }
            .toSet()
    }

    fun formatItems(items: Set<HomeSummaryItem>): String =
        HomeSummaryItem.entries
            .filter { it in items }
            .joinToString(",") { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    status: HomeStatus?,
    onStatusChange: (HomeStatus?) -> Unit,
    onOpen: (ToolboxModule) -> Unit,
    onOpenLedgerAccounts: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditModeChange: (Boolean) -> Unit = {},
    homePrefsRevision: Int = 0,
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var layoutRaw by remember { mutableStateOf(preferences.homeGridLayout()) }
    var summaryEnabled by remember { mutableStateOf(preferences.homeSummaryEnabled()) }
    var summaryItems by remember { mutableStateOf(preferences.homeSummaryItems()) }
    var summaryCustomText by remember { mutableStateOf(preferences.homeSummaryCustomText()) }
    var editMode by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)

    fun reloadHomePrefs() {
        layoutRaw = preferences.homeGridLayout()
        summaryEnabled = preferences.homeSummaryEnabled()
        summaryItems = preferences.homeSummaryItems()
        summaryCustomText = preferences.homeSummaryCustomText()
    }

    BackHandler(enabled = editMode) {
        editMode = false
    }
    LaunchedEffect(editMode) {
        onEditModeChange(editMode)
    }
    LaunchedEffect(homePrefsRevision) {
        if (homePrefsRevision > 0) reloadHomePrefs()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadHomePrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val needsQuickStatus = summaryEnabled && (
        HomeSummaryItem.BATTERY in summaryItems ||
            HomeSummaryItem.STORAGE in summaryItems ||
            HomeSummaryItem.MEMORY in summaryItems
        )
    val needsNetworkStatus = summaryEnabled && HomeSummaryItem.NETWORK in summaryItems
    val needsLedgerStatus = summaryEnabled && (
        HomeSummaryItem.ASSETS in summaryItems ||
            HomeSummaryItem.MONTH_BALANCE in summaryItems ||
            HomeSummaryItem.TODAY_SPEND in summaryItems
        )
    val needsLiveStatus = needsQuickStatus || needsNetworkStatus || needsLedgerStatus

    LaunchedEffect(needsLiveStatus, needsQuickStatus, needsNetworkStatus, needsLedgerStatus) {
        if (!needsLiveStatus) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            currentOnStatusChange(withContext(Dispatchers.IO) {
                HomeStatusReader.read(context, needsQuickStatus, needsNetworkStatus, needsLedgerStatus)
            })
        }
    }

    Scaffold(
            modifier = Modifier.semantics {
                contentDescription = strings.homeAccessibilityTitle
                customActions = listOf(
                    CustomAccessibilityAction(strings.homeOpenSettings) {
                        onOpenSettings()
                        true
                    },
                )
            },
            floatingActionButton = {
                if (editMode) {
                    ToolboxCard(shape = RoundedCornerShape(50)) {
                        TextButton(onClick = { editMode = false }) { Text(strings.doneAction) }
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val spacing = 10.dp
                val columns = (
                    (maxWidth - 32.dp + spacing) / (150.dp + spacing)
                    ).toInt().coerceIn(2, HomeGridLayout.MAX_COLUMNS)
                val cellWidth = (
                    (maxWidth - 32.dp - spacing * (columns - 1)) / columns
                    ).coerceAtLeast(0.dp)
                val iconSize = (cellWidth * 0.24f).coerceIn(28.dp, 40.dp)
                val showSummary = summaryEnabled
                val layout = remember(layoutRaw, columns, showSummary) {
                    HomeGridLayout.parse(
                        raw = layoutRaw,
                        order = preferences.homeModuleOrder(),
                        columns = columns,
                        summaryExpected = showSummary,
                    ).reflow(columns)
                }
                LaunchedEffect(layoutRaw, columns, showSummary) {
                    if (layoutRaw == null) {
                        val initial = HomeGridLayout.default(
                            preferences.homeModuleOrder(),
                            columns,
                            showSummary,
                        )
                        preferences.saveHomeGridLayout(initial.format())
                        preferences.saveHomeModuleOrder(initial.readingOrder())
                        layoutRaw = initial.format()
                    }
                }
                val availableHeightPx = with(LocalDensity.current) {
                    (maxHeight.roundToPx() - 2 * 16.dp.roundToPx()).coerceAtLeast(0)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = if (editMode) 80.dp else HomePagerIndicatorClearance,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeModuleGrid(
                        strings = strings,
                        deviceInfo = deviceInfo,
                        status = status,
                        layout = layout,
                        columns = columns,
                        cellWidth = cellWidth,
                        spacing = spacing,
                        iconSize = iconSize,
                        availableHeightPx = availableHeightPx,
                        summaryItems = summaryItems,
                        summaryCustomText = summaryCustomText,
                        editMode = editMode,
                        onEditModeChange = { editMode = it },
                        onPersistLayout = { next ->
                            val raw = next.format()
                            layoutRaw = raw
                            preferences.saveHomeGridLayout(raw)
                            preferences.saveHomeModuleOrder(next.readingOrder())
                        },
                        onOpen = onOpen,
                        onOpenLedgerAccounts = onOpenLedgerAccounts,
                    )
                    if (editMode) {
                        Text(
                            text = strings.homeEditHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
}

@Composable
internal fun HomeSummaryConfigDialog(
    strings: ToolboxStrings,
    enabled: Boolean,
    items: Set<HomeSummaryItem>,
    customText: String,
    onEnabledChange: (Boolean) -> Unit,
    onItemsChange: (Set<HomeSummaryItem>) -> Unit,
    onCustomTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.homeSummaryConfigTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.homeSummaryEnabledLabel,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
                AnimatedVisibility(visible = enabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = onCustomTextChange,
                            label = { Text(strings.homeSummaryCustomTextLabel) },
                            placeholder = { Text(strings.homeSummaryCustomTextHint) },
                            maxLines = 3,
                            trailingIcon = if (customText.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onCustomTextChange("") }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = strings.clearAction,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = strings.homeSummaryItemsLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column {
                            HomeSummaryItem.entries.forEach { item ->
                                val label = strings.homeSummaryItemLabel(item)
                                val checked = item in items
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            val next = if (checked) items - item else items + item
                                            onItemsChange(next)
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            val next = if (isChecked) items + item else items - item
                                            onItemsChange(next)
                                        },
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        if (items.isEmpty() && customText.isBlank()) {
                            Text(
                                text = strings.homeSummaryEmptyHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    )
}
