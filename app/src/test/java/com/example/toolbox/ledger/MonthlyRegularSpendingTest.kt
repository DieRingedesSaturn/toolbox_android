package com.example.toolbox.ledger

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyRegularSpendingTest {
    private val zone = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 11)
    private val date = month.atDay(5).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun entry(id: String, amount: Long = 10_000L) = LedgerEntry(
        uuid = id,
        title = id,
        amountCents = amount,
        occurredAtMillis = date,
    )

    private fun subscription(id: String, cycle: BillingCycle, amount: Long = 10_000L) =
        entry(id, amount).copy(
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = cycle,
        )

    @Test
    fun regularNetExcludesLongTermOutlaysButActualTotalsRemainIntact() {
        val rows = listOf(
            entry("food"),
            subscription("monthly", BillingCycle.MONTHLY, 2_000L),
            subscription("annual", BillingCycle.YEARLY, 24_000L),
            entry("asset", 120_000L).copy(costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED),
            entry("salary", 500_000L).copy(type = LedgerEntryType.INCOME),
        )
        val result = LedgerCalculator.monthSummary(rows, month, zone)
        assertEquals(156_000L, result.expenseCents)
        assertEquals(156_000L, result.expenseByCategory.sumOf { it.second })
        assertEquals(10_000L, result.ordinaryExpenseCents)
        assertEquals(2_000L, result.monthlySubscriptionCents)
        assertEquals(12_000L, result.regularExpenseCents)
        assertEquals(144_000L, result.oneTimeOutlayCents)
        assertEquals(488_000L, result.regularNetCents)
        assertEquals(344_000L, result.netCents)
        assertEquals(156_000L, LedgerCalculator.groupByDay(rows, month, zone).sumOf { it.expenseCents })
    }

    @Test
    fun onlyMonthlyAndExactlyOneCustomCalendarMonthQualify() {
        val rows = BillingCycle.entries.map { subscription(it.name, it) } + listOf(
            subscription("one-month", BillingCycle.CUSTOM_DAYS).copy(
                customCycleDays = 1, customCycleUnit = CycleUnit.MONTHS,
            ),
            subscription("two-months", BillingCycle.CUSTOM_DAYS).copy(
                customCycleDays = 2, customCycleUnit = CycleUnit.MONTHS,
            ),
            subscription("four-weeks", BillingCycle.CUSTOM_DAYS).copy(
                customCycleDays = 4, customCycleUnit = CycleUnit.WEEKS,
            ),
            subscription("one-year", BillingCycle.CUSTOM_DAYS).copy(
                customCycleDays = 1, customCycleUnit = CycleUnit.YEARS,
            ),
        )
        val result = LedgerCalculator.monthSummary(rows, month, zone)
        assertEquals(rows.size * 10_000L, result.expenseCents)
        assertEquals(0L, result.ordinaryExpenseCents)
        assertEquals(20_000L, result.monthlySubscriptionCents)
        assertEquals(20_000L, result.regularExpenseCents)
        assertEquals(rows.size * 10_000L - 20_000L, result.oneTimeOutlayCents)
    }

    @Test
    fun renewalsUseTheirParentsCycleEvenWhenParentIsOutsideMonthOrDeleted() {
        val past = LocalDate.of(2025, 11, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthly = subscription("monthly", BillingCycle.MONTHLY).copy(
            occurredAtMillis = past, isActiveCost = false, deletedAtMillis = date + 1,
        )
        val annual = subscription("annual", BillingCycle.YEARLY, 120_000L).copy(occurredAtMillis = past)
        val monthlyRenewal = entry("monthly-renewal").copy(parentUuid = monthly.uuid, linkType = LINK_TYPE_RENEWAL)
        val annualRenewal = entry("annual-renewal", 120_000L).copy(parentUuid = annual.uuid, linkType = LINK_TYPE_RENEWAL)
        val deleted = monthlyRenewal.copy(uuid = "deleted-renewal", deletedAtMillis = date)
        val result = LedgerCalculator.monthSummary(
            listOf(monthly, annual, monthlyRenewal, annualRenewal, deleted), month, zone,
        )
        assertEquals(130_000L, result.expenseCents)
        assertEquals(0L, result.ordinaryExpenseCents)
        assertEquals(10_000L, result.monthlySubscriptionCents)
        assertEquals(10_000L, result.regularExpenseCents)
        assertEquals(120_000L, result.oneTimeOutlayCents)
    }

    @Test
    fun filteredRenewalCanResolveParentFromUnfilteredContext() {
        val annual = subscription("parent", BillingCycle.YEARLY)
        val renewal = entry("renewal").copy(parentUuid = annual.uuid, linkType = LINK_TYPE_RENEWAL)
        val result = LedgerCalculator.monthSummary(
            entries = listOf(renewal), month = month, zone = zone, referenceEntries = listOf(annual, renewal),
        )
        assertEquals(10_000L, result.expenseCents)
        assertEquals(0L, result.ordinaryExpenseCents)
        assertEquals(0L, result.monthlySubscriptionCents)
        assertEquals(0L, result.regularExpenseCents)
        assertEquals(10_000L, result.oneTimeOutlayCents)
    }

    @Test
    fun orphanRenewalRetainsActualOutlayWithoutGuessingUnknownCycle() {
        val renewal = entry("orphan").copy(parentUuid = "missing", linkType = LINK_TYPE_RENEWAL)
        val result = LedgerCalculator.monthSummary(listOf(renewal), month, zone)
        assertEquals(10_000L, result.expenseCents)
        assertEquals(0L, result.regularExpenseCents)
        assertEquals(10_000L, result.oneTimeOutlayCents)
    }

    @Test
    fun unconfirmedRenewalsAreNotInventedAndRecordedPaymentsStillCountAfterStopping() {
        val old = subscription("active", BillingCycle.MONTHLY).copy(
            occurredAtMillis = month.minusMonths(1).atDay(5).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        assertEquals(0L, LedgerCalculator.monthSummary(listOf(old), month, zone).regularExpenseCents)
        val paidThenStopped = subscription("stopped", BillingCycle.MONTHLY).copy(isActiveCost = false)
        assertEquals(10_000L, LedgerCalculator.monthSummary(listOf(old, paidThenStopped), month, zone).regularExpenseCents)
    }

    @Test
    fun lockedFxRatesMonthBoundariesAndNonSpendingRowsRemainRespected() {
        val foreign = subscription("usd", BillingCycle.MONTHLY, 1_000L).copy(currency = "USD", fxRateToCny = 7.2)
        val boundary = entry("boundary").copy(
            occurredAtMillis = LocalDate.of(2026, 10, 31).atTime(23, 30).atZone(zone).toInstant().toEpochMilli(),
        )
        val ignored = listOf(
            entry("transfer").copy(type = LedgerEntryType.TRANSFER),
            entry("adjustment").copy(type = LedgerEntryType.ADJUSTMENT),
            entry("deleted").copy(deletedAtMillis = date),
        )
        val rows = ignored + foreign + boundary
        assertEquals(7_200L, LedgerCalculator.monthSummary(rows, month, zone).regularExpenseCents)
        assertEquals(17_200L, LedgerCalculator.monthSummary(rows, month, ZoneId.of("Asia/Shanghai")).regularExpenseCents)
    }

    @Test
    fun averageMonthlyHoldingCostStillIncludesAssetsAndNonMonthlySubscriptions() {
        val rows = listOf(
            entry("asset", 365_000L).copy(costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED, targetDays = 365),
            subscription("monthly", BillingCycle.MONTHLY),
            subscription("annual", BillingCycle.YEARLY, 120_000L),
        )
        val expected = rows.sumOf {
            LedgerCalculator.calculateCostBreakdown(it, date, zone = zone)!!.monthlyCostYuan
        }
        val summary = LedgerCalculator.summarize(rows, date, zone = zone)
        assertEquals(expected, summary.activeMonthlyBurnRateYuan, 0.0001)
        assertEquals(1, summary.activeOneTimeCount)
        assertEquals(2, summary.activeSubscriptionCount)
        val cashMonth = LedgerCalculator.monthSummary(rows, month, zone)
        assertEquals(10_000L, cashMonth.regularExpenseCents)
        assertEquals(485_000L, cashMonth.oneTimeOutlayCents)
    }

    @Test
    fun monthlyCycleHelperAcceptsOnlyCalendarMonthSubscriptions() {
        assertEquals(
            true,
            LedgerCalculator.isMonthlySubscriptionCycle(subscription("m", BillingCycle.MONTHLY)),
        )
        assertEquals(
            true,
            LedgerCalculator.isMonthlySubscriptionCycle(
                subscription("one", BillingCycle.CUSTOM_DAYS).copy(
                    customCycleDays = 1,
                    customCycleUnit = CycleUnit.MONTHS,
                ),
            ),
        )
        assertEquals(
            false,
            LedgerCalculator.isMonthlySubscriptionCycle(subscription("y", BillingCycle.YEARLY)),
        )
        assertEquals(false, LedgerCalculator.isMonthlySubscriptionCycle(entry("food")))
        assertEquals(
            false,
            LedgerCalculator.isMonthlySubscriptionCycle(
                entry("asset").copy(costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED),
            ),
        )
    }
}
