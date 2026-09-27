package com.example.toolbox.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DueRenewal(
    val subscription: LedgerEntry,
    val index: Int,
    val date: LocalDate,
    val uuid: String,
)

/**
 * Periodic-subscription renewals as real ledger rows. Renewal dates come from
 * the subscription's start date by calendar arithmetic (never cumulative), so
 * a month-end start clamps correctly (Jan 31 -> Feb 28/29 -> Mar 31).
 * Renewal uuids are deterministic — two devices recording the same renewal
 * converge through the LWW merge without duplicates.
 */
object SubscriptionRenewals {

    fun renewalUuid(parentUuid: String, index: Int): String =
        UUID.nameUUIDFromBytes(
            "toolbox-renewal:$parentUuid:$index".toByteArray(Charsets.UTF_8),
        ).toString()

    fun saleUuid(assetUuid: String): String =
        UUID.nameUUIDFromBytes(
            "toolbox-sale:$assetUuid".toByteArray(Charsets.UTF_8),
        ).toString()

    fun scheduleDate(
        start: LocalDate,
        cycle: BillingCycle,
        customDays: Int,
        customUnit: CycleUnit = CycleUnit.DAYS,
        index: Int = 1,
    ): LocalDate = when (cycle) {
        BillingCycle.WEEKLY -> start.plusWeeks(index.toLong())
        BillingCycle.MONTHLY -> start.plusMonths(index.toLong())
        BillingCycle.QUARTERLY -> start.plusMonths(3L * index)
        BillingCycle.YEARLY -> start.plusYears(index.toLong())
        BillingCycle.CUSTOM_DAYS -> {
            val count = customDays.coerceAtLeast(1).toLong() * index
            when (customUnit) {
                CycleUnit.DAYS -> start.plusDays(count)
                CycleUnit.WEEKS -> start.plusWeeks(count)
                CycleUnit.MONTHS -> start.plusMonths(count)
                CycleUnit.YEARS -> start.plusYears(count)
            }
        }
    }

    /**
     * Renewal rows due for recording. `entries` must include tombstones —
     * a recorded (or skipped) renewal blocks re-prompting either way.
     *
     * End rule: an active subscription is due up to `today` inclusive; a
     * stopped subscription (isActiveCost=false with a retired date) is due
     * only for dates strictly before the stop date — cancelling on the
     * renewal day means that renewal was never charged.
     */
    fun dueRenewals(
        entries: List<LedgerEntry>,
        today: LocalDate,
        zone: ZoneId,
        maxPerSubscription: Int = 120,
    ): List<DueRenewal> {
        val knownUuids = entries.mapTo(HashSet()) { it.uuid }
        val out = mutableListOf<DueRenewal>()
        for (sub in entries) {
            if (sub.deletedAtMillis != null ||
                // Periodic parents of either type renew; linked children never do.
                sub.costTrackingMode != CostTrackingMode.PERIODIC_SUBSCRIPTION ||
                sub.parentUuid != null
            ) {
                continue
            }
            val start = Instant.ofEpochMilli(sub.occurredAtMillis)
                .atZone(zone).toLocalDate()
            val stoppedDate = if (!sub.isActiveCost && sub.retiredAtMillis != null) {
                Instant.ofEpochMilli(sub.retiredAtMillis).atZone(zone).toLocalDate()
            } else {
                null
            }
            var index = 1
            var count = 0
            while (count < maxPerSubscription) {
                val dueDate = scheduleDate(
                    start, sub.billingCycle, sub.customCycleDays,
                    sub.customCycleUnit, index,
                )
                val due = if (stoppedDate != null) {
                    dueDate < stoppedDate
                } else {
                    dueDate <= today
                }
                if (!due) break
                val uuid = renewalUuid(sub.uuid, index)
                if (uuid !in knownUuids) {
                    out += DueRenewal(
                        subscription = sub,
                        index = index,
                        date = dueDate,
                        uuid = uuid,
                    )
                    count++
                }
                index++
            }
        }
        return out
    }

    fun buildRenewal(
        sub: LedgerEntry,
        due: DueRenewal,
        fxRateToCny: Double,
        fxRateDate: String?,
        fxRateSource: String?,
        zone: ZoneId,
        nowMillis: Long,
    ): LedgerEntry = LedgerEntry(
        uuid = due.uuid,
        title = sub.title,
        amountCents = sub.amountCents,
        currency = sub.currency,
        fxRateToCny = fxRateToCny,
        fxRateDate = fxRateDate,
        fxRateSource = fxRateSource,
        type = sub.type,
        category = sub.category,
        occurredAtMillis = due.date.atTime(
            Instant.ofEpochMilli(sub.occurredAtMillis).atZone(zone).toLocalTime(),
        ).atZone(zone).toInstant().toEpochMilli(),
        note = "",
        parentUuid = sub.uuid,
        linkType = LINK_TYPE_RENEWAL,
        renewalIndex = due.index,
        accountUuid = sub.accountUuid,
        accountAmountCents = sub.accountAmountCents,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        deletedAtMillis = null,
        syncStatus = LedgerSyncStatus.PENDING_PUSH,
    )

    /**
     * An invisible tombstone for a skipped renewal: it occupies the
     * deterministic uuid so the renewal stops being due and the skip
     * propagates to other devices.
     */
    fun buildSkipMarker(
        sub: LedgerEntry,
        due: DueRenewal,
        zone: ZoneId,
        nowMillis: Long,
    ): LedgerEntry = LedgerEntry(
        uuid = due.uuid,
        title = sub.title,
        amountCents = sub.amountCents,
        currency = sub.currency,
        type = sub.type,
        category = sub.category,
        occurredAtMillis = due.date.atTime(
            Instant.ofEpochMilli(sub.occurredAtMillis).atZone(zone).toLocalTime(),
        ).atZone(zone).toInstant().toEpochMilli(),
        note = "",
        parentUuid = sub.uuid,
        linkType = LINK_TYPE_RENEWAL,
        renewalIndex = due.index,
        accountUuid = sub.accountUuid,
        accountAmountCents = sub.accountAmountCents,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        deletedAtMillis = nowMillis,
        syncStatus = LedgerSyncStatus.PENDING_PUSH,
    )

    /** The first renewal strictly after [today]; null when stopped. */
    fun nextRenewalDate(
        sub: LedgerEntry,
        today: LocalDate,
        zone: ZoneId,
    ): LocalDate? {
        if (!sub.isActiveCost) return null
        val start = Instant.ofEpochMilli(sub.occurredAtMillis)
            .atZone(zone).toLocalDate()
        var index = 1
        while (index <= 10_000) {
            val date = scheduleDate(
                start, sub.billingCycle, sub.customCycleDays,
                sub.customCycleUnit, index,
            )
            if (date > today) return date
            index++
        }
        return null
    }
}
