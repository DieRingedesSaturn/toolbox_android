package com.example.toolbox.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRenewalsTest {

    private val zone = ZoneId.of("UTC")

    private fun sub(
        uuid: String = "sub-1",
        start: LocalDate = LocalDate.of(2026, 8, 22),
        cycle: BillingCycle = BillingCycle.MONTHLY,
        customDays: Int = 30,
        active: Boolean = true,
        retiredAt: LocalDate? = null,
        endsAt: LocalDate? = null,
        type: LedgerEntryType = LedgerEntryType.EXPENSE,
        mode: CostTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
    ) = LedgerEntry(
        uuid = uuid,
        title = "Netflix JP",
        amountCents = 286_000L, // JPY 2,860
        currency = "JPY",
        fxRateToCny = 0.0426,
        type = type,
        occurredAtMillis = start.atTime(9, 30)
            .atZone(zone).toInstant().toEpochMilli(),
        costTrackingMode = mode,
        billingCycle = cycle,
        customCycleDays = customDays,
        isActiveCost = active,
        retiredAtMillis = retiredAt?.atTime(9, 30)
            ?.atZone(zone)?.toInstant()?.toEpochMilli(),
        costEndsAtMillis = endsAt?.atTime(9, 30)
            ?.atZone(zone)?.toInstant()?.toEpochMilli(),
    )

    private fun recordedRenewal(
        parent: LedgerEntry,
        index: Int,
        tombstone: Boolean = false,
    ) = LedgerEntry(
        uuid = SubscriptionRenewals.renewalUuid(parent.uuid, index),
        title = parent.title,
        amountCents = parent.amountCents,
        occurredAtMillis = 0,
        parentUuid = parent.uuid,
        linkType = LINK_TYPE_RENEWAL,
        renewalIndex = index,
        deletedAtMillis = if (tombstone) 1L else null,
    )

    @Test
    fun testMonthEndClampingFromJan31() {
        val start = LocalDate.of(2028, 1, 31)
        assertEquals(
            LocalDate.of(2028, 2, 29), // leap year
            SubscriptionRenewals.scheduleDate(start, BillingCycle.MONTHLY, 30, CycleUnit.DAYS, 1),
        )
        assertEquals(
            LocalDate.of(2028, 3, 31),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.MONTHLY, 30, CycleUnit.DAYS, 2),
        )
        assertEquals(
            LocalDate.of(2028, 4, 30),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.MONTHLY, 30, CycleUnit.DAYS, 3),
        )
    }

    @Test
    fun testCycleSchedules() {
        val start = LocalDate.of(2026, 1, 5)
        assertEquals(
            LocalDate.of(2026, 1, 19),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.WEEKLY, 30, CycleUnit.DAYS, 2),
        )
        assertEquals(
            LocalDate.of(2026, 7, 5),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.QUARTERLY, 30, CycleUnit.DAYS, 2),
        )
        assertEquals(
            LocalDate.of(2027, 1, 5),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.SEMI_ANNUAL, 30, CycleUnit.DAYS, 2),
        )
        assertEquals(
            LocalDate.of(2028, 1, 5),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.YEARLY, 30, CycleUnit.DAYS, 2),
        )
        assertEquals(
            LocalDate.of(2026, 3, 6),
            SubscriptionRenewals.scheduleDate(start, BillingCycle.CUSTOM_DAYS, 30, CycleUnit.DAYS, 2),
        )
    }

    @Test
    fun testDueExcludesRecordedAndSkipped() {
        val subscription = sub(start = LocalDate.of(2026, 7, 22))
        val recorded = recordedRenewal(subscription, 1)
        val skipped = recordedRenewal(subscription, 2, tombstone = true)
        val today = LocalDate.of(2026, 10, 25)
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription, recorded, skipped),
            today,
            zone,
        )
        // Indices 1 (08-22) and 2 (09-22) exist (live + tombstone) → index 3 is due.
        assertEquals(listOf(3), due.map { it.index })
        assertEquals(LocalDate.of(2026, 10, 22), due.single().date)
    }

    @Test
    fun testStoppedSubscriptionStrictEndDate() {
        // Monthly on the 22nd; cancelled on 2026-10-22 → that renewal is NOT due.
        val subscription = sub(
            start = LocalDate.of(2026, 8, 22),
            active = false,
            retiredAt = LocalDate.of(2026, 10, 22),
        )
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2026, 11, 30),
            zone = zone,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 22)),
            due.map { it.date },
        )
    }

    @Test
    fun testNoRenewalsForChildrenIncomeNonPeriodicOrDeleted() {
        val today = LocalDate.of(2026, 9, 25)
        val sub = sub()
        val child = recordedRenewal(sub, 1)
        val income = sub(uuid = "inc-1", type = LedgerEntryType.INCOME)
        val plain = sub(uuid = "plain-1", mode = CostTrackingMode.NONE)
        val deleted = sub(uuid = "del-1").copy(deletedAtMillis = 1L)
        val due = SubscriptionRenewals.dueRenewals(
            listOf(sub, child, income, plain, deleted),
            today,
            zone,
        )
        // sub has 2 due (indices 2,3 wait — start 08-22: due 09-22 only since child
        // recorded index 1 already; child itself never generates renewals).
        assertTrue(due.all { it.subscription.uuid == "sub-1" || it.subscription.uuid == "inc-1" })
        assertTrue(due.none { it.subscription.uuid == "plain-1" })
        assertTrue(due.none { it.subscription.uuid == "del-1" })
        assertTrue(due.none { it.subscription.parentUuid != null })
        // Income periodic parents generate income renewals too.
        assertTrue(due.any { it.subscription.type == LedgerEntryType.INCOME })
    }

    @Test
    fun testDeterministicUuid() {
        assertEquals(
            SubscriptionRenewals.renewalUuid("sub-1", 3),
            SubscriptionRenewals.renewalUuid("sub-1", 3),
        )
        assertFalse(
            SubscriptionRenewals.renewalUuid("sub-1", 3) ==
                SubscriptionRenewals.renewalUuid("sub-1", 4),
        )
    }

    @Test
    fun testMaxPerSubscriptionCap() {
        val subscription = sub(start = LocalDate.of(2020, 1, 1))
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2026, 9, 25),
            zone = zone,
            maxPerSubscription = 5,
        )
        assertEquals(5, due.size)
    }

    @Test
    fun testBuildRenewalKeepsTimeOfDayAndFields() {
        val subscription = sub()
        val due = DueRenewal(subscription, 2, LocalDate.of(2026, 9, 22), "r-uuid")
        val renewal = SubscriptionRenewals.buildRenewal(
            subscription, due, 0.0450, "2026-09-22", "ecb", zone, 1234L,
        )
        assertEquals("r-uuid", renewal.uuid)
        assertEquals(subscription.title, renewal.title)
        assertEquals(subscription.amountCents, renewal.amountCents)
        assertEquals("JPY", renewal.currency)
        assertEquals(0.0450, renewal.fxRateToCny, 0.0001)
        assertEquals("2026-09-22", renewal.fxRateDate)
        assertEquals("ecb", renewal.fxRateSource)
        assertEquals(subscription.uuid, renewal.parentUuid)
        assertEquals(LINK_TYPE_RENEWAL, renewal.linkType)
        assertEquals(2, renewal.renewalIndex)
        assertEquals(CostTrackingMode.NONE, renewal.costTrackingMode)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, renewal.syncStatus)
        // 09-22 at the parent's 09:30 local time.
        val renewalLocal = java.time.Instant.ofEpochMilli(renewal.occurredAtMillis)
            .atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 22), renewalLocal.toLocalDate())
        assertEquals(9, renewalLocal.hour)
        assertEquals(30, renewalLocal.minute)
    }

    @Test
    fun testSkipMarkerIsTombstone() {
        val subscription = sub()
        val due = DueRenewal(subscription, 1, LocalDate.of(2026, 9, 22), "r-uuid")
        val marker = SubscriptionRenewals.buildSkipMarker(subscription, due, zone, 999L)
        assertEquals("r-uuid", marker.uuid)
        assertEquals(999L, marker.deletedAtMillis)
        assertEquals(999L, marker.updatedAtMillis)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, marker.syncStatus)
    }

    @Test
    fun testNextRenewalDate() {
        val subscription = sub(start = LocalDate.of(2026, 8, 22))
        assertEquals(
            LocalDate.of(2026, 10, 22),
            SubscriptionRenewals.nextRenewalDate(
                subscription,
                LocalDate.of(2026, 9, 25),
                zone,
            ),
        )
        // Stopped subscription: no next renewal.
        assertNull(
            SubscriptionRenewals.nextRenewalDate(
                subscription.copy(
                    isActiveCost = false,
                    retiredAtMillis = 1L,
                ),
                LocalDate.of(2026, 9, 25),
                zone,
            ),
        )
        // Renewal due today → next is next month.
        assertEquals(
            LocalDate.of(2026, 10, 22),
            SubscriptionRenewals.nextRenewalDate(
                subscription,
                LocalDate.of(2026, 9, 22),
                zone,
            ),
        )
    }

    @Test
    fun testNothingDueBeforeFirstCycle() {
        val subscription = sub(start = LocalDate.of(2026, 9, 22))
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2026, 9, 21),
            zone = zone,
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun testCustomUnitMonthsFromJan31() {
        val start = LocalDate.of(2026, 1, 31)
        // Every 2 months keeps the calendar-day anchor (clamped at month end).
        assertEquals(
            LocalDate.of(2026, 3, 31),
            SubscriptionRenewals.scheduleDate(
                start, BillingCycle.CUSTOM_DAYS, 2, CycleUnit.MONTHS, 1,
            ),
        )
        assertEquals(
            LocalDate.of(2026, 5, 31),
            SubscriptionRenewals.scheduleDate(
                start, BillingCycle.CUSTOM_DAYS, 2, CycleUnit.MONTHS, 2,
            ),
        )
    }

    @Test
    fun testCustomUnitWeeks() {
        val start = LocalDate.of(2026, 1, 5)
        assertEquals(
            LocalDate.of(2026, 2, 16),
            SubscriptionRenewals.scheduleDate(
                start, BillingCycle.CUSTOM_DAYS, 3, CycleUnit.WEEKS, 2,
            ),
        )
        assertEquals(
            LocalDate.of(2026, 1, 12),
            SubscriptionRenewals.scheduleDate(
                start, BillingCycle.CUSTOM_DAYS, 7, CycleUnit.DAYS, 1,
            ),
        )
    }

    @Test
    fun testDueRenewalsUseCustomUnit() {
        val subscription = sub(
            start = LocalDate.of(2026, 1, 5),
            cycle = BillingCycle.CUSTOM_DAYS,
            customDays = 2,
        ).copy(customCycleUnit = CycleUnit.WEEKS)
        val dues = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2026, 2, 20),
            zone = zone,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 19), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 16)),
            dues.map { it.date },
        )
    }

    @Test
    fun testDueRenewalsCappedAtScheduledEnd() {
        // Monthly on the 22nd, scheduled to end on 2026-12-22 — a renewal
        // landing exactly on the end date is never charged.
        val subscription = sub(
            start = LocalDate.of(2026, 8, 22),
            endsAt = LocalDate.of(2026, 12, 22),
        )
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2027, 3, 1),
            zone = zone,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 11, 22),
            ),
            due.map { it.date },
        )
    }

    @Test
    fun testScheduledEndBeforeFirstRenewalYieldsNothing() {
        val subscription = sub(
            start = LocalDate.of(2026, 8, 22),
            endsAt = LocalDate.of(2026, 9, 20),
        )
        val due = SubscriptionRenewals.dueRenewals(
            listOf(subscription),
            today = LocalDate.of(2026, 10, 1),
            zone = zone,
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun testNextRenewalDateRespectsScheduledEnd() {
        val subscription = sub(
            start = LocalDate.of(2026, 8, 22),
            endsAt = LocalDate.of(2026, 12, 22),
        )
        // Next renewal 10-22 is before the end date.
        assertEquals(
            LocalDate.of(2026, 10, 22),
            SubscriptionRenewals.nextRenewalDate(
                subscription,
                LocalDate.of(2026, 9, 25),
                zone,
            ),
        )
        // After the last in-term renewal, the next candidate lands on the
        // end date itself — never charged, so no next renewal.
        assertNull(
            SubscriptionRenewals.nextRenewalDate(
                subscription,
                LocalDate.of(2026, 11, 23),
                zone,
            ),
        )
    }

    @Test
    fun testNextRenewalDateCustomUnit() {
        val subscription = sub(
            start = LocalDate.of(2026, 8, 22),
            cycle = BillingCycle.CUSTOM_DAYS,
            customDays = 3,
        ).copy(customCycleUnit = CycleUnit.MONTHS)
        assertEquals(
            LocalDate.of(2026, 11, 22),
            SubscriptionRenewals.nextRenewalDate(
                subscription,
                LocalDate.of(2026, 10, 22),
                zone,
            ),
        )
    }
}
