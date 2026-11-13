package com.example.toolbox.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LedgerEntryDraftTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val date = LocalDate.of(2026, 10, 1).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
    private fun draft(type: LedgerEntryType = LedgerEntryType.EXPENSE) = LedgerEntryDraft(
        uuid = "entry", title = "Entry", amountCents = 1234L, currency = "USD", type = type, occurredAtMillis = date,
    )

    @Test fun foreignExpenseKeepsLockedRateAndAccountCurrencyAmount() {
        val entry = draft().copy(fxRateToCny = 7.2, fxRateDate = "2026-10-01", fxRateSource = "manual",
            account = EntryAccountInput("cny-account", "CNY", "88.85"), tagUuids = listOf("tag")).resolve(date, zone)
        assertEquals(8885L, entry.baseAmountCents)
        assertEquals(8885L, entry.accountAmountCents)
        assertEquals("manual", entry.fxRateSource)
        assertEquals(listOf("tag"), entry.tagUuids)
    }

    @Test fun transferHasDestinationAmountAndNeverCostOrTags() {
        val entry = draft(LedgerEntryType.TRANSFER).copy(
            cost = EntryCostInput(CostTrackingMode.ONE_TIME_AMORTIZED, endsEpochDay = LocalDate.of(2027, 1, 1).toEpochDay()),
            tagUuids = listOf("tag"), account = EntryAccountInput("usd", "USD", "99", "jpy", "JPY", "1800"),
        ).resolve(date, zone)
        assertEquals("jpy", entry.toAccountUuid)
        assertEquals(180000L, entry.toAmountCents)
        assertNull(entry.accountAmountCents)
        assertNull(entry.costEndsAtMillis)
        assertEquals(CostTrackingMode.NONE, entry.costTrackingMode)
        assertTrue(entry.tagUuids.isEmpty())
    }

    @Test fun linkedPaymentDoesNotInheritParentsEndDateValidationOrHorizon() {
        val initial = LedgerEntry(uuid = "renewal", title = "Renewal", amountCents = 100, occurredAtMillis = date,
            parentUuid = "parent", linkType = LINK_TYPE_RENEWAL, renewalIndex = 2,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION, costEndsAtMillis = date)
        val entry = draft().copy(initial = initial,
            cost = EntryCostInput(CostTrackingMode.PERIODIC_SUBSCRIPTION, endsEpochDay = LocalDate.of(2026, 9, 1).toEpochDay()),
        ).resolve(date, zone)
        assertNull(entry.costEndsAtMillis)
        assertEquals("parent", entry.parentUuid)
        assertEquals(2, entry.renewalIndex)
        assertEquals(CostTrackingMode.PERIODIC_SUBSCRIPTION, entry.costTrackingMode)
    }

    @Test fun assetEndUsesSelectedLocalTimeAndMetadataIsPreserved() {
        val initial = LedgerEntry(uuid = "entry", title = "Old", amountCents = 100, occurredAtMillis = date,
            createdAtMillis = 1, updatedAtMillis = 2, serverRevision = 3, disposalType = DISPOSAL_SOLD)
        val entry = draft().copy(initial = initial,
            cost = EntryCostInput(CostTrackingMode.ONE_TIME_AMORTIZED, endsEpochDay = LocalDate.of(2027, 1, 1).toEpochDay()),
        ).resolve(date, zone)
        val end = java.time.Instant.ofEpochMilli(entry.costEndsAtMillis!!).atZone(zone)
        assertEquals(9, end.hour)
        assertEquals(30, end.minute)
        assertEquals(1L, entry.createdAtMillis)
        assertEquals(2L, entry.updatedAtMillis)
        assertEquals(3L, entry.serverRevision)
        assertEquals(DISPOSAL_SOLD, entry.disposalType)
    }

    @Test fun adjustmentPreservesSignedAmountAndDefaultAccount() {
        val entry = draft(LedgerEntryType.ADJUSTMENT).copy(amountCents = -100,
            account = EntryAccountInput(defaultUuid = "default"), tagUuids = listOf("tag"),
        ).resolve(date, zone)
        assertEquals(-100L, entry.amountCents)
        assertEquals("default", entry.accountUuid)
        assertNull(entry.toAccountUuid)
        assertTrue(entry.tagUuids.isEmpty())
    }
}
