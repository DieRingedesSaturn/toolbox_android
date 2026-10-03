package com.example.toolbox.ledger

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerBudgetsTest {

    @Test
    fun testTagBudgetsRoundTripAndDropInvalidPairs() {
        val parsed = LedgerBudgets.parseTagBudgets("b=500;a=12000;;bad;c=0;d=-5;=7;e=x")
        assertEquals(mapOf("a" to 12_000L, "b" to 500L), parsed)
        assertEquals("a=12000;b=500", LedgerBudgets.formatTagBudgets(parsed))
        assertEquals(parsed, LedgerBudgets.parseTagBudgets(LedgerBudgets.formatTagBudgets(parsed)))
        assertEquals(emptyMap<String, Long>(), LedgerBudgets.parseTagBudgets(null))
    }

    @Test
    fun testProgressReportsRemainingAndOverspend() {
        val under = BudgetProgress(budgetCents = 300_000L, spentCents = 120_000L)
        assertEquals(180_000L, under.remainingCents)
        assertEquals(0.4f, under.fraction, 0.0001f)
        assertFalse(under.isOver)

        val over = BudgetProgress(budgetCents = 300_000L, spentCents = 320_000L)
        assertEquals(-20_000L, over.remainingCents)
        assertTrue(over.isOver)
    }

    @Test
    fun testDailyAllowanceSplitsRemainderOverDaysLeftIncludingToday() {
        val month = YearMonth.of(2026, 10)
        val progress = BudgetProgress(budgetCents = 300_000L, spentCents = 100_000L)

        assertEquals(10_000L, progress.dailyAllowanceCents(LocalDate.of(2026, 10, 12), month))
        assertEquals(200_000L, progress.dailyAllowanceCents(LocalDate.of(2026, 10, 31), month))
        assertNull(progress.dailyAllowanceCents(LocalDate.of(2026, 11, 1), month))
        assertNull(
            BudgetProgress(100L, 100L).dailyAllowanceCents(LocalDate.of(2026, 10, 12), month),
        )
    }
}
