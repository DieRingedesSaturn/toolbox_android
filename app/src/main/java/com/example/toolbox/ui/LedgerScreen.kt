package com.example.toolbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toolbox.R
import com.example.toolbox.ledger.BackupException
import com.example.toolbox.ledger.BackupFailure
import com.example.toolbox.fx.FxException
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.fx.FxSource
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.FX_SOURCE_MANUAL
import com.example.toolbox.ledger.LINK_TYPE_SALE
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerBackup
import com.example.toolbox.ledger.LedgerBackupReminder
import com.example.toolbox.ledger.LedgerBackupStatusStore
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerCategory
import com.example.toolbox.ledger.LedgerCsv
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.ledger.LedgerSyncPayload
import com.example.toolbox.ledger.LedgerSyncSerializer
import com.example.toolbox.ledger.LedgerSyncStatus
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.LedgerTags
import com.example.toolbox.ledger.LedgerWebDavSync
import com.example.toolbox.ledger.NotificationLedgerCandidate
import com.example.toolbox.ledger.NotificationLedgerStore
import com.example.toolbox.ledger.SubscriptionRenewals
import com.example.toolbox.ledger.TagFilterMode
import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavConfig
import com.example.toolbox.ledger.WebDavConfigStore
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    openEditorRequested: Boolean = false,
    onEditorRequestHandled: () -> Unit = {},
    tabRequest: Int? = null,
    onTabRequestHandled: () -> Unit = {},
    cachedSnapshot: LedgerSnapshot? = null,
    onSnapshot: (LedgerSnapshot) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { LedgerStore(context) }
    val scope = rememberCoroutineScope()
    val currentOnSnapshot by rememberUpdatedState(onSnapshot)

    var entries by remember { mutableStateOf(cachedSnapshot?.entries.orEmpty()) }
    var allEntries by remember { mutableStateOf(cachedSnapshot?.allEntries.orEmpty()) }
    var entriesLoaded by remember { mutableStateOf(cachedSnapshot != null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(tabRequest ?: 0) }
    var editingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var pendingReplace by remember { mutableStateOf<PendingReplace?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedMonthName by rememberSaveable {
        mutableStateOf(YearMonth.now().toString())
    }
    var showWebDavDialog by rememberSaveable { mutableStateOf(false) }
    var showNotificationLedgerDialog by remember { mutableStateOf(false) }
    var notificationDraft by remember {
        mutableStateOf<Pair<NotificationLedgerCandidate, Long>?>(null)
    }
    val webDavStore = remember(context) { WebDavConfigStore(context) }
    var webDavConfigVersion by remember { mutableIntStateOf(0) }
    var webDavConfig by remember { mutableStateOf<WebDavConfig?>(null) }
    var webDavPasswordUnavailable by remember { mutableStateOf(false) }
    var webDavLastSyncAt by remember { mutableStateOf<Long?>(null) }
    var webDavStatusLoaded by remember { mutableStateOf(false) }
    val backupStatusStore = remember(context) { LedgerBackupStatusStore(context) }
    var lastFileExportAt by remember { mutableStateOf<Long?>(null) }
    var backupSnoozedUntil by remember { mutableStateOf<Long?>(null) }
    var backupStatusLoaded by remember { mutableStateOf(false) }
    var webDavBusyAction by remember { mutableStateOf<WebDavBusyAction?>(null) }
    var webDavLastResult by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf(cachedSnapshot?.tags.orEmpty()) }
    var accounts by remember { mutableStateOf(cachedSnapshot?.accounts.orEmpty()) }
    var detailAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var latestFxRates by remember { mutableStateOf(cachedSnapshot?.latestFxRates) }
    var renewalBusy by remember { mutableStateOf(false) }
    var renewalRateIssue by remember { mutableStateOf<RenewalRateIssue?>(null) }
    var nearestForIssue by remember { mutableStateOf<Map<LocalDate, FxRates>>(emptyMap()) }
    var confirmSkipDues by remember { mutableStateOf<List<DueRenewal>?>(null) }
    var selectedEntryUuids by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Non-null while the ledger search box is open; "" means "just opened".
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var bulkTagPickAdd by remember { mutableStateOf(false) }
    var bulkTagPickRemove by remember { mutableStateOf(false) }
    // True when the tags dialog opened from the ledger screen (entry rows
    // navigate to the editor); false when opened from inside the editor.
    var tagsDialogEntryNav by remember { mutableStateOf(true) }
    var disposalTarget by remember { mutableStateOf<LedgerEntry?>(null) }
    var undoDisposalTarget by remember { mutableStateOf<LedgerEntry?>(null) }
    var stopSubTarget by remember { mutableStateOf<LedgerEntry?>(null) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(webDavConfigVersion) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                webDavStore.load(),
                webDavStore.isPasswordUnavailable(),
                webDavStore.lastSyncAtMillis(),
            )
        }
        webDavConfig = loaded.first
        webDavPasswordUnavailable = loaded.second
        webDavLastSyncAt = loaded.third
        webDavStatusLoaded = true
    }

    LaunchedEffect(backupStatusStore) {
        val loaded = withContext(Dispatchers.IO) {
            backupStatusStore.lastFileExportAtMillis() to backupStatusStore.snoozedUntilMillis()
        }
        lastFileExportAt = loaded.first
        backupSnoozedUntil = loaded.second
        backupStatusLoaded = true
    }

    fun reloadEntries() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    store.queryVisibleEntries(),
                    store.queryTags(includeDeleted = true),
                    store.queryAllEntriesForSync(),
                ) to store.queryAccounts(includeDeleted = true)
            }
            entries = loaded.first.first
            tags = loaded.first.second
            allEntries = loaded.first.third
            accounts = loaded.second
            latestFxRates = withContext(Dispatchers.IO) { fxStore.latest() }
            entriesLoaded = true
            currentOnSnapshot(
                LedgerSnapshot(entries, allEntries, tags, accounts, latestFxRates),
            )
        }
    }

    LaunchedEffect(store) {
        reloadEntries()
    }

    LaunchedEffect(tabRequest) {
        if (tabRequest != null) {
            selectedTab = tabRequest
            onTabRequestHandled()
        }
    }

    LaunchedEffect(openEditorRequested) {
        if (openEditorRequested) {
            editingUuid = null
            notificationDraft = null
            showEditor = true
            onEditorRequestHandled()
        }
    }

    fun openEditor(entry: LedgerEntry?) {
        editingUuid = entry?.uuid
        notificationDraft = null
        showEditor = true
    }

    fun copyEntry(entry: LedgerEntry) {
        copyToClipboard(
            context = context,
            label = entry.title,
            value = ledgerEntryLine(
                strings,
                entry,
                entry.tagUuids.mapNotNull { uuid ->
                    tags.firstOrNull {
                        it.uuid == uuid && it.deletedAtMillis == null
                    }?.name
                },
                accounts.associate { it.uuid to it.name },
            ),
            copiedMessage = strings.copied(entry.title),
        )
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
    val overviewMonthSummary = remember(entries, currentMonth) {
        LedgerCalculator.monthSummary(entries, currentMonth)
    }
    val selectedMonth = runCatching { YearMonth.parse(selectedMonthName) }
        .getOrElse { YearMonth.now() }
    val editingEntry = editingUuid?.let { uuid ->
        entries.firstOrNull { it.uuid == uuid }
    }
    val pendingDeleteEntry = pendingDeleteUuid?.let { uuid ->
        entries.firstOrNull { it.uuid == uuid }
    }

    // The referenced entry may be gone after a reload (deleted elsewhere).
    LaunchedEffect(showEditor, editingUuid, entriesLoaded, editingEntry) {
        if (showEditor && editingUuid != null && entriesLoaded && editingEntry == null) {
            showEditor = false
            editingUuid = null
        }
    }
    LaunchedEffect(pendingDeleteUuid, pendingDeleteEntry, entriesLoaded) {
        if (pendingDeleteUuid != null && pendingDeleteEntry == null && entriesLoaded) {
            pendingDeleteUuid = null
        }
    }

    fun toggleActive(entry: LedgerEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.toggleActiveStatus(entry, !entry.isActiveCost)
            }
            reloadEntries()
        }
    }

    /** Writes the built renewal rows; shared by the record/retry paths. */
    fun commitRenewals(
        dues: List<DueRenewal>,
        ratesByDate: Map<LocalDate, FxRates>,
        manualRate: Double? = null,
    ) {
        val now = System.currentTimeMillis()
        val built = mutableListOf<LedgerEntry>()
        val stillUnresolved = mutableSetOf<LocalDate>()
        for (due in dues) {
            val sub = due.subscription
            if (sub.currency == "CNY") {
                built += SubscriptionRenewals.buildRenewal(
                    sub, due, 1.0, null, null, zone, now,
                )
                continue
            }
            val rates = ratesByDate[due.date]
            val cny = rates?.cnyPerUnit(sub.currency)?.takeIf { it > 0 }
            when {
                // The chain-resolved rate always wins; the manual input only
                // covers dates the chain could not resolve.
                cny != null -> built += SubscriptionRenewals.buildRenewal(
                    sub, due, cny, rates.date, rates.source.id, zone, now,
                )
                manualRate != null && manualRate > 0 ->
                    built += SubscriptionRenewals.buildRenewal(
                        sub, due, manualRate, null, FX_SOURCE_MANUAL, zone, now,
                    )
                else -> stillUnresolved += due.date
            }
        }
        if (stillUnresolved.isNotEmpty()) {
            // Nothing is written until every date has a rate — reopen the
            // issue dialog for the dates still missing one.
            scope.launch {
                val nearest = withContext(Dispatchers.IO) {
                    stillUnresolved.mapNotNull { date ->
                        fxStore.nearestCached(date)?.let { date to it }
                    }.toMap()
                }
                nearestForIssue = nearest
                renewalRateIssue = RenewalRateIssue(
                    dues = dues,
                    resolved = ratesByDate,
                    unresolvedDates = stillUnresolved.sorted(),
                    attempts = renewalRateIssue?.attempts.orEmpty(),
                )
            }
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) { store.insertIfAbsent(built) }
            reloadEntries()
            Toast.makeText(
                context,
                strings.renewalsRecorded(built.size),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Resolves each due date's rate via the fallback chain, then writes. */
    fun recordRenewals(dues: List<DueRenewal>) {
        if (renewalBusy) return
        renewalBusy = true
        scope.launch {
            var attempts: List<Pair<FxSource, FxFailure>> = emptyList()
            val outcome = withContext(Dispatchers.IO) {
                val foreignDates = dues
                    .filter { it.subscription.currency != "CNY" }
                    .map { it.date }
                    .toSet()
                val resolved = if (foreignDates.isEmpty()) {
                    emptyMap()
                } else {
                    try {
                        fxStore.ratesForDates(foreignDates, fetchIfMissing = true)
                    } catch (e: FxException) {
                        attempts = e.attempts
                        emptyMap()
                    }
                }
                val unresolved = foreignDates.filter { it !in resolved }.sorted()
                resolved to unresolved
            }
            renewalBusy = false
            val unresolved = outcome.second
            if (unresolved.isEmpty()) {
                commitRenewals(dues, outcome.first)
            } else {
                scope.launch {
                    val nearest = withContext(Dispatchers.IO) {
                        unresolved.mapNotNull { date ->
                            fxStore.nearestCached(date)?.let { date to it }
                        }.toMap()
                    }
                    nearestForIssue = nearest
                    renewalRateIssue = RenewalRateIssue(
                        dues = dues,
                        resolved = outcome.first,
                        unresolvedDates = unresolved,
                        attempts = attempts,
                    )
                }
            }
        }
    }

    fun skipRenewals(dues: List<DueRenewal>) {
        if (renewalBusy) return
        renewalBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                store.insertIfAbsent(
                    dues.map { SubscriptionRenewals.buildSkipMarker(it.subscription, it, zone, now) },
                )
            }
            renewalBusy = false
            reloadEntries()
        }
    }

    fun applyDisposal(
        asset: LedgerEntry,
        disposalType: String,
        date: LocalDate,
        saleCents: Long,
        currency: String,
        fxRateToCny: Double,
        fxRateDate: String?,
        fxRateSource: String?,
        saleAccountUuid: String? = null,
    ) {
        disposalTarget = null
        scope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val timeOfDay = Instant.ofEpochMilli(asset.occurredAtMillis)
                    .atZone(zone).toLocalTime()
                val retiredMillis = date.atTime(timeOfDay)
                    .atZone(zone).toInstant().toEpochMilli()
                val updatedAsset = asset.copy(
                    isActiveCost = false,
                    retiredAtMillis = retiredMillis,
                    disposalType = disposalType,
                    updatedAtMillis = now,
                    syncStatus = LedgerSyncStatus.PENDING_PUSH,
                )
                val saleAccount = saleAccountUuid?.let { uuid ->
                    accounts.firstOrNull { it.uuid == uuid }
                }
                val saleAccountCents = if (
                    saleAccount != null && saleAccount.currency != currency
                ) {
                    val saleBase = (saleCents * fxRateToCny).toLong()
                    val accRate = if (saleAccount.currency == "CNY") {
                        1.0
                    } else {
                        runCatching { fxStore.latest() }
                            .getOrNull()?.cnyPerUnit(saleAccount.currency)
                    }
                    accRate?.let {
                        LedgerAccounts.accountAmountForEntry(saleBase, 1.0, it)
                    }
                } else {
                    null
                }
                val sale = if (disposalType == DISPOSAL_SOLD) {
                    LedgerEntry(
                        uuid = SubscriptionRenewals.saleUuid(asset.uuid),
                        title = strings.soldTitlePrefix(asset.title),
                        amountCents = saleCents,
                        currency = currency,
                        fxRateToCny = fxRateToCny,
                        fxRateDate = fxRateDate,
                        fxRateSource = fxRateSource,
                        type = LedgerEntryType.INCOME,
                        category = asset.category,
                        occurredAtMillis = retiredMillis,
                        note = "",
                        parentUuid = asset.uuid,
                        linkType = LINK_TYPE_SALE,
                        tagUuids = asset.tagUuids,
                        accountUuid = saleAccountUuid ?: asset.accountUuid,
                        accountAmountCents = saleAccountCents,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        syncStatus = LedgerSyncStatus.PENDING_PUSH,
                    )
                } else {
                    null
                }
                store.applyDisposal(updatedAsset, sale)
            }
            reloadEntries()
        }
    }

    fun undoDisposal(asset: LedgerEntry) {
        undoDisposalTarget = null
        scope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val sale = allEntries.firstOrNull {
                    it.uuid == SubscriptionRenewals.saleUuid(asset.uuid)
                }
                val restored = asset.copy(
                    isActiveCost = true,
                    retiredAtMillis = null,
                    disposalType = null,
                    updatedAtMillis = now,
                    syncStatus = LedgerSyncStatus.PENDING_PUSH,
                )
                store.applyDisposal(
                    restored,
                    sale?.copy(
                        deletedAtMillis = now,
                        updatedAtMillis = now,
                        syncStatus = LedgerSyncStatus.PENDING_PUSH,
                    ),
                )
            }
            reloadEntries()
        }
    }

    fun stopSubscription(entry: LedgerEntry, date: LocalDate) {
        stopSubTarget = null
        scope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val timeOfDay = Instant.ofEpochMilli(entry.occurredAtMillis)
                    .atZone(zone).toLocalTime()
                store.applyDisposal(
                    entry.copy(
                        isActiveCost = false,
                        retiredAtMillis = date.atTime(timeOfDay)
                            .atZone(zone).toInstant().toEpochMilli(),
                        updatedAtMillis = now,
                        syncStatus = LedgerSyncStatus.PENDING_PUSH,
                    ),
                    null,
                )
            }
            reloadEntries()
        }
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        scope.launch {
            withContext(Dispatchers.IO) {
                webDavStore.save(config)
            }
            webDavConfigVersion++
        }
    }

    fun clearWebDavConfig() {
        scope.launch {
            withContext(Dispatchers.IO) {
                webDavStore.clear()
            }
            webDavLastResult = null
            webDavConfigVersion++
        }
    }

    fun webDavErrorMessage(error: Throwable): String {
        val webDavError = error as? WebDavException
        return strings.webDavError(
            webDavError?.failure ?: WebDavFailure.NETWORK,
            webDavError?.httpCode,
        )
    }

    fun runWebDavTest(config: WebDavConfig) {
        if (webDavBusyAction != null) return
        webDavBusyAction = WebDavBusyAction.TEST
        webDavLastResult = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                webDavStore.save(config)
                runCatching {
                    LedgerWebDavSync(
                        client = WebDavClient(
                            config.folderUrl,
                            config.username,
                            config.password,
                        ),
                        loadLocal = { emptyList() },
                        loadLocalTags = { emptyList() },
                        loadLocalAccounts = { emptyList() },
                        applyMerged = {},
                    ).testConnection()
                }
            }
            webDavConfigVersion++
            webDavBusyAction = null
            webDavLastResult = outcome.fold(
                onSuccess = { strings.webDavTestOk },
                onFailure = { webDavErrorMessage(it) },
            )
        }
    }

    fun runWebDavSync(config: WebDavConfig, toastResult: Boolean) {
        if (webDavBusyAction != null) return
        webDavBusyAction = WebDavBusyAction.SYNC
        webDavLastResult = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                webDavStore.save(config)
                runCatching {
                    val result = LedgerWebDavSync(
                        client = WebDavClient(
                            config.folderUrl,
                            config.username,
                            config.password,
                        ),
                        loadLocal = { store.queryAllEntriesForSync() },
                        loadLocalTags = { store.queryTags(includeDeleted = true) },
                        loadLocalAccounts = { store.queryAccounts(includeDeleted = true) },
                        applyMerged = { merged -> store.importSyncPayload(merged) },
                    ).sync()
                    webDavStore.saveLastSync(System.currentTimeMillis())
                    result
                }
            }
            webDavConfigVersion++
            webDavBusyAction = null
            outcome.onSuccess { result ->
                webDavLastResult = strings.webDavSyncSuccess(
                    total = result.totalEntries,
                    pulled = result.pulledCount,
                    pushed = result.pushedCount,
                    snapshotName = result.snapshotName,
                ) + result.snapshotFailure?.let { failure ->
                    "\n" + strings.webDavSnapshotFailed(strings.webDavError(failure))
                }.orEmpty()
                reloadEntries()
            }
            outcome.onFailure { error ->
                webDavLastResult = webDavErrorMessage(error)
            }
            if (toastResult) {
                webDavLastResult?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun backupFileName(pattern: String, suffix: String): String =
        "toolbox-ledger-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern)) +
            suffix

    fun writeTextToUri(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("openOutputStream returned null")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun exportJsonBackup(uri: Uri) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val exportedAt = System.currentTimeMillis()
                    val allRows = store.queryAllEntriesForSync()
                    writeTextToUri(
                        uri,
                        LedgerSyncSerializer.toJsonString(
                            LedgerSyncPayload(
                                entries = allRows,
                                tags = store.queryTags(includeDeleted = true),
                                accounts = store.queryAccounts(includeDeleted = true),
                            ),
                        ),
                    )
                    backupStatusStore.saveFileExport(exportedAt)
                    exportedAt to allRows.count { it.deletedAtMillis == null }
                }
            }
            outcome.onSuccess { lastFileExportAt = it.first }
            Toast.makeText(
                context,
                outcome.fold(
                    onSuccess = { strings.backupExported(it.second) },
                    onFailure = { strings.backupExportFailed },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun exportCsvBackup(uri: Uri) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val visible = store.queryVisibleEntries()
                    val liveTagNames = store.queryTags(includeDeleted = false)
                        .associate { it.uuid to it.name }
                    val accountNames = store.queryAccounts(includeDeleted = false)
                        .associate { it.uuid to it.name }
                    writeTextToUri(
                        uri,
                        LedgerCsv.build(
                            entries = visible,
                            headers = strings.csvHeaders,
                            typeLabel = { type ->
                                when (type) {
                                    LedgerEntryType.INCOME -> strings.incomeTypeLabel
                                    LedgerEntryType.EXPENSE -> strings.expenseTypeLabel
                                    LedgerEntryType.TRANSFER -> strings.transferTypeLabel
                                    LedgerEntryType.ADJUSTMENT ->
                                        strings.adjustmentTypeLabel
                                }
                            },
                            tagsLabel = { entry ->
                                entry.tagUuids
                                    .mapNotNull { liveTagNames[it] }
                                    .joinToString("、")
                            },
                            costModeLabel = { mode ->
                                when (mode) {
                                    CostTrackingMode.NONE -> strings.costModeNone
                                    CostTrackingMode.ONE_TIME_AMORTIZED ->
                                        strings.costModeOneTime
                                    CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                                        strings.costModePeriodic
                                }
                            },
                            accountLabel = { entry ->
                                entry.accountUuid?.let { accountNames[it] } ?: ""
                            },
                            toAccountLabel = { entry ->
                                entry.toAccountUuid?.let { accountNames[it] } ?: ""
                            },
                            zone = ZoneId.systemDefault(),
                        ),
                    )
                    visible.size
                }
            }
            Toast.makeText(
                context,
                outcome.fold(
                    onSuccess = { strings.backupExported(it) },
                    onFailure = { strings.backupExportFailed },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun startImport(uri: Uri) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val declaredSize = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")
                        ?.use { it.length } ?: -1L
                    if (declaredSize > LedgerBackup.MAX_BYTES) {
                        throw BackupException(BackupFailure.TOO_LARGE)
                    }
                    val text = context.contentResolver.openInputStream(uri)
                        ?.use { stream ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > LedgerBackup.MAX_BYTES) {
                                    throw BackupException(BackupFailure.TOO_LARGE)
                                }
                                out.write(buffer, 0, read)
                            }
                            String(out.toByteArray(), Charsets.UTF_8)
                        }
                        ?: throw IOException("openInputStream returned null")
                    val payload = LedgerBackup.parse(text)
                    PendingImport(
                        payload = payload,
                        preview = LedgerBackup.previewMerge(
                            store.queryAllEntriesForSync(),
                            payload.entries,
                        ),
                    )
                }
            }
            outcome.onSuccess { pendingImport = it }
            outcome.onFailure { error ->
                val message = (error as? BackupException)
                    ?.let { strings.backupError(it.failure) }
                    ?: strings.backupReadFailed
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyMergeImport(pending: PendingImport) {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.importSyncPayload(pending.payload)
            }
            pendingImport = null
            reloadEntries()
            Toast.makeText(
                context,
                strings.backupMergedDone(
                    pending.preview.added,
                    pending.preview.updated,
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun requestReplace(pending: PendingImport) {
        scope.launch {
            val plan = withContext(Dispatchers.IO) {
                LedgerBackup.planReplace(
                    store.queryAllEntriesForSync(),
                    pending.payload.entries,
                    localTags = store.queryTags(includeDeleted = true),
                    incomingTags = pending.payload.tags,
                    localAccounts = store.queryAccounts(includeDeleted = true),
                    incomingAccounts = pending.payload.accounts,
                    nowMillis = System.currentTimeMillis(),
                )
            }
            pendingReplace = PendingReplace(pending.payload, plan)
        }
    }

    fun applyReplaceImport(pending: PendingReplace) {
        scope.launch {
            val plan = withContext(Dispatchers.IO) {
                // Recompute right before writing so edits made while the
                // dialogs were open aren't silently dropped.
                val fresh = LedgerBackup.planReplace(
                    store.queryAllEntriesForSync(),
                    pending.payload.entries,
                    localTags = store.queryTags(includeDeleted = true),
                    incomingTags = pending.payload.tags,
                    localAccounts = store.queryAccounts(includeDeleted = true),
                    incomingAccounts = pending.payload.accounts,
                    nowMillis = System.currentTimeMillis(),
                )
                store.applyReplace(fresh)
                fresh
            }
            pendingReplace = null
            pendingImport = null
            reloadEntries()
            Toast.makeText(
                context,
                strings.backupReplacedDone(plan.keptCount, plan.deletedCount),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(::exportJsonBackup) }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(::exportCsvBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::startImport) }

    val searching = searchQuery != null
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    val lastBackupAt = listOfNotNull(lastFileExportAt, webDavLastSyncAt).maxOrNull()
    val backupReminderDue = remember(allEntries, tags, accounts, lastBackupAt, backupSnoozedUntil) {
        LedgerBackupReminder.isDue(
            lastBackupAtMillis = lastBackupAt,
            earliestDataAtMillis = allEntries.minOfOrNull { it.createdAtMillis },
            latestChangeAtMillis = (
                allEntries.map { it.updatedAtMillis } +
                    tags.map { it.updatedAtMillis } +
                    accounts.map { it.updatedAtMillis }
                ).filter { it > 0L }.maxOrNull(),
            snoozedUntilMillis = backupSnoozedUntil,
            nowMillis = System.currentTimeMillis(),
        )
    }
    val showBackupReminder = backupReminderDue &&
        entriesLoaded &&
        backupStatusLoaded &&
        webDavStatusLoaded

    Scaffold(
        topBar = {
            Column {
                if (searching) {
                    TopAppBar(
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                } else {
                    ToolboxTopBar(
                        title = strings.ledger,
                        onBack = onBack,
                        backLabel = strings.back,
                        actions = {
                            IconButton(
                                onClick = {
                                    selectedEntryUuids = emptySet()
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
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.webDavBackupMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            showWebDavDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.notificationLedgerTitle) },
                                        onClick = {
                                            menuExpanded = false
                                            showNotificationLedgerDialog = true
                                        },
                                    )
                                    webDavConfig?.takeIf { it.isComplete }
                                        ?.let { config ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(strings.webDavSyncNow)
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    runWebDavSync(
                                                        config,
                                                        toastResult = true,
                                                    )
                                                },
                                            )
                                        }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(strings.manageTagsMenu) },
                                        onClick = {
                                            menuExpanded = false
                                            tagsDialogEntryNav = true
                                            showTagsDialog = true
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(strings.backupExportJson) },
                                        onClick = {
                                            menuExpanded = false
                                            exportJsonLauncher.launch(
                                                backupFileName(
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
                                                backupFileName("yyyyMMdd", ".csv"),
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
                                }
                            }
                        },
                    )
                }
                if (!searching) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        listOf(
                            strings.ledgerTabOverview,
                            strings.ledgerTabTransactions,
                            strings.ledgerTabCosts,
                            strings.accountsTabLabel,
                        ).forEachIndexed { index, tabTitle ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    selectedEntryUuids = emptySet()
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
                            webDavAvailable = webDavConfig?.isComplete == true,
                            onExport = {
                                exportJsonLauncher.launch(
                                    backupFileName("yyyyMMdd-HHmmss", ".json"),
                                )
                            },
                            onWebDavSync = {
                                webDavConfig?.takeIf { it.isComplete }?.let {
                                    runWebDavSync(it, toastResult = true)
                                }
                            },
                            onLater = {
                                val until = LedgerBackupReminder.snoozeUntil(
                                    System.currentTimeMillis(),
                                )
                                backupSnoozedUntil = until
                                scope.launch(Dispatchers.IO) {
                                    backupStatusStore.saveSnoozedUntil(until)
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!searching && selectedEntryUuids.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { openEditor(null) },
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
                    onOpenEntry = ::openEditor,
                    onCopyEntry = ::copyEntry,
                )
            } else when (selectedTab) {
                0 -> LedgerOverviewTab(
                    strings = strings,
                    entries = entries,
                    summary = summary,
                    monthSummary = overviewMonthSummary,
                    tags = tags,
                    accounts = accounts.filter { it.deletedAtMillis == null },
                    pendingRenewals = pendingRenewals,
                    renewalBusy = renewalBusy,
                    onRecordRenewals = ::recordRenewals,
                    onSkipRenewals = { confirmSkipDues = it },
                    onOpenEntry = ::openEditor,
                    onCopyEntry = ::copyEntry,
                    onSeeAll = { selectedTab = 1 },
                    onShowCosts = { selectedTab = 2 },
                )

                1 -> LedgerTransactionsTab(
                    strings = strings,
                    entries = filteredEntries,
                    tags = tags,
                    accounts = accounts.filter { it.deletedAtMillis == null },
                    selectedMonth = selectedMonth,
                    filterSelected = filterSelected,
                    filterMode = filterMode,
                    onOpenFilter = { showFilterDialog = true },
                    onClearFilter = { filterTagCsv = "" },
                    onMonthChange = {
                        selectedMonthName = it.toString()
                        selectedEntryUuids = emptySet()
                    },
                    onOpenEntry = ::openEditor,
                    onCopyEntry = ::copyEntry,
                    selectedUuids = selectedEntryUuids,
                    onToggleSelect = { entry ->
                        selectedEntryUuids = if (entry.uuid in selectedEntryUuids) {
                            selectedEntryUuids - entry.uuid
                        } else {
                            selectedEntryUuids + entry.uuid
                        }
                    },
                    onClearSelection = { selectedEntryUuids = emptySet() },
                    onBulkAddTag = { bulkTagPickAdd = true },
                    onBulkRemoveTag = { bulkTagPickRemove = true },
                    onBulkCopy = {
                        val picked = entries
                            .filter { it.uuid in selectedEntryUuids }
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
                        tagsDialogEntryNav = true
                        showTagsDialog = true
                    },
                )

                2 -> LedgerCostTab(
                    strings = strings,
                    costItems = costItems,
                    summary = summary,
                    tags = tags,
                    pendingRenewals = pendingRenewals,
                    renewalBusy = renewalBusy,
                    isFiltered = filterSelected.isNotEmpty(),
                    onOpenFilter = { showFilterDialog = true },
                    onClearFilter = { filterTagCsv = "" },
                    onManageTags = {
                        tagsDialogEntryNav = true
                        showTagsDialog = true
                    },
                    onRecordRenewals = ::recordRenewals,
                    onSkipRenewals = { confirmSkipDues = it },
                    onDispose = { disposalTarget = it },
                    onUndoDisposal = { undoDisposalTarget = it },
                    onStopSubscription = { stopSubTarget = it },
                    onResume = ::toggleActive,
                    onOpenEntry = ::openEditor,
                    onCopyEntry = ::copyEntry,
                )

                3 -> LedgerAccountsTab(
                    strings = strings,
                    accounts = accounts,
                    entries = entries,
                    fxRates = latestFxRates,
                    onOpenAccount = { detailAccount = it },
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
                        detailAccount = fresh
                    },
                )
            }
        }
    }

    if (showEditor && (editingUuid == null || editingEntry != null)) {
        key(editingUuid ?: notificationDraft?.let { "${it.first.id}:${it.second}" } ?: "new") {
            LedgerEntryEditor(
                strings = strings,
                initialEntry = editingEntry,
                draftAmountCents = notificationDraft?.second,
                draftOccurredAtMillis = notificationDraft?.first?.occurredAtMillis,
                tags = tags,
                accounts = accounts,
                defaultAccountUuid = AppPreferences(context)
                    .ledgerLastAccount()
                    .ifBlank { LedgerAccounts.DEFAULT_ACCOUNT_UUID },
                parentTitle = editingEntry?.parentUuid?.let { parent ->
                    allEntries.firstOrNull { it.uuid == parent }?.title
                },
                onCreateTag = { tag ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.upsertTag(tag) }
                        reloadEntries()
                    }
                },
                // From inside the editor: manage tags without entry nav.
                onManageTags = {
                    tagsDialogEntryNav = false
                    showTagsDialog = true
                },
                onDismiss = {
                    showEditor = false
                    editingUuid = null
                    notificationDraft = null
                },
                onSave = { newEntry ->
                    val candidateToRemove = notificationDraft?.first?.id
                    showEditor = false
                    editingUuid = null
                    notificationDraft = null
                    newEntry.accountUuid?.let {
                        AppPreferences(context).saveLedgerLastAccount(it)
                    }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.upsert(newEntry)
                            candidateToRemove?.let {
                                NotificationLedgerStore(context).removeCandidate(it)
                            }
                        }
                        reloadEntries()
                    }
                },
                onDelete = { entry ->
                    pendingDeleteUuid = entry.uuid
                },
            )
        }
    }

    if (showNotificationLedgerDialog) {
        NotificationLedgerDialog(
            strings = strings,
            onSelectAmount = { candidate, amount ->
                showNotificationLedgerDialog = false
                editingUuid = null
                notificationDraft = candidate to amount
                showEditor = true
            },
            onDismiss = { showNotificationLedgerDialog = false },
        )
    }

    if (showWebDavDialog) {
        LedgerWebDavDialog(
            strings = strings,
            initialConfig = webDavConfig,
            passwordUnavailable = webDavPasswordUnavailable,
            lastSyncMillis = webDavLastSyncAt,
            pendingCount = summary.pendingSyncCount,
            busyAction = webDavBusyAction,
            lastResult = webDavLastResult,
            onSaveConfig = { config ->
                saveWebDavConfig(config)
                Toast.makeText(
                    context,
                    strings.webDavConfigSaved,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onTestConnection = ::runWebDavTest,
            onSyncNow = { runWebDavSync(it, toastResult = false) },
            onClearConfig = ::clearWebDavConfig,
            onDismiss = { showWebDavDialog = false },
        )
    }

    pendingImport?.let { pending ->
        LedgerImportDialog(
            strings = strings,
            pending = pending,
            onMerge = { applyMergeImport(pending) },
            onReplace = { requestReplace(pending) },
            onDismiss = { pendingImport = null },
        )
    }

    pendingReplace?.let { pending ->
        LedgerReplaceConfirmDialog(
            strings = strings,
            plan = pending.plan,
            onConfirm = { applyReplaceImport(pending) },
            onDismiss = { pendingReplace = null },
        )
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteUuid = null },
            title = { Text(entry.title) },
            text = { Text(strings.deleteEntryConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteUuid = null
                        showEditor = false
                        editingUuid = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.softDelete(entry.uuid)
                            }
                            reloadEntries()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteUuid = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    renewalRateIssue?.let { issue ->
        RenewalRateIssueDialog(
            strings = strings,
            issue = issue,
            nearest = nearestForIssue,
            onUseCached = { nearest ->
                renewalRateIssue = null
                commitRenewals(issue.dues, issue.resolved + nearest)
            },
            onManual = { rate ->
                renewalRateIssue = null
                commitRenewals(issue.dues, issue.resolved, manualRate = rate)
            },
            onDismiss = { renewalRateIssue = null },
        )
    }

    confirmSkipDues?.let { dues ->
        AlertDialog(
            onDismissRequest = { confirmSkipDues = null },
            title = { Text(strings.skipAction) },
            text = { Text(strings.skipRenewalsConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSkipDues = null
                        skipRenewals(dues)
                    },
                ) {
                    Text(strings.skipAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkipDues = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    disposalTarget?.let { asset ->
        LedgerDisposalDialog(
            strings = strings,
            asset = asset,
            accounts = accounts.filter { it.deletedAtMillis == null },
            onConfirm = { type, date, cents, currency, rate, rateDate, rateSource, acctUuid ->
                applyDisposal(
                    asset, type, date, cents, currency, rate, rateDate, rateSource,
                    acctUuid,
                )
            },
            onDismiss = { disposalTarget = null },
        )
    }

    undoDisposalTarget?.let { asset ->
        AlertDialog(
            onDismissRequest = { undoDisposalTarget = null },
            title = { Text(asset.title) },
            text = { Text(strings.undoDisposalConfirm) },
            confirmButton = {
                Button(onClick = { undoDisposal(asset) }) {
                    Text(strings.undoDisposalAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { undoDisposalTarget = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    stopSubTarget?.let { entry ->
        ToolboxDatePickerDialog(
            initial = LocalDate.now(zone),
            minDate = Instant.ofEpochMilli(entry.occurredAtMillis)
                .atZone(zone).toLocalDate(),
            maxDate = LocalDate.now(zone),
            strings = strings,
            onConfirm = { stopSubscription(entry, it) },
            onDismiss = { stopSubTarget = null },
        )
    }

    // Back clears an active multi-select before the screen reacts.
    if (selectedEntryUuids.isNotEmpty()) {
        BackHandler { selectedEntryUuids = emptySet() }
    }

    // While searching, Back closes the search box instead of leaving.
    if (searching) {
        BackHandler { searchQuery = null }
    }

    // Bulk-tag pickers for the transactions selection mode.
    if (bulkTagPickAdd || bulkTagPickRemove) {
        val pickerTags = tags
            .filter { it.deletedAtMillis == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        val candidateUuids = if (bulkTagPickAdd) {
            pickerTags.map { it.uuid }.toSet()
        } else {
            // Removing offers only live tags present on the selection.
            entries.filter { it.uuid in selectedEntryUuids }
                .flatMapTo(HashSet()) { it.tagUuids }
                .filterTo(HashSet()) { uuid ->
                    pickerTags.any { it.uuid == uuid }
                }
        }
        AlertDialog(
            onDismissRequest = {
                bulkTagPickAdd = false
                bulkTagPickRemove = false
            },
            title = {
                Text(
                    if (bulkTagPickAdd) {
                        strings.addTagsAction
                    } else {
                        strings.removeTagsAction
                    },
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    pickerTags
                        .filter { it.uuid in candidateUuids }
                        .forEach { tag ->
                            Text(
                                text = "${tag.emoji.ifBlank { "🏷" }} ${tag.name}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val isAdd = bulkTagPickAdd
                                        val uuids = selectedEntryUuids
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                store.updateEntryTags(
                                                    uuids,
                                                    add = if (isAdd) tag.uuid else null,
                                                    remove = if (isAdd) null else tag.uuid,
                                                )
                                            }
                                            reloadEntries()
                                        }
                                        bulkTagPickAdd = false
                                        bulkTagPickRemove = false
                                        selectedEntryUuids = emptySet()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        bulkTagPickAdd = false
                        bulkTagPickRemove = false
                    },
                ) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    if (showTagsDialog) {
        LedgerTagsDialog(
            strings = strings,
            tags = tags,
            entries = entries,
            accounts = accounts.filter { it.deletedAtMillis == null },
            onUpsert = { tag ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.upsertTag(tag) }
                    reloadEntries()
                }
            },
            onDelete = { tag ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.softDeleteTag(tag.uuid) }
                    reloadEntries()
                }
            },
            onReorder = { ordered ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.reorderTags(ordered) }
                    reloadEntries()
                }
            },
            onMerge = { from, to ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.mergeTag(from, to) }
                    reloadEntries()
                }
            },
            onOpenEntry = if (tagsDialogEntryNav) { entry ->
                showTagsDialog = false
                openEditor(entry)
            } else {
                null
            },
            onDismiss = { showTagsDialog = false },
        )
    }

    detailAccount?.let { account ->
        LedgerAccountDetail(
            strings = strings,
            account = account,
            entries = allEntries,
            tags = tags,
            accounts = accounts.filter { it.deletedAtMillis == null },
            onReconcile = { acc, actualCents ->
                scope.launch {
                    val diff = withContext(Dispatchers.IO) {
                        val all = store.queryAllEntriesForSync()
                        actualCents - LedgerAccounts.balance(acc, all)
                    }
                    if (diff == 0L) {
                        Toast.makeText(
                            context,
                            strings.balanceMatchesToast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            store.upsert(
                                LedgerEntry(
                                    uuid = UUID.randomUUID().toString(),
                                    title = strings.adjustmentTitleText,
                                    amountCents = diff,
                                    currency = acc.currency,
                                    fxRateToCny = 1.0,
                                    type = LedgerEntryType.ADJUSTMENT,
                                    category = LedgerCategory.OTHER.name,
                                    occurredAtMillis = System.currentTimeMillis(),
                                    note = "",
                                    accountUuid = acc.uuid,
                                ),
                            )
                        }
                        Toast.makeText(
                            context,
                            strings.adjustmentTitleText,
                            Toast.LENGTH_SHORT,
                        ).show()
                        reloadEntries()
                    }
                }
            },
            onSave = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.upsertAccount(updated) }
                    detailAccount = updated
                    reloadEntries()
                }
            },
            onArchive = { acc, archived ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.upsertAccount(acc.copy(isArchived = archived))
                    }
                    detailAccount = null
                    reloadEntries()
                }
            },
            onDelete = { acc ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.softDeleteAccount(acc.uuid) }
                    detailAccount = null
                    reloadEntries()
                }
            },
            onOpenEntry = ::openEditor,
            onCopyEntry = ::copyEntry,
            onDismiss = { detailAccount = null },
        )
    }

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
