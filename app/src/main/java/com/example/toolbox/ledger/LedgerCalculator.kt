package com.example.toolbox.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
        /** Ordinary spending plus recorded monthly subscriptions; excludes long-term outlays. */
        val regularExpenseCents: Long = expenseCents,
    ) {
        val netCents: Long
            get() = incomeCents - expenseCents
        val regularNetCents: Long
            get() = incomeCents - regularExpenseCents
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
                calculateOneTimeBreakdown(entry, nowMillis, children, zone)
            CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                calculatePeriodicBreakdown(entry, nowMillis, children, zone)
        }
    }

    /** Local calendar day the cost item's scheduled end falls on. */
    private fun scheduledEndDate(entry: LedgerEntry, zone: ZoneId): LocalDate? =
        entry.costEndsAtMillis?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }

    /**
     * Whole days a cost item has been held (at least 1): up to the recorded
     * stop, then up to the scheduled end date once it has passed — counted
     * in calendar days so it matches the end-date horizon across DST
     * shifts — otherwise up to `now`.
     */
    private fun heldDays(
        entry: LedgerEntry,
        nowMillis: Long,
        endDate: LocalDate?,
        endedBySchedule: Boolean,
        zone: ZoneId,
    ): Int {
        val stoppedAt = entry.retiredAtMillis?.takeIf { !entry.isActiveCost }
        val days = when {
            stoppedAt != null -> (stoppedAt - entry.occurredAtMillis) / MILLIS_PER_DAY
            endedBySchedule && endDate != null -> ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone).toLocalDate(),
                endDate,
            )
            else -> (nowMillis - entry.occurredAtMillis) / MILLIS_PER_DAY
        }
        return days.toInt().coerceAtLeast(1)
    }

    private fun calculateOneTimeBreakdown(
        entry: LedgerEntry,
        nowMillis: Long,
        children: List<LedgerEntry>,
        zone: ZoneId,
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
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val endDate = scheduledEndDate(entry, zone)
        val endedBySchedule = endDate != null && today > endDate
        val daysHeld = heldDays(entry, nowMillis, endDate, endedBySchedule, zone)

        val actualDailyYuan = netCostYuan / daysHeld
        val actualMonthlyYuan = actualDailyYuan * DAYS_PER_MONTH

        // An explicit end date is the amortization horizon; otherwise the
        // days-count field carries the plan.
        val horizonDays = endDate?.let {
            val start = Instant.ofEpochMilli(entry.occurredAtMillis)
                .atZone(zone).toLocalDate()
            ChronoUnit.DAYS.between(start, it).toInt()
        }?.takeIf { it > 0 }
        val plannedDays = horizonDays ?: entry.targetDays?.takeIf { it > 0 }
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
            isActive = entry.isActiveCost && !endedBySchedule,
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

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val endDate = scheduledEndDate(entry, zone)
        val endedBySchedule = endDate != null && today > endDate
        val daysHeld = heldDays(entry, nowMillis, endDate, endedBySchedule, zone)
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
            isActive = entry.isActiveCost && !endedBySchedule,
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
                if (breakdown != null && breakdown.isActive) {
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
                if (breakdown != null && breakdown.isActive) {
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
        referenceEntries: List<LedgerEntry> = entries,
    ): MonthSummary {
        var expenseCents = 0L
        var regularExpenseCents = 0L
        var incomeCents = 0L
        val expenseByCategory = HashMap<LedgerCategory, Long>()
        // Include tombstoned parents for classification of historical renewals.
        val byUuid = referenceEntries.associateBy { it.uuid }
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
                    if (countsAsRegularExpense(entry, byUuid)) {
                        regularExpenseCents += entry.baseAmountCents
                    }
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
            regularExpenseCents = regularExpenseCents,
            expenseByCategory = expenseByCategory.entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
                .map { it.key to it.value },
        )
    }

    /** Today's actual income and expense totals, excluding reconcile adjustments. */
    fun dayTotals(
        entries: List<LedgerEntry>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        var expenseCents = 0L
        var incomeCents = 0L
        for (entry in entries) {
            if (entry.deletedAtMillis != null) continue
            if (entry.type == LedgerEntryType.ADJUSTMENT) continue
            val entryDate = Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone).toLocalDate()
            if (entryDate != date) continue
            when (entry.type) {
                LedgerEntryType.INCOME -> incomeCents += entry.baseAmountCents
                LedgerEntryType.EXPENSE -> expenseCents += entry.baseAmountCents
                LedgerEntryType.TRANSFER,
                LedgerEntryType.ADJUSTMENT,
                -> Unit
            }
        }
        return expenseCents to incomeCents
    }

    private fun countsAsRegularExpense(
        entry: LedgerEntry,
        byUuid: Map<String, LedgerEntry>,
    ): Boolean {
        // Renewal children have mode NONE; their billing rule belongs to the parent.
        // An orphan renewal has no known cycle, so keep it in actual outlays only.
        val source = if (entry.linkType == LINK_TYPE_RENEWAL) {
            byUuid[entry.parentUuid] ?: return false
        } else {
            entry
        }
        return when (source.costTrackingMode) {
            CostTrackingMode.NONE -> entry.linkType != LINK_TYPE_RENEWAL
            CostTrackingMode.ONE_TIME_AMORTIZED -> false
            CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                source.billingCycle == BillingCycle.MONTHLY ||
                    (source.billingCycle == BillingCycle.CUSTOM_DAYS &&
                        source.customCycleUnit == CycleUnit.MONTHS && source.customCycleDays == 1)
        }
    }

    fun monthlyTrend(
        entries: List<LedgerEntry>,
        lastMonth: YearMonth,
        months: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthSummary> = (months - 1 downTo 0).map { back ->
        monthSummary(entries, lastMonth.minusMonths(back.toLong()), zone)
    }

    fun averageFullMonthExpense(trend: List<MonthSummary>): Pair<Int, Long>? {
        val firstActive = trend.indexOfFirst { it.expenseCents > 0L || it.incomeCents > 0L }
        if (firstActive < 0) return null
        val fullMonths = trend.subList(firstActive, trend.size).dropLast(1)
        if (fullMonths.isEmpty()) return null
        return fullMonths.size to fullMonths.sumOf { it.expenseCents } / fullMonths.size
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

    fun formatCurrency(amountYuan: Double, symbol: String = "¥"): String {
        val sign = if (amountYuan < -0.000_001) "−" else ""
        return "$sign$symbol%,.2f".format(Locale.US, kotlin.math.abs(amountYuan))
    }

    /** Parses a decimal amount into cents without binary floating-point truncation. */
    fun parseMoneyToCents(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return runCatching {
            BigDecimal(normalized)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }

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

    /**
     * Straight-line remaining book value while a one-time asset is inside its
     * planned horizon. Returns 0 once [daysHeld] reaches [plannedDays], and
     * null when there is no positive plan.
     */
    fun remainingBookValueYuan(
        netCostYuan: Double,
        daysHeld: Int,
        plannedDays: Int?,
    ): Double? {
        val planned = plannedDays?.takeIf { it > 0 } ?: return null
        if (daysHeld >= planned) return 0.0
        return netCostYuan * (planned - daysHeld) / planned
    }
}
