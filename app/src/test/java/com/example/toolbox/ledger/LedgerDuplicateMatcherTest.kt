package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerDuplicateMatcherTest {
    private val draft = LedgerEntry(
        uuid = "draft",
        title = "Lunch",
        amountCents = 2_500L,
        occurredAtMillis = 1_000_000L,
    )

    @Test
    fun matchesBothTimeDirectionsAndWindowBoundaries() {
        val before = draft.copy(uuid = "before", occurredAtMillis = draft.occurredAtMillis - 300_000L)
        val after = draft.copy(uuid = "after", occurredAtMillis = draft.occurredAtMillis + 300_000L)
        assertEquals(listOf(after, before), LedgerDuplicateMatcher.find(draft, listOf(before, after)))
        assertTrue(LedgerDuplicateMatcher.find(draft, listOf(
            before.copy(occurredAtMillis = before.occurredAtMillis - 1),
            after.copy(occurredAtMillis = after.occurredAtMillis + 1),
        )).isEmpty())
    }

    @Test
    fun requiresMatchingAmountCurrencyTypeAndAccount() {
        val other = draft.copy(uuid = "other")
        val different = listOf(
            other.copy(amountCents = 2_501L),
            other.copy(currency = "USD"),
            other.copy(type = LedgerEntryType.INCOME),
            other.copy(accountUuid = "another-account"),
            other.copy(type = LedgerEntryType.TRANSFER),
            other.copy(type = LedgerEntryType.ADJUSTMENT),
        )
        assertTrue(LedgerDuplicateMatcher.find(draft, different).isEmpty())
    }

    @Test
    fun legacyNullAccountEqualsExplicitDefaultAccount() {
        val existing = draft.copy(uuid = "existing", accountUuid = LedgerAccounts.DEFAULT_ACCOUNT_UUID)
        assertEquals(listOf(existing), LedgerDuplicateMatcher.find(draft, listOf(existing)))
        assertEquals(listOf(draft), LedgerDuplicateMatcher.find(existing, listOf(draft)))
    }

    @Test
    fun ignoresDeletedEntriesAndTheSameUuid() {
        val existing = draft.copy(uuid = "existing", deletedAtMillis = 1L)
        assertTrue(LedgerDuplicateMatcher.find(draft, listOf(draft, existing)).isEmpty())
        assertTrue(LedgerDuplicateMatcher.find(existing, listOf(draft)).isEmpty())
    }

    @Test
    fun alsoChecksIncomeButNeverTransfersAdjustmentsOrInvalidAmounts() {
        val income = draft.copy(type = LedgerEntryType.INCOME)
        assertEquals(1, LedgerDuplicateMatcher.find(income, listOf(income.copy(uuid = "other"))).size)
        listOf(
            draft.copy(type = LedgerEntryType.TRANSFER),
            draft.copy(type = LedgerEntryType.ADJUSTMENT),
            draft.copy(amountCents = 0L),
            draft.copy(amountCents = -1L),
        ).forEach { entry ->
            assertTrue(LedgerDuplicateMatcher.find(entry, listOf(entry.copy(uuid = "other"))).isEmpty())
        }
    }

    @Test
    fun titleTagsAndFxRateDoNotHideMatchingPayments() {
        val existing = draft.copy(uuid = "other", title = "Bank payment", tagUuids = listOf("tag"), fxRateToCny = 7.0)
        assertEquals(listOf(existing), LedgerDuplicateMatcher.find(draft, listOf(existing)))
    }

    @Test
    fun extremeTimestampsDoNotOverflowTheTimeWindow() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE).forEach { time ->
            val entry = draft.copy(occurredAtMillis = time)
            assertEquals(1, LedgerDuplicateMatcher.find(entry, listOf(entry.copy(uuid = "other"))).size)
            assertTrue(LedgerDuplicateMatcher.find(entry, listOf(draft.copy(uuid = "other"))).isEmpty())
        }
    }
}
