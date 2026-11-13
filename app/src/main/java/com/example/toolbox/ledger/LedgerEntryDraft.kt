package com.example.toolbox.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class EntryCostInput(
    val mode: CostTrackingMode = CostTrackingMode.NONE,
    val salvageCents: Long = 0L,
    val targetDays: Int? = null,
    val endsEpochDay: Long = -1L,
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val customCount: Int = 30,
    val customUnit: CycleUnit = CycleUnit.DAYS,
)

data class EntryAccountInput(
    val accountUuid: String = "",
    val currency: String? = null,
    val amountText: String = "",
    val toUuid: String = "",
    val toCurrency: String? = null,
    val toAmountText: String = "",
    val defaultUuid: String = LedgerAccounts.DEFAULT_ACCOUNT_UUID,
)

/** Pure form resolution. No Compose state, database access or rate fetching. */
data class LedgerEntryDraft(
    val uuid: String,
    val title: String,
    val amountCents: Long,
    val currency: String,
    val type: LedgerEntryType,
    val occurredAtMillis: Long,
    val note: String = "",
    val fxRateToCny: Double = 1.0,
    val fxRateDate: String? = null,
    val fxRateSource: String? = null,
    val cost: EntryCostInput = EntryCostInput(),
    val account: EntryAccountInput = EntryAccountInput(),
    val tagUuids: List<String> = emptyList(),
    val initial: LedgerEntry? = null,
) {
    fun resolve(nowMillis: Long, zone: ZoneId): LedgerEntry {
        val transfer = type == LedgerEntryType.TRANSFER
        val cash = type == LedgerEntryType.INCOME || type == LedgerEntryType.EXPENSE
        val endsAt = if (cash && initial?.parentUuid == null && cost.mode != CostTrackingMode.NONE && cost.endsEpochDay >= 0) {
            LocalDate.ofEpochDay(cost.endsEpochDay)
                .atTime(Instant.ofEpochMilli(occurredAtMillis).atZone(zone).toLocalTime())
                .atZone(zone).toInstant().toEpochMilli()
        } else null
        return LedgerEntry(
            uuid = uuid,
            title = title,
            amountCents = amountCents,
            currency = currency,
            fxRateToCny = fxRateToCny,
            fxRateDate = fxRateDate,
            fxRateSource = fxRateSource,
            type = type,
            category = initial?.category ?: LedgerCategory.OTHER.name,
            occurredAtMillis = occurredAtMillis,
            note = note,
            costTrackingMode = if (cash) cost.mode else CostTrackingMode.NONE,
            salvageValueCents = cost.salvageCents,
            targetDays = cost.targetDays,
            retiredAtMillis = initial?.retiredAtMillis,
            costEndsAtMillis = endsAt,
            billingCycle = cost.cycle,
            customCycleDays = cost.customCount.coerceAtLeast(1),
            customCycleUnit = cost.customUnit,
            isActiveCost = initial?.isActiveCost ?: true,
            parentUuid = initial?.parentUuid,
            linkType = initial?.linkType,
            renewalIndex = initial?.renewalIndex,
            disposalType = initial?.disposalType,
            tagUuids = if (cash) tagUuids else emptyList(),
            accountUuid = account.accountUuid.ifBlank { account.defaultUuid },
            accountAmountCents = if (cash && account.currency != null && account.currency != currency) {
                LedgerCalculator.parseMoneyToCents(account.amountText)
            } else null,
            toAccountUuid = if (transfer) account.toUuid.ifBlank { null } else null,
            toAmountCents = if (transfer && account.toCurrency != null && account.toCurrency != currency) {
                LedgerCalculator.parseMoneyToCents(account.toAmountText)
            } else null,
            createdAtMillis = initial?.createdAtMillis ?: nowMillis,
            updatedAtMillis = initial?.updatedAtMillis ?: nowMillis,
            deletedAtMillis = initial?.deletedAtMillis,
            syncStatus = initial?.syncStatus ?: LedgerSyncStatus.PENDING_PUSH,
            serverRevision = initial?.serverRevision ?: 0L,
        )
    }
}
