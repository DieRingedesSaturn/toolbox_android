package com.example.toolbox.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

object LedgerCalculator {
    const val MILLIS_PER_DAY = 86_400_000L
    const val DAYS_PER_MONTH = 30.4375
    const val DAYS_PER_YEAR = 365.25

    data class MonthSummary(
        val month: YearMonth,
        val expenseCents: Long,
        val incomeCents: Long,
        val expenseByCategory: List<Pair<LedgerCategory, Long>>,
    ) {
        val netCents: Long
            get() = incomeCents - expenseCents
    }

    data class DayGroup(
        val date: LocalDate,
        val entries: List<LedgerEntry>,
        val expenseCents: Long,
        val incomeCents: Long,
    )

    /** Live linked children (renewal or sale rows), keyed by parent uuid. */
    fun childrenByParent(
        entries: List<LedgerEntry>,
    ): Map<String, List<LedgerEntry>> =
        entries
            .filter { it.parentUuid != null && it.deletedAtMillis == null }
            .groupBy { it.parentUuid!! }

    /**
     * [children] are the entry's live linked rows (see [childrenByParent]).
     * For subscriptions they are the recorded renewals; for assets the sale
     * income row when disposed.
     */
    fun calculateCostBreakdown(
        entry: LedgerEntry,
        nowMillis: Long = System.currentTimeMillis(),
        children: List<LedgerEntry> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): CostBreakdown? {
        if (entry.deletedAtMillis != null) return null
        return when (entry.costTrackingMode) {
            CostTrackingMode.NONE -> null
            CostTrackingMode.ONE_TIME_AMORTIZED ->
                calculateOneTimeBreakdown(entry, nowMillis, children)
            CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                calculatePeriodicBreakdown(entry, nowMillis, children, zone)
        }
    }

    private fun calculateOneTimeBreakdown(
        entry: LedgerEntry,
        nowMillis: Long,
        children: List<LedgerEntry>,
    ): CostBreakdown {
        val netCents = when (entry.disposalType) {
            // Sold: net cost is price minus what it was actually sold for.
            DISPOSAL_SOLD -> (
                entry.baseAmountCents -
                    (children.firstOrNull { it.linkType == LINK_TYPE_SALE }
                        ?.baseAmountCents ?: 0L)
                ).coerceAtLeast(0L)
            // Scrapped: the estimated salvage no longer offsets the cost.
            DISPOSAL_SCRAPPED -> entry.baseAmountCents
            else -> entry.netCostCents
        }
        val netCostYuan = netCents / 100.0
        val effectiveEndMillis = if (!entry.isActiveCost && entry.retiredAtMillis != null) {
            entry.retiredAtMillis
        } else {
            nowMillis
        }
        val elapsedDays = ((effectiveEndMillis - entry.occurredAtMillis) / MILLIS_PER_DAY).toInt()
        val daysHeld = elapsedDays.coerceAtLeast(1)

        val actualDailyYuan = netCostYuan / daysHeld
        val actualMonthlyYuan = actualDailyYuan * DAYS_PER_MONTH

        val plannedDays = entry.targetDays?.takeIf { it > 0 }
        val targetDailyYuan = plannedDays?.let { netCostYuan / it }
        val targetMonthlyYuan = targetDailyYuan?.let { it * DAYS_PER_MONTH }

        // Primary displayed average cost: if user specified a target lifespan (e.g. 730d)
        // and actual days held hasn't exceeded it yet, amortize by targetDays; otherwise by daysHeld.
        val primaryDailyYuan = if (plannedDays != null && daysHeld < plannedDays) {
            targetDailyYuan ?: actualDailyYuan
        } else {
            actualDailyYuan
        }
        val primaryMonthlyYuan = primaryDailyYuan * DAYS_PER_MONTH
        val primaryYearlyYuan = primaryDailyYuan * DAYS_PER_YEAR

        return CostBreakdown(
            entryUuid = entry.uuid,
            mode = CostTrackingMode.ONE_TIME_AMORTIZED,
            netCostYuan = netCostYuan,
            daysHeld = daysHeld,
            targetDays = plannedDays,
            dailyCostYuan = primaryDailyYuan,
            monthlyCostYuan = primaryMonthlyYuan,
            yearlyCostYuan = primaryYearlyYuan,
            targetDailyCostYuan = actualDailyYuan, // Expose actual-to-date daily cost for comparison
            targetMonthlyCostYuan = actualMonthlyYuan,
            nextRenewalMillis = null,
            daysUntilRenewal = null,
            accumulatedCyclesCount = 1,
            accumulatedTotalYuan = netCostYuan,
            isActive = entry.isActiveCost,
        )
    }

    private fun calculatePeriodicBreakdown(
        entry: LedgerEntry,
        nowMillis: Long,
        children: List<LedgerEntry>,
        zone: ZoneId,
    ): CostBreakdown {
        // Recorded payments = the subscription entry itself plus every live
        // renewal child. The burn rate prices each future cycle at the LATEST
        // recorded payment's base amount (rate drift is handled by storing a
        // new renewal each time).
        val payments = (listOf(entry) +
            children.filter { it.linkType == LINK_TYPE_RENEWAL })
            .sortedBy { it.occurredAtMillis }
        val cyclePriceYuan = payments.last().baseAmountCents / 100.0
        val cycleDays = when (entry.billingCycle) {
            BillingCycle.WEEKLY -> BillingCycle.WEEKLY.averageDays
            BillingCycle.MONTHLY -> BillingCycle.MONTHLY.averageDays
            BillingCycle.QUARTERLY -> BillingCycle.QUARTERLY.averageDays
            BillingCycle.SEMI_ANNUAL -> BillingCycle.SEMI_ANNUAL.averageDays
            BillingCycle.YEARLY -> BillingCycle.YEARLY.averageDays
            BillingCycle.CUSTOM_DAYS -> {
                val count = entry.customCycleDays.coerceAtLeast(1).toDouble()
                count * when (entry.customCycleUnit) {
                    CycleUnit.DAYS -> 1.0
                    CycleUnit.WEEKS -> 7.0
                    CycleUnit.MONTHS -> 30.4375
                    CycleUnit.YEARS -> 365.25
                }
            }
        }

        val dailyCostYuan = cyclePriceYuan / cycleDays
        val monthlyCostYuan = when (entry.billingCycle) {
            BillingCycle.MONTHLY -> cyclePriceYuan
            BillingCycle.QUARTERLY -> cyclePriceYuan / 3.0
            BillingCycle.SEMI_ANNUAL -> cyclePriceYuan / 6.0
            BillingCycle.YEARLY -> cyclePriceYuan / 12.0
            else -> dailyCostYuan * DAYS_PER_MONTH
        }
        val yearlyCostYuan = when (entry.billingCycle) {
            BillingCycle.YEARLY -> cyclePriceYuan
            BillingCycle.SEMI_ANNUAL -> cyclePriceYuan * 2.0
            BillingCycle.QUARTERLY -> cyclePriceYuan * 4.0
            BillingCycle.MONTHLY -> cyclePriceYuan * 12.0
            else -> dailyCostYuan * DAYS_PER_YEAR
        }

        val effectiveEndMillis = if (!entry.isActiveCost && entry.retiredAtMillis != null) {
            entry.retiredAtMillis
        } else {
            nowMillis
        }
        val elapsedMillis = (effectiveEndMillis - entry.occurredAtMillis).coerceAtLeast(0L)
        val daysHeld = ((elapsedMillis / MILLIS_PER_DAY).toInt()).coerceAtLeast(1)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val nextRenewal = SubscriptionRenewals.nextRenewalDate(entry, today, zone)
            ?.let { date ->
                date.atTime(
                    Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone).toLocalTime(),
                ).atZone(zone).toInstant().toEpochMilli()
            }
        val daysUntilRenewal = nextRenewal?.let {
            ((it - nowMillis + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
        }

        return CostBreakdown(
            entryUuid = entry.uuid,
            mode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            netCostYuan = cyclePriceYuan,
            daysHeld = daysHeld,
            targetDays = cycleDays.toInt(),
            dailyCostYuan = dailyCostYuan,
            monthlyCostYuan = monthlyCostYuan,
            yearlyCostYuan = yearlyCostYuan,
            targetDailyCostYuan = null,
            targetMonthlyCostYuan = null,
            nextRenewalMillis = nextRenewal,
            daysUntilRenewal = daysUntilRenewal,
            accumulatedCyclesCount = payments.size,
            accumulatedTotalYuan = payments.sumOf { it.baseAmountCents } / 100.0,
            isActive = entry.isActiveCost,
        )
    }

    fun summarize(
        entries: List<LedgerEntry>,
        nowMillis: Long = System.currentTimeMillis(),
        tags: List<LedgerTag> = emptyList(),
        accounts: List<LedgerAccount> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerSummary {
        val visibleEntries = entries.filter { it.deletedAtMillis == null }
        val children = childrenByParent(entries)
        var activeOneTimeDaily = 0.0
        var activePeriodicDaily = 0.0
        var activeOneTimeMonthly = 0.0
        var activePeriodicMonthly = 0.0
        var activeOneTimeCount = 0
        var activeSubscriptionCount = 0
        var recurringIncomeDaily = 0.0
        var recurringIncomeMonthly = 0.0
        var recurringIncomeCount = 0

        val currentMonth = monthSummary(
            visibleEntries,
            YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())),
            ZoneId.systemDefault(),
        )

        for (entry in visibleEntries) {
            if (entry.parentUuid != null) continue
            if (entry.type == LedgerEntryType.EXPENSE && entry.isActiveCost) {
                val breakdown = calculateCostBreakdown(
                    entry,
                    nowMillis,
                    children[entry.uuid].orEmpty(),
                    zone,
                )
                if (breakdown != null) {
                    when (entry.costTrackingMode) {
                        CostTrackingMode.ONE_TIME_AMORTIZED -> {
                            activeOneTimeDaily += breakdown.dailyCostYuan
                            activeOneTimeMonthly += breakdown.monthlyCostYuan
                            activeOneTimeCount++
                        }
                        CostTrackingMode.PERIODIC_SUBSCRIPTION -> {
                            activePeriodicDaily += breakdown.dailyCostYuan
                            activePeriodicMonthly += breakdown.monthlyCostYuan
                            activeSubscriptionCount++
                        }
                        CostTrackingMode.NONE -> Unit
                    }
                }
            } else if (
                entry.type == LedgerEntryType.INCOME &&
                entry.isActiveCost &&
                entry.costTrackingMode == CostTrackingMode.PERIODIC_SUBSCRIPTION
            ) {
                val breakdown = calculateCostBreakdown(
                    entry,
                    nowMillis,
                    children[entry.uuid].orEmpty(),
                    zone,
                )
                if (breakdown != null) {
                    recurringIncomeDaily += breakdown.dailyCostYuan
                    recurringIncomeMonthly += breakdown.monthlyCostYuan
                    recurringIncomeCount++
                }
            }
        }

        val totalDailyBurn = activeOneTimeDaily + activePeriodicDaily
        val totalMonthlyBurn = activeOneTimeMonthly + activePeriodicMonthly
        val totalYearlyBurn = totalDailyBurn * DAYS_PER_YEAR
        val pendingSync = entries.count {
            it.syncStatus == LedgerSyncStatus.PENDING_PUSH
        } + tags.count { it.syncStatus == LedgerSyncStatus.PENDING_PUSH } +
            accounts.count { it.syncStatus == LedgerSyncStatus.PENDING_PUSH }

        return LedgerSummary(
            activeDailyBurnRateYuan = totalDailyBurn,
            activeMonthlyBurnRateYuan = totalMonthlyBurn,
            activeYearlyBurnRateYuan = totalYearlyBurn,
            activeOneTimeDailyYuan = activeOneTimeDaily,
            activePeriodicDailyYuan = activePeriodicDaily,
            activeOneTimeCount = activeOneTimeCount,
            activeSubscriptionCount = activeSubscriptionCount,
            currentMonthExpenseYuan = currentMonth.expenseCents / 100.0,
            currentMonthIncomeYuan = currentMonth.incomeCents / 100.0,
            totalEntriesCount = visibleEntries.size,
            pendingSyncCount = pendingSync,
            recurringIncomeDailyYuan = recurringIncomeDaily,
            recurringIncomeMonthlyYuan = recurringIncomeMonthly,
            recurringIncomeCount = recurringIncomeCount,
        )
    }

    fun monthSummary(
        entries: List<LedgerEntry>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthSummary {
        var expenseCents = 0L
        var incomeCents = 0L
        val expenseByCategory = HashMap<LedgerCategory, Long>()
        for (entry in entries) {
            if (entry.deletedAtMillis != null) continue
            val entryMonth = YearMonth.from(
                Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone),
            )
            if (entryMonth != month) continue
            when (entry.type) {
                LedgerEntryType.INCOME -> incomeCents += entry.baseAmountCents
                LedgerEntryType.EXPENSE -> {
                    expenseCents += entry.baseAmountCents
                    expenseByCategory.merge(
                        entry.categoryEnum,
                        entry.baseAmountCents,
                        Long::plus,
                    )
                }
                // Transfers and balance adjustments are never income/expense.
                LedgerEntryType.TRANSFER,
                LedgerEntryType.ADJUSTMENT,
                -> Unit
            }
        }
        return MonthSummary(
            month = month,
            expenseCents = expenseCents,
            incomeCents = incomeCents,
            expenseByCategory = expenseByCategory.entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
                .map { it.key to it.value },
        )
    }

    /**
     * Per-tag base-amount totals for [month]. An entry with several tags
     * counts under each of them; entries with no live tag fall into the
     * `null` key. Deleted and unknown tag uuids are ignored.
     */
    fun tagBreakdown(
        entries: List<LedgerEntry>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
        type: LedgerEntryType = LedgerEntryType.EXPENSE,
        tags: List<LedgerTag>,
    ): List<Pair<LedgerTag?, Long>> {
        val liveTags = tags.filter { it.deletedAtMillis == null }.associateBy { it.uuid }
        val sums = LinkedHashMap<String?, Long>()
        for (entry in entries) {
            if (entry.deletedAtMillis != null || entry.type != type) continue
            val entryMonth = YearMonth.from(
                Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone),
            )
            if (entryMonth != month) continue
            val live = entry.tagUuids.filter { liveTags.containsKey(it) }
            if (live.isEmpty()) {
                sums.merge(null, entry.baseAmountCents, Long::plus)
            } else {
                for (uuid in live) {
                    sums.merge(uuid, entry.baseAmountCents, Long::plus)
                }
            }
        }
        return sums.entries
            .sortedByDescending { it.value }
            .map { it.key?.let { uuid -> liveTags[uuid] } to it.value }
    }

    /**
     * Day-grouped records for the Transactions list. Reconcile adjustments
     * are excluded: they only correct an account balance and remain visible
     * in that account's own entry list.
     */
    fun groupByDay(
        entries: List<LedgerEntry>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayGroup> {
        return entries
            .filter { it.deletedAtMillis == null }
            .filter { it.type != LedgerEntryType.ADJUSTMENT }
            .filter {
                YearMonth.from(Instant.ofEpochMilli(it.occurredAtMillis).atZone(zone)) == month
            }
            .groupBy { Instant.ofEpochMilli(it.occurredAtMillis).atZone(zone).toLocalDate() }
            .map { (date, dayEntries) ->
                val sorted = dayEntries.sortedByDescending { it.occurredAtMillis }
                DayGroup(
                    date = date,
                    entries = sorted,
                    expenseCents = sorted
                        .filter { it.type == LedgerEntryType.EXPENSE }
                        .sumOf { it.baseAmountCents },
                    incomeCents = sorted
                        .filter { it.type == LedgerEntryType.INCOME }
                        .sumOf { it.baseAmountCents },
                )
            }
            .sortedByDescending { it.date }
    }

    /**
     * Case-insensitive search across all live records for the ledger
     * search box. Matches title, note, live tag names, account names
     * (both legs of a transfer), and the amount in original or CNY
     * terms. Reconcile adjustments are excluded — like in the
     * Transactions list they are account corrections, not records.
     */
    fun searchEntries(
        entries: List<LedgerEntry>,
        query: String,
        tags: List<LedgerTag>,
        accounts: List<LedgerAccount>,
    ): List<LedgerEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val liveTags = tags.filter { it.deletedAtMillis == null }.associateBy { it.uuid }
        val accountNames = accounts
            .filter { it.deletedAtMillis == null }
            .associate { it.uuid to it.name.lowercase() }
        fun amountMatches(cents: Long): Boolean =
            "%.2f".format(Locale.US, cents / 100.0).contains(q)
        return entries
            .filter { it.deletedAtMillis == null }
            .filter { it.type != LedgerEntryType.ADJUSTMENT }
            .filter { entry ->
                entry.title.lowercase().contains(q) ||
                    entry.note.lowercase().contains(q) ||
                    entry.tagUuids.any { uuid ->
                        liveTags[uuid]?.name?.lowercase()?.contains(q) == true
                    } ||
                    accountNames[entry.accountUuid]?.contains(q) == true ||
                    accountNames[entry.toAccountUuid]?.contains(q) == true ||
                    amountMatches(entry.amountCents) ||
                    amountMatches(entry.baseAmountCents)
            }
            .sortedByDescending { it.occurredAtMillis }
    }

    fun formatCurrency(amountYuan: Double, symbol: String = "¥"): String =
        "$symbol%.2f".format(Locale.US, amountYuan)

    /**
     * Formats an amount in the entry's own currency (not the CNY base).
     * [amountCents] is amount x 100 in [currency]. Fraction digits come from
     * [java.util.Currency] (JPY 0); no grouping separators.
     */
    fun currencySymbol(currency: String): String = when (currency) {
        "CNY" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "JPY" -> "JP¥"
        else -> "$currency "
    }

    fun formatMoney(amountCents: Long, currency: String): String {
        val symbol = currencySymbol(currency)
        val digits = runCatching {
            java.util.Currency.getInstance(currency).defaultFractionDigits
        }.getOrDefault(2).coerceAtLeast(0)
        return symbol + String.format(Locale.US, "%.${digits}f", amountCents / 100.0)
    }
}
