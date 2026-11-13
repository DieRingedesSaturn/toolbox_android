package com.example.toolbox.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.toolbox.fx.FxException
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.fx.FxSource
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.FX_SOURCE_MANUAL
import com.example.toolbox.ledger.LINK_TYPE_SALE
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.ledger.LedgerSyncStatus
import com.example.toolbox.ledger.SubscriptionRenewals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renewal and disposal transactions with screen-scoped progress and confirmation state. */
internal class LedgerCostSession(
    private val context: Context,
    private val store: LedgerStore,
    private val scope: CoroutineScope,
    private val strings: ToolboxStrings,
    private val loadAccounts: () -> List<LedgerAccount>,
    private val loadEntries: () -> List<LedgerEntry>,
    private val onChanged: () -> Unit,
) {
    private val zone = ZoneId.systemDefault()
    private val fxStore by lazy { FxRateStore(context.applicationContext) }
    val accounts get() = loadAccounts()
    private val allEntries get() = loadEntries()
    var renewalBusy by mutableStateOf(false)
    var renewalRateIssue by mutableStateOf<RenewalRateIssue?>(null)
    var nearestForIssue by mutableStateOf<Map<LocalDate, FxRates>>(emptyMap())
    var confirmSkipDues by mutableStateOf<List<DueRenewal>?>(null)
    var disposalTarget by mutableStateOf<LedgerEntry?>(null)
    var disposalStartAsSale by mutableStateOf(true)
    var undoDisposalTarget by mutableStateOf<LedgerEntry?>(null)
    var stopSubTarget by mutableStateOf<LedgerEntry?>(null)

    fun toggleActive(entry: LedgerEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.toggleActiveStatus(entry, !entry.isActiveCost)
            }
            onChanged()
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
            val rates = ratesByDate[due.date]
            // The chain-resolved rate always wins; the manual input only
            // covers dates the chain could not resolve.
            val cny = if (sub.currency == "CNY") {
                1.0
            } else {
                rates?.cnyPerUnit(sub.currency)?.takeIf { it > 0 } ?: manualRate
            }
            if (cny == null || cny <= 0) {
                stillUnresolved += due.date
                continue
            }
            val account = accounts.firstOrNull {
                it.uuid == (sub.accountUuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID) &&
                    it.deletedAtMillis == null
            }
            val needsAccountConversion = account != null && account.currency != sub.currency
            val accountRate = if (needsAccountConversion) {
                if (account.currency == "CNY") {
                    1.0
                } else {
                    rates?.cnyPerUnit(account.currency)?.takeIf { it > 0 }
                }
            } else {
                null
            }
            if (needsAccountConversion && accountRate == null) {
                // Never post a renewal using the previous cycle's account
                // amount while the base amount uses this cycle's rate.
                stillUnresolved += due.date
                continue
            }
            val accountAmountCents = if (needsAccountConversion) {
                LedgerAccounts.accountAmountForEntry(sub.amountCents, cny, accountRate!!)
            } else {
                null
            }
            val date = if (sub.currency == "CNY") null else rates?.date
            val source = if (sub.currency == "CNY") null else {
                rates?.source?.id ?: FX_SOURCE_MANUAL
            }
            built += SubscriptionRenewals.buildRenewal(
                sub,
                due,
                cny,
                date,
                source,
                zone,
                now,
                accountAmountCents = accountAmountCents,
            )
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
            onChanged()
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
                val datesNeedingRates = dues
                    .filter { due ->
                        val sub = due.subscription
                        val account = accounts.firstOrNull {
                            it.uuid == (sub.accountUuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID) &&
                                it.deletedAtMillis == null
                        }
                        sub.currency != "CNY" ||
                            (account != null && account.currency != sub.currency &&
                                account.currency != "CNY")
                    }
                    .map { it.date }
                    .toSet()
                val resolved = if (datesNeedingRates.isEmpty()) {
                    emptyMap()
                } else {
                    try {
                        fxStore.ratesForDates(datesNeedingRates, fetchIfMissing = true)
                    } catch (e: FxException) {
                        attempts = e.attempts
                        emptyMap()
                    }
                }
                val unresolved = datesNeedingRates.filter { it !in resolved }.sorted()
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
            onChanged()
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
                    val saleBase = (saleCents * fxRateToCny).roundToLong()
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
            onChanged()
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
            onChanged()
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
            onChanged()
        }
    }

    @Composable
    fun Host() {
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
                initialIsSale = disposalStartAsSale,
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

    }
}
