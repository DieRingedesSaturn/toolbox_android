package com.example.toolbox.ledger

import java.util.UUID

enum class LedgerEntryType {
    EXPENSE,
    INCOME,
    /** Money moved between own accounts; never income or expense. */
    TRANSFER,
    /** Signed balance-correction row created by 校准余额. */
    ADJUSTMENT,
}

/**
 * Defines whether an entry participates in long-term / average usage cost calculation:
 * - [NONE]: Regular one-off expense or income entry.
 * - [ONE_TIME_AMORTIZED]: One-time purchase of a long-term asset (e.g. smartphone, laptop, appliance),
 *   amortized across days held (and/or target service days).
 * - [PERIODIC_SUBSCRIPTION]: Recurring subscription or membership (e.g. streaming VIP, VPS, cloud storage),
 *   normalized to daily and monthly average cost.
 */
enum class CostTrackingMode {
    NONE,
    ONE_TIME_AMORTIZED,
    PERIODIC_SUBSCRIPTION,
}

enum class BillingCycle(val averageDays: Double) {
    WEEKLY(7.0),
    MONTHLY(30.4375),
    QUARTERLY(91.3125),
    YEARLY(365.25),
    /** count × [LedgerEntry.customCycleUnit]; name kept for compat. */
    CUSTOM_DAYS(30.0),
}

/** Unit of a CUSTOM_DAYS (custom) billing cycle's count. */
enum class CycleUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

enum class LedgerSyncStatus {
    LOCAL_ONLY,
    PENDING_PUSH,
    SYNCED,
}

const val LINK_TYPE_RENEWAL = "RENEWAL"
const val LINK_TYPE_SALE = "SALE"
const val DISPOSAL_SOLD = "SOLD"
const val DISPOSAL_SCRAPPED = "SCRAPPED"
const val FX_SOURCE_MANUAL = "manual"

data class LedgerEntry(
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val amountCents: Long,
    val currency: String = "CNY",
    // CNY-per-unit rate locked when the entry was saved; ISO date of the rate
    // used (null for CNY or a manually typed rate). amountCents is always
    // amount x 100 in `currency`.
    val fxRateToCny: Double = 1.0,
    val fxRateDate: String? = null,
    // Rate provenance: "frankfurter"/"ecb"/"currency-api"/"manual"; null for CNY.
    val fxRateSource: String? = null,
    val type: LedgerEntryType = LedgerEntryType.EXPENSE,
    val category: String = LedgerCategory.OTHER.name,
    val occurredAtMillis: Long,
    val note: String = "",
    val costTrackingMode: CostTrackingMode = CostTrackingMode.NONE,
    // One-time long-term asset fields
    val salvageValueCents: Long = 0L,
    val targetDays: Int? = null,
    val retiredAtMillis: Long? = null,
    // Periodic / Subscription fields
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    /** For CUSTOM_DAYS: the count of `customCycleUnit` per cycle. */
    val customCycleDays: Int = 30,
    val customCycleUnit: CycleUnit = CycleUnit.DAYS,
    // Active flag (whether still in service / still subscribed)
    val isActiveCost: Boolean = true,
    // Generic links between entries: "RENEWAL" (child of a subscription,
    // carries renewalIndex) or "SALE" (income from disposing an asset).
    val parentUuid: String? = null,
    val linkType: String? = null,
    val renewalIndex: Int? = null,
    // On parent assets only: "SOLD"/"SCRAPPED" when disposed (retiredAtMillis
    // is then the disposal date); null = active or legacy retired.
    val disposalType: String? = null,
    // Multi-tag links; a deleted tag's uuid stays listed — display ignores it.
    val tagUuids: List<String> = emptyList(),
    /** Account this entry posts to; null → the default account. */
    val accountUuid: String? = null,
    /** Amount in the account's currency when it differs from `currency`. */
    val accountAmountCents: Long? = null,
    /** TRANSFER only: destination account + amount in its currency. */
    val toAccountUuid: String? = null,
    val toAmountCents: Long? = null,
    // Metadata for Last-Write-Wins merge across file backups and WebDAV sync
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deletedAtMillis: Long? = null,
    val syncStatus: LedgerSyncStatus = LedgerSyncStatus.PENDING_PUSH,
    val serverRevision: Long = 0L,
) {
    val amountValue: Double
        get() = amountCents / 100.0

    val salvageValue: Double
        get() = salvageValueCents / 100.0

    /** Amount converted to the CNY base using the rate locked at save time. */
    val baseAmountCents: Long
        get() = Math.round(amountCents * fxRateToCny)

    val baseSalvageCents: Long
        get() = Math.round(salvageValueCents * fxRateToCny)

    val netCostCents: Long
        get() = (baseAmountCents - baseSalvageCents).coerceAtLeast(0L)
}

data class CostBreakdown(
    val entryUuid: String,
    val mode: CostTrackingMode,
    val netCostYuan: Double,
    val daysHeld: Int,
    val targetDays: Int?,
    val dailyCostYuan: Double,
    val monthlyCostYuan: Double,
    val yearlyCostYuan: Double,
    val targetDailyCostYuan: Double?,
    val targetMonthlyCostYuan: Double?,
    val nextRenewalMillis: Long?,
    val daysUntilRenewal: Int?,
    val accumulatedCyclesCount: Int,
    val accumulatedTotalYuan: Double,
    val isActive: Boolean,
)

data class LedgerSummary(
    val activeDailyBurnRateYuan: Double,
    val activeMonthlyBurnRateYuan: Double,
    val activeYearlyBurnRateYuan: Double,
    val activeOneTimeDailyYuan: Double,
    val activePeriodicDailyYuan: Double,
    val activeOneTimeCount: Int,
    val activeSubscriptionCount: Int,
    val currentMonthExpenseYuan: Double,
    val currentMonthIncomeYuan: Double,
    val totalEntriesCount: Int,
    val pendingSyncCount: Int,
    val recurringIncomeDailyYuan: Double = 0.0,
    val recurringIncomeMonthlyYuan: Double = 0.0,
    val recurringIncomeCount: Int = 0,
)
