package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerQuickPicksTest {

    private val day = LedgerCalculator.MILLIS_PER_DAY
    private val now = 1_000 * day

    private fun entry(
        title: String,
        cents: Long,
        daysAgo: Long,
        type: LedgerEntryType = LedgerEntryType.EXPENSE,
        tags: List<String> = emptyList(),
        account: String? = null,
        currency: String = "CNY",
    ) = LedgerEntry(
        title = title,
        amountCents = cents,
        currency = currency,
        type = type,
        occurredAtMillis = now - daysAgo * day,
        tagUuids = tags,
        accountUuid = account,
    )

    @Test
    fun testGroupsByTypeAndTitleAndUsesTheLatestDetails() {
        val picks = LedgerQuickPicks.from(
            listOf(
                entry("Lunch", 2_500L, daysAgo = 9, tags = listOf("food")),
                entry(" lunch ", 3_000L, daysAgo = 1, tags = listOf("food", "work"), account = "card"),
                entry("Lunch", 9_000L, daysAgo = 3, type = LedgerEntryType.INCOME),
                entry("Coffee", 1_800L, daysAgo = 2),
            ),
            now,
        )

        assertEquals(1, picks.size)
        val lunch = picks.single()
        assertEquals(LedgerEntryType.EXPENSE, lunch.type)
        assertEquals("lunch", lunch.title)
        assertEquals(3_000L, lunch.amountCents)
        assertEquals(listOf("food", "work"), lunch.tagUuids)
        assertEquals("card", lunch.accountUuid)
        assertEquals(2, lunch.uses)
    }

    @Test
    fun testSkipsOldDeletedLinkedCostTrackedAndNonRecordTypes() {
        val picks = LedgerQuickPicks.from(
            listOf(
                entry("Old", 100L, daysAgo = 100),
                entry("Old", 100L, daysAgo = 95),
                entry("Gone", 100L, daysAgo = 1).copy(deletedAtMillis = now),
                entry("Gone", 100L, daysAgo = 2),
                entry("Renewal", 100L, daysAgo = 1).copy(parentUuid = "sub"),
                entry("Renewal", 100L, daysAgo = 2).copy(parentUuid = "sub"),
                entry("Phone", 100L, daysAgo = 1)
                    .copy(costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED),
                entry("Phone", 100L, daysAgo = 2)
                    .copy(costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED),
                entry("Move", 100L, daysAgo = 1, type = LedgerEntryType.TRANSFER),
                entry("Move", 100L, daysAgo = 2, type = LedgerEntryType.TRANSFER),
                entry("", 100L, daysAgo = 1),
                entry("", 100L, daysAgo = 2),
            ),
            now,
        )

        assertEquals(emptyList<LedgerQuickPick>(), picks)
    }

    @Test
    fun testOrdersByUsesThenRecencyAndCaps() {
        val entries = buildList {
            repeat(3) { add(entry("Metro", 400L, daysAgo = 10L + it)) }
            repeat(2) { add(entry("Tea", 1_200L, daysAgo = 20L + it)) }
            repeat(2) { add(entry("Snack", 800L, daysAgo = 1L + it)) }
            (1..10).forEach { n ->
                add(entry("Item $n", 100L, daysAgo = 30L + n))
                add(entry("Item $n", 100L, daysAgo = 31L + n))
            }
        }

        val picks = LedgerQuickPicks.from(entries, now)

        assertEquals(LedgerQuickPicks.MAX_PICKS, picks.size)
        assertEquals(listOf("Metro", "Snack", "Tea"), picks.take(3).map { it.title })
    }
}
