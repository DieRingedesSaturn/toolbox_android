package com.example.toolbox.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toolbox.R
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerBackupReminder
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.NotificationBookkeeping
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerQuickPicks
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.ledger.LedgerUpgradeBackupException
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.LedgerTags
import com.example.toolbox.ledger.SubscriptionRenewals
import com.example.toolbox.ledger.TagFilterMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LEDGER_TAB_COUNT = 4

internal data class LedgerSnapshot(
    val entries: List<LedgerEntry>,
    val allEntries: List<LedgerEntry>,
    val tags: List<LedgerTag>,
    val accounts: List<LedgerAccount>,
    val latestFxRates: FxRates?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    openEditorRequested: Boolean = false,
    onEditorRequestHandled: () -> Unit = {},
    tabRequest: Int? = null,
    onTabRequestHandled: () -> Unit = {},
    cachedSnapshot: LedgerSnapshot? = null,
    onSnapshot: (LedgerSnapshot) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { LedgerStore(context) }
    DisposableEffect(store) {
        onDispose { store.close() }
    }
    val preferences = remember(context) { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val currentOnSnapshot by rememberUpdatedState(onSnapshot)

    var entries by remember { mutableStateOf(cachedSnapshot?.entries.orEmpty()) }
    var allEntries by remember { mutableStateOf(cachedSnapshot?.allEntries.orEmpty()) }
    var entriesLoaded by remember { mutableStateOf(cachedSnapshot != null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(tabRequest ?: 0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedMonthName by rememberSaveable {
        mutableStateOf(YearMonth.now().toString())
    }
    var tags by remember { mutableStateOf(cachedSnapshot?.tags.orEmpty()) }
    var accounts by remember { mutableStateOf(cachedSnapshot?.accounts.orEmpty()) }
    var latestFxRates by remember { mutableStateOf(cachedSnapshot?.latestFxRates) }
    // Non-null while the ledger search box is open; "" means "just opened".
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    // True when the tags dialog opened from the ledger screen (entry rows
    // navigate to the editor); false when opened from inside the editor.
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSnapshotDialog by rememberSaveable { mutableStateOf(false) }
    var filterTagCsv by rememberSaveable { mutableStateOf("") }
    var filterModeName by rememberSaveable {
        mutableStateOf(TagFilterMode.ANY.name)
    }
    val filterSelected = filterTagCsv.split(',')
        .filter { it.isNotBlank() }
        .toSet()
    val filterMode = runCatching { TagFilterMode.valueOf(filterModeName) }
        .getOrDefault(TagFilterMode.ANY)
    val zone = ZoneId.systemDefault()
    val fxStore = remember(context) { FxRateStore(context.applicationContext) }

    fun reloadEntries() {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    LedgerSnapshot(
                        entries = store.queryVisibleEntries(),
                        allEntries = store.queryAllEntriesForSync(),
                        tags = store.queryTags(includeDeleted = true),
                        accounts = store.queryAccounts(includeDeleted = true),
                        latestFxRates = fxStore.latest(),
                    )
                }
            }
            outcome.onSuccess { loaded ->
                entries = loaded.entries
                tags = loaded.tags
                allEntries = loaded.allEntries
                accounts = loaded.accounts
                latestFxRates = loaded.latestFxRates
                entriesLoaded = true
                loadError = null
                currentOnSnapshot(loaded)
            }.onFailure { loadError = it }
        }
    }

    LaunchedEffect(store) {
        reloadEntries()
    }

    val managementSession = remember(context, store, strings) {
        LedgerManagementSession(
            context, store, scope, strings,
            { LedgerSnapshot(entries, allEntries, tags, accounts, latestFxRates) },
            preferences.ledgerOverviewOrder(), ::reloadEntries,
        )
    }
    val editorSaver = LedgerEditorSession.saver(
        context, store, scope, strings,
        snapshot = { LedgerSnapshot(entries, allEntries, tags, accounts, latestFxRates) },
        loaded = { entriesLoaded },
        onChanged = ::reloadEntries,
        onManageTags = {
            managementSession.tagsDialogEntryNav = false
            managementSession.showTagsDialog = true
        },
    )
    val editorSession = rememberSaveable(context, store, strings, saver = editorSaver) {
        LedgerEditorSession(
            context, store, scope, strings,
            { LedgerSnapshot(entries, allEntries, tags, accounts, latestFxRates) }, { entriesLoaded },
            ::reloadEntries,
            {
                managementSession.tagsDialogEntryNav = false
                managementSession.showTagsDialog = true
            },
        )
    }
    val backupSession = remember(context, store, strings) {
        LedgerBackupSession(context, store, scope, strings, ::reloadEntries, onOpenSettings)
    }
    val costSession = remember(context, store, strings) {
        LedgerCostSession(context, store, scope, strings, { accounts }, { allEntries }, ::reloadEntries)
    }
    LaunchedEffect(managementSession) { managementSession.initialize() }
    LaunchedEffect(backupSession) { backupSession.initialize() }
    LaunchedEffect(backupSession.webDavConfigVersion) { backupSession.reloadStatus() }
    LaunchedEffect(tabRequest) {
        if (tabRequest != null) {
            selectedTab = tabRequest
            onTabRequestHandled()
        }
    }

    val pagerState = rememberPagerState(initialPage = selectedTab) { LEDGER_TAB_COUNT }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != selectedTab) {
                selectedTab = page
                managementSession.selectedEntryUuids = emptySet()
            }
        }
    }

    LaunchedEffect(openEditorRequested) {
        if (openEditorRequested) {
            editorSession.openEditor(null)
            onEditorRequestHandled()
        }
    }

    val nowMillis = remember(entries) { System.currentTimeMillis() }
    val filteredEntries = remember(entries, filterSelected, filterMode) {
        entries.filter { LedgerTags.matches(it, filterSelected, filterMode) }
    }
    val searchResults = remember(entries, tags, accounts, searchQuery) {
        searchQuery?.let {
            LedgerCalculator.searchEntries(entries, it, tags, accounts)
        }
    }
    val childrenByParent = remember(entries) { LedgerCalculator.childrenByParent(entries) }
    val summary = remember(entries, tags, nowMillis) {
        LedgerCalculator.summarize(entries, nowMillis, tags = tags, accounts = accounts)
    }
    val costItems = remember(filteredEntries, childrenByParent, nowMillis) {
        filteredEntries.mapNotNull { entry ->
            if (entry.parentUuid != null) return@mapNotNull null
            val breakdown = LedgerCalculator.calculateCostBreakdown(
                entry,
                nowMillis,
                childrenByParent[entry.uuid].orEmpty(),
            )
            if (breakdown != null) Pair(entry, breakdown) else null
        }
    }
    val pendingRenewals = remember(allEntries) {
        SubscriptionRenewals.dueRenewals(
            entries = allEntries,
            today = LocalDate.now(zone),
            zone = zone,
        ).groupBy { it.subscription }
    }
    val currentMonth = remember(entries) { YearMonth.now() }
    val overviewMonthSummary = remember(allEntries, currentMonth) {
        LedgerCalculator.monthSummary(allEntries, currentMonth)
    }
    val selectedMonth = runCatching { YearMonth.parse(selectedMonthName) }
        .getOrElse { YearMonth.now() }
    // The referenced entry may be gone after a reload (deleted elsewhere).
    LaunchedEffect(editorSession.showEditor, editorSession.editingUuid, entriesLoaded, editorSession.editingEntry) {
        if (editorSession.showEditor && editorSession.editingUuid != null && entriesLoaded && editorSession.editingEntry == null) {
            editorSession.showEditor = false
            editorSession.editingUuid = null
        }
    }
    LaunchedEffect(editorSession.pendingDeleteUuid, editorSession.pendingDeleteEntry, entriesLoaded) {
        if (editorSession.pendingDeleteUuid != null && editorSession.pendingDeleteEntry == null && entriesLoaded) {
            editorSession.pendingDeleteUuid = null
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(backupSession::exportJsonBackup) }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(backupSession::exportCsvBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(backupSession::startImport) }

    val searching = searchQuery != null
    BackHandler(enabled = searching) {
        searchQuery = null
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    val lastBackupAt = listOfNotNull(backupSession.lastFileExportAt, backupSession.webDavLastSyncAt).maxOrNull()
    val backupReminderDue = remember(allEntries, tags, accounts, lastBackupAt, backupSession.backupSnoozedUntil) {
        LedgerBackupReminder.isDue(
            lastBackupAtMillis = lastBackupAt,
            earliestDataAtMillis = allEntries.minOfOrNull { it.createdAtMillis },
            latestChangeAtMillis = (
                allEntries.map { it.updatedAtMillis } +
                    tags.map { it.updatedAtMillis } +
                    accounts.map { it.updatedAtMillis }
                ).filter { it > 0L }.maxOrNull(),
            snoozedUntilMillis = backupSession.backupSnoozedUntil,
            nowMillis = System.currentTimeMillis(),
        )
    }
    val showBackupReminder = backupReminderDue &&
        entriesLoaded &&
        backupSession.backupStatusLoaded &&
        backupSession.webDavStatusLoaded

    if (loadError != null) {
        Scaffold(
            topBar = {
                ToolboxTopBar(
                    title = strings.moduleTitle(ToolboxModule.LEDGER),
                    onBack = onBack,
                    backLabel = strings.back,
                )
            },
        ) { padding ->
            InfoCard(
                title = strings.ledgerOpenFailed,
                modifier = Modifier.padding(padding).padding(16.dp),
            ) {
                Text(
                    if (loadError is LedgerUpgradeBackupException) {
                        strings.ledgerUpgradeBackupFailed
                    } else {
                        strings.ledgerOpenFailedHint
                    },
                )
                TextButton(onClick = ::reloadEntries) { Text(strings.refresh) }
            }
        }
        return
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (searching) {
                    TopAppBar(
                        modifier = Modifier.acrylicSurface(
                            shape = RectangleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        title = {
                            TextField(
                                value = searchQuery.orEmpty(),
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = strings.searchFieldHint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                trailingIcon = {
                                    if (!searchQuery.isNullOrEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                painter = painterResource(
                                                    R.drawable.ic_close,
                                                ),
                                                contentDescription =
                                                    strings.cancelAction,
                                            )
                                        }
                                    }
                                },
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { searchQuery = null }) {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.ic_arrow_back,
                                    ),
                                    contentDescription = strings.back,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    )
                } else {
                    ToolboxTopBar(
                        title = strings.ledger,
                        onBack = onBack,
                        backLabel = strings.back,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(
                                onClick = {
                                    managementSession.selectedEntryUuids = emptySet()
                                    searchQuery = ""
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = strings.searchAction,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(
                                            R.drawable.ic_more_vert,
                                        ),
                                        contentDescription = strings.moreOptions,
                                    )
                                }
                                ToolboxDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.webDavBackupMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSettings()
                                        },
                                    )
                                    if (NotificationBookkeeping.ENABLED) {
                                        DropdownMenuItem(
                                            text = { Text(strings.notificationLedgerTitle) },
                                            onClick = {
                                                menuExpanded = false
                                                editorSession.showNotificationLedgerDialog = true
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(strings.backupHistoryTitle) },
                                        onClick = { menuExpanded = false; backupSession.showBackupHistory = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.backupPasswordTitle) },
                                        onClick = {
                                            menuExpanded = false
                                            backupSession.pendingBackupAction = { backupSession.ledgerPassphrase = it }
                                        },
                                    )
                                    if (backupSession.webDavTargets.any { it.isComplete }) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(strings.webDavSyncNow)
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                backupSession.runSelectedWebDavSync(toastResult = true)
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(strings.manageTagsMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            managementSession.tagsDialogEntryNav = true
                                            managementSession.showTagsDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.budgetMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            managementSession.showBudgetDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.ledgerOverviewOrderMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            managementSession.showOverviewOrderDialog = true
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(strings.backupExportJson) },
                                        onClick = {
                                            menuExpanded = false
                                            exportJsonLauncher.launch(
                                                backupSession.backupFileName(
                                                    "yyyyMMdd-HHmmss",
                                                    ".json",
                                                ),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.backupExportCsv) },
                                        onClick = {
                                            menuExpanded = false
                                            exportCsvLauncher.launch(
                                                backupSession.backupFileName("yyyyMMdd", ".csv"),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.backupImport) },
                                        onClick = {
                                            menuExpanded = false
                                            importLauncher.launch(
                                                arrayOf(
                                                    "application/json",
                                                    "text/plain",
                                                    "application/octet-stream",
                                                ),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.snapshotRestoreMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            showSnapshotDialog = true
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                if (!searching) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.acrylicSurface(
                            shape = RectangleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        containerColor = Color.Transparent,
                        divider = {},
                    ) {
                        listOf(
                            strings.ledgerTabOverview,
                            strings.ledgerTabTransactions,
                            strings.ledgerTabCosts,
                            strings.accountsTabLabel,
                        ).forEachIndexed { index, tabTitle ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    selectedTab = index
                                    managementSession.selectedEntryUuids = emptySet()
                                },
                                text = {
                                    Text(
                                        text = tabTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                    AnimatedVisibility(visible = showBackupReminder) {
                        LedgerBackupReminderCard(
                            strings = strings,
                            daysSinceBackup = lastBackupAt?.let {
                                LedgerBackupReminder.daysSince(it, System.currentTimeMillis())
                            },
                            webDavAvailable = backupSession.webDavTargets.any { it.isComplete },
                            onExport = {
                                exportJsonLauncher.launch(
                                    backupSession.backupFileName("yyyyMMdd-HHmmss", ".json"),
                                )
                            },
                            onWebDavSync = {
                                backupSession.runSelectedWebDavSync(toastResult = true)
                            },
                            onLater = {
                                val until = LedgerBackupReminder.snoozeUntil(
                                    System.currentTimeMillis(),
                                )
                                backupSession.backupSnoozedUntil = until
                                scope.launch(Dispatchers.IO) {
                                    backupSession.backupStatusStore.saveSnoozedUntil(until)
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!searching && managementSession.selectedEntryUuids.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { editorSession.openEditor(null) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                        )
                    },
                    text = { Text(strings.addEntryShort) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searching) {
                LedgerSearchResults(
                    strings = strings,
                    results = searchResults.orEmpty(),
                    tags = tags,
                    accounts = accounts.filter { it.deletedAtMillis == null },
                    onOpenEntry = editorSession::openEditor,
                    onCopyEntry = editorSession::copyEntry,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = managementSession.selectedEntryUuids.isEmpty(),
                ) { page ->
                    when (page) {
                        0 -> LedgerOverviewTab(
                            strings = strings,
                            entries = entries,
                            summary = summary,
                            monthSummary = overviewMonthSummary,
                            tags = tags,
                            accounts = accounts.filter { it.deletedAtMillis == null },
                            pendingRenewals = pendingRenewals,
                            renewalBusy = costSession.renewalBusy,
                            onRecordRenewals = costSession::recordRenewals,
                            onSkipRenewals = { costSession.confirmSkipDues = it },
                            onOpenEntry = editorSession::openEditor,
                            onCopyEntry = editorSession::copyEntry,
                            onSeeAll = { selectedTab = 1 },
                            onShowCosts = { selectedTab = 2 },
                            onOpenMonth = { month ->
                                selectedMonthName = month.toString()
                                managementSession.selectedEntryUuids = emptySet()
                                selectedTab = 1
                            },
                            budgets = managementSession.budgets,
                            onEditBudgets = { managementSession.showBudgetDialog = true },
                            cardOrder = managementSession.overviewOrder,
                        )

                        1 -> LedgerTransactionsTab(
                            strings = strings,
                            entries = filteredEntries,
                            referenceEntries = allEntries,
                            tags = tags,
                            accounts = accounts.filter { it.deletedAtMillis == null },
                            selectedMonth = selectedMonth,
                            filterSelected = filterSelected,
                            filterMode = filterMode,
                            onOpenFilter = { showFilterDialog = true },
                            onClearFilter = { filterTagCsv = "" },
                            onMonthChange = {
                                selectedMonthName = it.toString()
                                managementSession.selectedEntryUuids = emptySet()
                            },
                            onOpenEntry = editorSession::openEditor,
                            onCopyEntry = editorSession::copyEntry,
                            selectedUuids = managementSession.selectedEntryUuids,
                            onToggleSelect = { entry ->
                                managementSession.selectedEntryUuids = if (entry.uuid in managementSession.selectedEntryUuids) {
                                    managementSession.selectedEntryUuids - entry.uuid
                                } else {
                                    managementSession.selectedEntryUuids + entry.uuid
                                }
                            },
                            onClearSelection = { managementSession.selectedEntryUuids = emptySet() },
                            onBulkAddTag = { managementSession.bulkTagPickAdd = true },
                            onBulkRemoveTag = { managementSession.bulkTagPickRemove = true },
                            onBulkCopy = {
                                val picked = entries
                                    .filter { it.uuid in managementSession.selectedEntryUuids }
                                    .sortedBy { it.occurredAtMillis }
                                if (picked.isNotEmpty()) {
                                    copyToClipboard(
                                        context = context,
                                        label = strings.appName,
                                        value = picked.joinToString("\n\n") {
                                            ledgerEntryLine(
                                                strings,
                                                it,
                                                it.tagUuids.mapNotNull { uuid ->
                                                    tags.firstOrNull { tag ->
                                                        tag.uuid == uuid &&
                                                            tag.deletedAtMillis == null
                                                    }?.name
                                                },
                                                accounts.associate { a -> a.uuid to a.name },
                                            )
                                        },
                                    )
                                }
                            },
                            onManageTags = {
                                managementSession.tagsDialogEntryNav = true
                                managementSession.showTagsDialog = true
                            },
                        )

                        2 -> LedgerCostTab(
                            strings = strings,
                            costItems = costItems,
                            summary = summary,
                            tags = tags,
                            pendingRenewals = pendingRenewals,
                            renewalBusy = costSession.renewalBusy,
                            filterSelectedCount = filterSelected.size,
                            onOpenFilter = { showFilterDialog = true },
                            onClearFilter = { filterTagCsv = "" },
                            onManageTags = {
                                managementSession.tagsDialogEntryNav = true
                                managementSession.showTagsDialog = true
                            },
                            onRecordRenewals = costSession::recordRenewals,
                            onSkipRenewals = { costSession.confirmSkipDues = it },
                            onDispose = { entry, startAsSale ->
                                costSession.disposalStartAsSale = startAsSale
                                costSession.disposalTarget = entry
                            },
                            onUndoDisposal = { costSession.undoDisposalTarget = it },
                            onStopSubscription = { costSession.stopSubTarget = it },
                            onResume = costSession::toggleActive,
                            onOpenEntry = editorSession::openEditor,
                            onCopyEntry = editorSession::copyEntry,
                        )

                        3 -> LedgerAccountsTab(
                            strings = strings,
                            accounts = accounts,
                            entries = entries,
                            fxRates = latestFxRates,
                            onOpenAccount = { managementSession.detailAccount = it },
                            onNewAccount = {
                                val fresh = LedgerAccount(
                                    uuid = UUID.randomUUID().toString(),
                                    name = "",
                                    currency = "CNY",
                                    openingBalanceCents = 0L,
                                    openingAtMillis = System.currentTimeMillis(),
                                    colorArgb = TAG_COLOR_PALETTE[
                                        (accounts.size) % TAG_COLOR_PALETTE.size
                                    ],
                                    sortOrder = accounts.maxOfOrNull { it.sortOrder }
                                        ?.plus(1) ?: 0,
                                )
                                managementSession.detailAccount = fresh
                            },
                        )
                    }
                }
            }
        }
    }

    editorSession.Host()

    LedgerBackupHost(backupSession)

    if (showSnapshotDialog) {
        LedgerSnapshotHost(backupSession, onDismiss = { showSnapshotDialog = false })
    }

    costSession.Host()

    managementSession.Host(editorSession::openEditor, editorSession::copyEntry)

    if (showFilterDialog) {
        TagFilterDialog(
            strings = strings,
            tags = tags,
            selected = filterSelected,
            mode = filterMode,
            onApply = { sel, mode ->
                filterTagCsv = sel.joinToString(",")
                filterModeName = mode.name
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }
}
