package com.example.toolbox.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalculatorTest {

    @Test
    fun testOneTimePurchaseDynamicDaysHeld() {
        val now = 1_700_000_000_000L
        // Purchased 200 days ago for 6000 RMB (600_000 cents), salvage value 1000 RMB (100_000 cents)
        val entry = LedgerEntry(
            uuid = "phone-uuid-1",
            title = "Smartphone",
            amountCents = 600_000L,
            salvageValueCents = 100_000L,
            occurredAtMillis = now - 200 * LedgerCalculator.MILLIS_PER_DAY,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            targetDays = null,
        )

        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)
        assertNotNull(breakdown)
        breakdown!!
        assertEquals(200, breakdown.daysHeld)
        assertEquals(5000.0, breakdown.netCostYuan, 0.001)
        assertEquals(25.0, breakdown.dailyCostYuan, 0.001)
        assertEquals(25.0 * 30.4375, breakdown.monthlyCostYuan, 0.01)
    }

    @Test
    fun testOneTimePurchaseWithTargetLifespan() {
        val now = 1_700_000_000_000L
        // Purchased today (0 days ago) for 7300 RMB, planned target lifespan = 730 days (2 years)
        val entry = LedgerEntry(
            uuid = "laptop-uuid-1",
            title = "Laptop",
            amountCents = 730_000L,
            occurredAtMillis = now,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            targetDays = 730,
        )

        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)!!
        assertEquals(1, breakdown.daysHeld)
        assertEquals(10.0, breakdown.dailyCostYuan, 0.001)
        assertEquals(304.375, breakdown.monthlyCostYuan, 0.001)
        assertEquals(3652.5, breakdown.yearlyCostYuan, 0.001)
    }

    @Test
    fun testPeriodicSubscriptionYearlyAndMonthlyCalculation() {
        val now = 1_700_000_000_000L
        // Yearly membership: 180 RMB / year, started 10 days ago
        val entry = LedgerEntry(
            uuid = "vip-uuid-1",
            title = "Video VIP",
            amountCents = 18_000L,
            occurredAtMillis = now - 10 * LedgerCalculator.MILLIS_PER_DAY,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.YEARLY,
        )

        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)!!
        assertEquals(15.0, breakdown.monthlyCostYuan, 0.001)
        assertEquals(180.0, breakdown.yearlyCostYuan, 0.001)
        assertEquals(180.0 / 365.25, breakdown.dailyCostYuan, 0.001)
        assertEquals(1, breakdown.accumulatedCyclesCount)
        assertNotNull(breakdown.nextRenewalMillis)
    }

    @Test
    fun testStoppedSubscriptionFreezesAccumulationAtRetirement() {
        val occurredAt = 1_700_000_000_000L
        val retiredAt = occurredAt + 100 * LedgerCalculator.MILLIS_PER_DAY
        // Weekly subscription (0.5 RMB/week) retired after 100 days: 14 full cycles + 1 partial = 15
        val entry = LedgerEntry(
            uuid = "stopped-sub-1",
            title = "Weekly Sub",
            amountCents = 50L,
            occurredAtMillis = occurredAt,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.WEEKLY,
            isActiveCost = false,
            retiredAtMillis = retiredAt,
        )

        val now = retiredAt + 50 * LedgerCalculator.MILLIS_PER_DAY
        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)!!
        // Accumulation now counts recorded payments only (the entry itself).
        assertEquals(1, breakdown.accumulatedCyclesCount)
        assertEquals(0.50, breakdown.accumulatedTotalYuan, 0.001)
        assertEquals(100, breakdown.daysHeld)
        assertNull(breakdown.nextRenewalMillis)
        assertNull(breakdown.daysUntilRenewal)
    }

    @Test
    fun testActiveSubscriptionAccumulatesUpToNow() {
        val occurredAt = 1_700_000_000_000L
        val entry = LedgerEntry(
            uuid = "active-sub-1",
            title = "Weekly Sub",
            amountCents = 50L,
            occurredAtMillis = occurredAt,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.WEEKLY,
            isActiveCost = true,
        )

        val now = occurredAt + 100 * LedgerCalculator.MILLIS_PER_DAY
        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)!!
        // Only recorded payments accumulate: the sub entry itself, so far.
        assertEquals(1, breakdown.accumulatedCyclesCount)
        assertEquals(0.50, breakdown.accumulatedTotalYuan, 0.001)
        assertEquals(occurredAt + 105 * LedgerCalculator.MILLIS_PER_DAY, breakdown.nextRenewalMillis)
        assertEquals(5, breakdown.daysUntilRenewal)
    }

    @Test
    fun testSummaryAggregatesActiveBurnRateAndExcludesSoftDeleted() {
        val now = 1_700_000_000_000L
        val entries = listOf(
            // Active one-time asset: 10 RMB/day
            LedgerEntry(
                title = "Phone",
                amountCents = 365_000L,
                occurredAtMillis = now,
                costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
                targetDays = 365,
                isActiveCost = true,
            ),
            // Active custom 30-day subscription: 30 RMB / 30 days = 1 RMB/day
            LedgerEntry(
                title = "VPS",
                amountCents = 3_000L,
                occurredAtMillis = now,
                costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
                billingCycle = BillingCycle.CUSTOM_DAYS,
                customCycleDays = 30,
                isActiveCost = true,
            ),
            // Retired asset: should NOT count toward active daily burn rate
            LedgerEntry(
                title = "Old Tablet",
                amountCents = 200_000L,
                occurredAtMillis = now - 100 * LedgerCalculator.MILLIS_PER_DAY,
                costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
                isActiveCost = false,
            ),
            // Soft-deleted subscription: should be ignored completely
            LedgerEntry(
                title = "Deleted Sub",
                amountCents = 5_000L,
                occurredAtMillis = now,
                costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
                deletedAtMillis = now,
            ),
        )

        val summary = LedgerCalculator.summarize(entries, now)
        assertEquals(11.0, summary.activeDailyBurnRateYuan, 0.001)
        assertEquals(10.0, summary.activeOneTimeDailyYuan, 0.001)
        assertEquals(1.0, summary.activePeriodicDailyYuan, 0.001)
        assertEquals(1, summary.activeOneTimeCount)
        assertEquals(1, summary.activeSubscriptionCount)
        assertEquals(3, summary.totalEntriesCount)
    }

    @Test
    fun testSyncPayloadJsonRoundTrip() {
        val now = 1_700_000_000_000L
        val original = LedgerEntry(
            uuid = "sync-uuid-123",
            title = "Camera Lens",
            amountCents = 450_000L,
            salvageValueCents = 150_000L,
            occurredAtMillis = now - 60 * LedgerCalculator.MILLIS_PER_DAY,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            targetDays = 1000,
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
            serverRevision = 42L,
        )
        val payload = LedgerSyncPayload(
            schemaVersion = LEDGER_SCHEMA_VERSION,
            clientTimestampMillis = now,
            baseServerRevision = 41L,
            entries = listOf(original),
        )

        val json = LedgerSyncSerializer.toJsonString(payload)
        val decoded = LedgerSyncSerializer.fromJsonString(json)

        assertEquals(LEDGER_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(41L, decoded.baseServerRevision)
        assertEquals(1, decoded.entries.size)
        val restored = decoded.entries.first()
        assertEquals("sync-uuid-123", restored.uuid)
        assertEquals("Camera Lens", restored.title)
        assertEquals(450_000L, restored.amountCents)
        assertEquals(150_000L, restored.salvageValueCents)
        assertEquals(CostTrackingMode.ONE_TIME_AMORTIZED, restored.costTrackingMode)
        assertEquals(1000, restored.targetDays)
        assertNull(restored.deletedAtMillis)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun testMonthSummaryRespectsMonthBoundaryInUtc() {
        val zone = ZoneId.of("UTC")
        val lastDayExpense = LedgerEntry(
            title = "March 31 dinner",
            amountCents = 5_000L,
            occurredAtMillis = at(2024, 3, 31, 23, 30, zone),
        )
        val nextMonthExpense = LedgerEntry(
            title = "April 1 breakfast",
            amountCents = 1_200L,
            occurredAtMillis = at(2024, 4, 1, 0, 10, zone),
        )
        val lastDayIncome = LedgerEntry(
            title = "Salary",
            amountCents = 800_000L,
            type = LedgerEntryType.INCOME,
            occurredAtMillis = at(2024, 3, 31, 10, 0, zone),
        )
        val entries = listOf(lastDayExpense, nextMonthExpense, lastDayIncome)

        val march = LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 3), zone)
        assertEquals(5_000L, march.expenseCents)
        assertEquals(800_000L, march.incomeCents)
        assertEquals(795_000L, march.netCents)

        val april = LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 4), zone)
        assertEquals(1_200L, april.expenseCents)
        assertEquals(0L, april.incomeCents)
        assertEquals(-1_200L, april.netCents)
    }

    @Test
    fun testMonthSummaryUsesProvidedZoneForBoundaries() {
        val utc = ZoneId.of("UTC")
        val shanghai = ZoneId.of("Asia/Shanghai")
        // 2024-03-31 16:30 UTC == 2024-04-01 00:30 Asia/Shanghai
        val boundaryMillis = at(2024, 3, 31, 16, 30, utc)
        val entry = LedgerEntry(
            title = "Boundary",
            amountCents = 1_000L,
            occurredAtMillis = boundaryMillis,
        )
        val entries = listOf(entry)

        assertEquals(1_000L, LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 3), utc).expenseCents)
        assertEquals(0L, LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 4), utc).expenseCents)
        assertEquals(0L, LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 3), shanghai).expenseCents)
        assertEquals(1_000L, LedgerCalculator.monthSummary(entries, YearMonth.of(2024, 4), shanghai).expenseCents)
    }

    @Test
    fun testMonthSummaryExcludesDeletedEntries() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2024, 3)
        val kept = LedgerEntry(
            title = "Kept",
            amountCents = 2_000L,
            occurredAtMillis = at(2024, 3, 10, 12, 0, zone),
        )
        val deleted = LedgerEntry(
            title = "Deleted",
            amountCents = 9_999L,
            occurredAtMillis = at(2024, 3, 11, 12, 0, zone),
            deletedAtMillis = at(2024, 3, 12, 12, 0, zone),
        )

        val summary = LedgerCalculator.monthSummary(listOf(kept, deleted), month, zone)
        assertEquals(2_000L, summary.expenseCents)
        assertEquals(1, summary.expenseByCategory.size)
    }

    @Test
    fun testMonthSummaryCategoryBreakdownSortedAndLegacyDigital() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2024, 3)
        val entries = listOf(
            LedgerEntry(title = "Big food", amountCents = 3_000L, category = "FOOD", occurredAtMillis = at(2024, 3, 5, 8, 0, zone)),
            LedgerEntry(title = "Small tech", amountCents = 500L, category = "ELECTRONICS", occurredAtMillis = at(2024, 3, 5, 9, 0, zone)),
            LedgerEntry(title = "Legacy row", amountCents = 1_000L, category = "DIGITAL", occurredAtMillis = at(2024, 3, 6, 9, 0, zone)),
            LedgerEntry(title = "Other row", amountCents = 200L, category = "OTHER", occurredAtMillis = at(2024, 3, 6, 10, 0, zone)),
        )

        val summary = LedgerCalculator.monthSummary(entries, month, zone)
        assertEquals(4_700L, summary.expenseCents)
        assertEquals(
            listOf(
                LedgerCategory.FOOD to 3_000L,
                LedgerCategory.OTHER to 1_200L,
                LedgerCategory.ELECTRONICS to 500L,
            ),
            summary.expenseByCategory,
        )
    }

    @Test
    fun testGroupByDayOrdersDaysAndEntriesDescending() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2024, 3)
        val day10Morning = LedgerEntry(
            title = "Morning",
            amountCents = 100L,
            occurredAtMillis = at(2024, 3, 10, 8, 0, zone),
        )
        val day10Evening = LedgerEntry(
            title = "Evening",
            amountCents = 200L,
            type = LedgerEntryType.INCOME,
            occurredAtMillis = at(2024, 3, 10, 20, 0, zone),
        )
        val day12 = LedgerEntry(
            title = "Next day",
            amountCents = 300L,
            occurredAtMillis = at(2024, 3, 12, 9, 0, zone),
        )
        val otherMonth = LedgerEntry(
            title = "April",
            amountCents = 999L,
            occurredAtMillis = at(2024, 4, 1, 9, 0, zone),
        )

        val groups = LedgerCalculator.groupByDay(
            listOf(day10Morning, day12, day10Evening, otherMonth),
            month,
            zone,
        )
        assertEquals(2, groups.size)
        assertEquals(LocalDate.of(2024, 3, 12), groups[0].date)
        assertEquals(300L, groups[0].expenseCents)
        assertEquals(LocalDate.of(2024, 3, 10), groups[1].date)
        assertEquals(listOf("Evening", "Morning"), groups[1].entries.map { it.title })
        assertEquals(100L, groups[1].expenseCents)
        assertEquals(200L, groups[1].incomeCents)

        assertTrue(LedgerCalculator.groupByDay(entries = emptyList(), month = month, zone = zone).isEmpty())
    }

    @Test
    fun testBaseAmountCentsRounding() {
        // USD 12.50 (1250 cents) at 7.1234 CNY/USD -> 8904.25 -> 8904 CNY cents
        val usd = LedgerEntry(
            uuid = "usd",
            title = "usd",
            amountCents = 1250L,
            currency = "USD",
            fxRateToCny = 7.1234,
            occurredAtMillis = 1_700_000_000_000L,
        )
        assertEquals(8904L, usd.baseAmountCents)

        // JPY 1500 (150000 cents) at 0.05 CNY/JPY -> 7500 CNY cents
        val jpy = usd.copy(
            amountCents = 150_000L,
            currency = "JPY",
            fxRateToCny = 0.05,
        )
        assertEquals(7500L, jpy.baseAmountCents)

        // CNY stays 1:1
        val cny = usd.copy(amountCents = 999L, currency = "CNY", fxRateToCny = 1.0)
        assertEquals(999L, cny.baseAmountCents)
    }

    @Test
    fun testNetCostCentsUsesForeignSalvage() {
        val entry = LedgerEntry(
            uuid = "cam",
            title = "Camera",
            amountCents = 1250L,
            currency = "USD",
            fxRateToCny = 7.1234,
            salvageValueCents = 500L,
            occurredAtMillis = 1_700_000_000_000L,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
        )
        // 1250*7.1234 = 8904.25 -> 8904; 500*7.1234 = 3561.7 -> 3562
        assertEquals(3562L, entry.baseSalvageCents)
        assertEquals(8904L - 3562L, entry.netCostCents)
    }

    @Test
    fun testMonthSummaryAndGroupByDaySumCnyBase() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2024, 3)
        val cnyExpense = LedgerEntry(
            uuid = "cny",
            title = "cny",
            amountCents = 10_000L,
            occurredAtMillis = at(2024, 3, 10, 12, 0, zone),
        )
        val usdExpense = LedgerEntry(
            uuid = "usd",
            title = "usd",
            amountCents = 1250L,
            currency = "USD",
            fxRateToCny = 7.1234,
            occurredAtMillis = at(2024, 3, 10, 13, 0, zone),
        )
        val jpyIncome = LedgerEntry(
            uuid = "jpy",
            title = "jpy",
            amountCents = 150_000L,
            currency = "JPY",
            fxRateToCny = 0.05,
            type = LedgerEntryType.INCOME,
            occurredAtMillis = at(2024, 3, 11, 12, 0, zone),
        )

        val summary = LedgerCalculator.monthSummary(
            listOf(cnyExpense, usdExpense, jpyIncome),
            month,
            zone,
        )
        assertEquals(10_000L + 8904L, summary.expenseCents)
        assertEquals(7500L, summary.incomeCents)

        val groups = LedgerCalculator.groupByDay(
            listOf(cnyExpense, usdExpense, jpyIncome),
            month,
            zone,
        )
        assertEquals(18_904L, groups.first { it.entries.any { e -> e.uuid == "usd" } }.expenseCents)
    }

    @Test
    fun testFormatMoneyCurrencies() {
        assertEquals("¥12.34", LedgerCalculator.formatMoney(1234L, "CNY"))
        assertEquals("$12.34", LedgerCalculator.formatMoney(1234L, "USD"))
        assertEquals("€12.34", LedgerCalculator.formatMoney(1234L, "EUR"))
        assertEquals("JP¥1500", LedgerCalculator.formatMoney(150_000L, "JPY"))
        assertEquals("XYZ 12.34", LedgerCalculator.formatMoney(1234L, "XYZ"))
    }

    @Test
    fun testPeriodicForeignCyclePriceUsesBase() {
        val now = 1_700_000_000_000L
        val entry = LedgerEntry(
            uuid = "sub",
            title = "Sub",
            amountCents = 1250L,
            currency = "USD",
            fxRateToCny = 7.1234,
            occurredAtMillis = now,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.MONTHLY,
        )
        val breakdown = LedgerCalculator.calculateCostBreakdown(entry, now)!!
        // cycle price = 89.04 CNY per month
        assertEquals(89.04, breakdown.monthlyCostYuan, 0.001)
    }

    @Test
    fun testPeriodicWithRecordedRenewals() {
        // JPY subscription: first payment at 0.0426, renewal at 0.0450.
        val zone = ZoneId.of("UTC")
        val start = LocalDate.of(2026, 8, 22)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val renewalDate = LocalDate.of(2026, 9, 22)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 9, 25)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val sub = LedgerEntry(
            uuid = "sub-jpy",
            title = "JPY Sub",
            amountCents = 286_000L,
            currency = "JPY",
            fxRateToCny = 0.0426,
            occurredAtMillis = start,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.MONTHLY,
        )
        val renewal = LedgerEntry(
            uuid = "r1",
            title = "JPY Sub",
            amountCents = 286_000L,
            currency = "JPY",
            fxRateToCny = 0.0450,
            occurredAtMillis = renewalDate,
            parentUuid = sub.uuid,
            linkType = LINK_TYPE_RENEWAL,
            renewalIndex = 1,
        )
        val breakdown = LedgerCalculator.calculateCostBreakdown(
            sub, now, children = listOf(renewal), zone = zone,
        )!!
        // 2 recorded payments; burn uses the latest payment's rate.
        assertEquals(2, breakdown.accumulatedCyclesCount)
        assertEquals(
            (286_000 * 0.0426 + 286_000 * 0.0450) / 100.0,
            breakdown.accumulatedTotalYuan,
            0.01,
        )
        assertEquals(286_000 * 0.0450 / 100.0, breakdown.monthlyCostYuan, 0.001)
        // Next renewal is the calendar date, not a 30.44-day average.
        assertEquals(
            LocalDate.of(2026, 10, 22)
                .atTime(0, 0).atZone(zone).toInstant().toEpochMilli(),
            breakdown.nextRenewalMillis,
        )
    }

    @Test
    fun testRenewalCountsInItsOwnMonth() {
        val zone = ZoneId.of("UTC")
        val renewal = LedgerEntry(
            uuid = "r1",
            title = "Sub renewal",
            amountCents = 10_000L,
            currency = "CNY",
            occurredAtMillis = LocalDate.of(2026, 9, 22)
                .atTime(0, 0).atZone(zone).toInstant().toEpochMilli(),
            parentUuid = "parent",
            linkType = LINK_TYPE_RENEWAL,
            renewalIndex = 1,
        )
        val summary = LedgerCalculator.monthSummary(
            listOf(renewal),
            YearMonth.of(2026, 9),
            zone,
        )
        assertEquals(10_000L, summary.expenseCents)
    }

    @Test
    fun testSoldAssetNetCostMinusSale() {
        val now = 1_700_000_000_000L
        val asset = LedgerEntry(
            uuid = "phone",
            title = "Phone",
            amountCents = 500_000L,
            occurredAtMillis = now - 100 * LedgerCalculator.MILLIS_PER_DAY,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            salvageValueCents = 50_000L,
            isActiveCost = false,
            retiredAtMillis = now,
            disposalType = DISPOSAL_SOLD,
        )
        val sale = LedgerEntry(
            uuid = "sale-1",
            title = "Sold: Phone",
            amountCents = 200_000L,
            type = LedgerEntryType.INCOME,
            occurredAtMillis = now,
            parentUuid = asset.uuid,
            linkType = LINK_TYPE_SALE,
        )
        val breakdown = LedgerCalculator.calculateCostBreakdown(
            asset, now, children = listOf(sale),
        )!!
        // ¥5000 price − ¥2000 sale = ¥3000 final cost.
        assertEquals(3000.0, breakdown.netCostYuan, 0.001)
        assertEquals(100, breakdown.daysHeld)
    }

    @Test
    fun testScrappedAssetIgnoresSalvage() {
        val now = 1_700_000_000_000L
        val asset = LedgerEntry(
            uuid = "broken",
            title = "Broken",
            amountCents = 10_000L,
            salvageValueCents = 9_000L,
            occurredAtMillis = now - 10 * LedgerCalculator.MILLIS_PER_DAY,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            isActiveCost = false,
            retiredAtMillis = now,
            disposalType = DISPOSAL_SCRAPPED,
        )
        val breakdown = LedgerCalculator.calculateCostBreakdown(
            asset, now, children = emptyList(),
        )!!
        assertEquals(100.0, breakdown.netCostYuan, 0.001)
        // Legacy retired asset (no disposalType) keeps the salvage estimate.
        val legacy = asset.copy(disposalType = null)
        assertEquals(
            10.0,
            LedgerCalculator.calculateCostBreakdown(legacy, now)!!.netCostYuan,
            0.001,
        )
    }

    @Test
    fun testSoldAssetExcludedFromBurnAndSaleCountsAsIncome() {
        val zone = ZoneId.of("UTC")
        val monthStart = YearMonth.now(zone).atDay(1)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val asset = LedgerEntry(
            uuid = "phone",
            title = "Phone",
            amountCents = 365_000L,
            occurredAtMillis = monthStart,
            costTrackingMode = CostTrackingMode.ONE_TIME_AMORTIZED,
            targetDays = 365,
            isActiveCost = false,
            retiredAtMillis = monthStart + 10 * LedgerCalculator.MILLIS_PER_DAY,
            disposalType = DISPOSAL_SOLD,
        )
        val sale = LedgerEntry(
            uuid = "sale-1",
            title = "Sold: Phone",
            amountCents = 100_000L,
            type = LedgerEntryType.INCOME,
            occurredAtMillis = monthStart + 10 * LedgerCalculator.MILLIS_PER_DAY,
            parentUuid = asset.uuid,
            linkType = LINK_TYPE_SALE,
        )
        val summary = LedgerCalculator.summarize(
            listOf(asset, sale),
            nowMillis = monthStart + 15 * LedgerCalculator.MILLIS_PER_DAY,
            zone = zone,
        )
        assertEquals(0.0, summary.activeDailyBurnRateYuan, 0.001)
        val month = LedgerCalculator.monthSummary(
            listOf(asset, sale),
            YearMonth.now(zone),
            zone,
        )
        assertEquals(100_000L, month.incomeCents)
        assertEquals(365_000L, month.expenseCents)
    }

    @Test
    fun testRecurringIncomeFields() {
        val zone = ZoneId.of("UTC")
        val start = YearMonth.now(zone).atDay(1)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val salary = LedgerEntry(
            uuid = "salary",
            title = "Salary",
            amountCents = 300_000L, // ¥3000/mo
            type = LedgerEntryType.INCOME,
            occurredAtMillis = start,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.MONTHLY,
            isActiveCost = true,
        )
        val sub = LedgerEntry(
            uuid = "sub",
            title = "Sub",
            amountCents = 3_000L, // ¥30/mo
            occurredAtMillis = start,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.MONTHLY,
        )
        val summary = LedgerCalculator.summarize(
            listOf(salary, sub),
            nowMillis = start + LedgerCalculator.MILLIS_PER_DAY,
            zone = zone,
        )
        assertEquals(3000.0, summary.recurringIncomeMonthlyYuan, 0.001)
        assertEquals(1, summary.recurringIncomeCount)
        // Burn rate stays expense-only.
        assertEquals(30.0, summary.activeMonthlyBurnRateYuan, 0.001)
    }

    @Test
    fun testTagBreakdownMultiTagAndDeleted() {
        val zone = ZoneId.of("UTC")
        val inMonth = YearMonth.now(zone).atDay(5)
            .atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
        val liveTag = LedgerTag(
            uuid = "t-live", name = "Food", colorArgb = 1,
            sortOrder = 0, createdAtMillis = 0, updatedAtMillis = 0,
        )
        val deadTag = liveTag.copy(uuid = "t-dead", deletedAtMillis = 1L)
        val entries = listOf(
            LedgerEntry(
                uuid = "a", title = "a", amountCents = 1000L,
                occurredAtMillis = inMonth, tagUuids = listOf("t-live"),
            ),
            LedgerEntry(
                uuid = "b", title = "b", amountCents = 2000L,
                occurredAtMillis = inMonth, tagUuids = listOf("t-live", "t-dead"),
            ),
            LedgerEntry(
                uuid = "c", title = "c", amountCents = 3000L,
                occurredAtMillis = inMonth, tagUuids = emptyList(),
            ),
        )
        val rows = LedgerCalculator.tagBreakdown(
            entries, YearMonth.now(zone), zone, tags = listOf(liveTag, deadTag),
        ).toMap()
        // "b" counts under t-live only (t-dead is deleted); "c" is untagged.
        assertEquals(3000L, rows.getValue(liveTag))
        assertEquals(3000L, rows.getValue(null))
    }

    @Test
    fun testSaleUuidDeterministic() {
        assertEquals(
            SubscriptionRenewals.saleUuid("asset-1"),
            SubscriptionRenewals.saleUuid("asset-1"),
        )
        assertFalse(
            SubscriptionRenewals.saleUuid("asset-1") ==
                SubscriptionRenewals.saleUuid("asset-2"),
        )
    }

    @Test
    fun testCustomMonthsBurnAverage() {
        // Every 2 months → cycleDays = 2 * 30.4375 = 60.875; daily = price/days.
        val entry = LedgerEntry(
            uuid = "sub-cm",
            title = "Custom",
            amountCents = 24_000L,
            currency = "CNY",
            occurredAtMillis = 1_700_000_000_000L,
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            billingCycle = BillingCycle.CUSTOM_DAYS,
            customCycleDays = 2,
            customCycleUnit = CycleUnit.MONTHS,
        )
        val breakdown = LedgerCalculator.calculateCostBreakdown(
            entry,
            1_700_000_000_000L + 10 * 86_400_000L,
            emptyList(),
        )!!
        val expectedDaily = entry.baseAmountCents / 100.0 / (2 * 30.4375)
        assertEquals(expectedDaily, breakdown.dailyCostYuan, 0.0001)
        assertEquals(expectedDaily * 30.4375, breakdown.monthlyCostYuan, 0.0001)
    }

    @Test
    fun testTransfersAndAdjustmentsExcludedFromStats() {
        val at = Instant.ofEpochMilli(1_700_000_000_000L)
            .atZone(ZoneId.of("UTC"))
        val month = YearMonth.from(at)
        fun row(uuid: String, type: LedgerEntryType, cents: Long) = LedgerEntry(
            uuid = uuid,
            title = uuid,
            amountCents = cents,
            currency = "CNY",
            type = type,
            occurredAtMillis = 1_700_000_000_000L,
            accountUuid = "a",
        )
        val expense = row("x", LedgerEntryType.EXPENSE, 1_000L)
        val income = row("i", LedgerEntryType.INCOME, 2_000L)
        val transfer = row("t", LedgerEntryType.TRANSFER, 9_999L)
            .copy(toAccountUuid = "b")
        val adjustment = row("j", LedgerEntryType.ADJUSTMENT, 8_888L)
        val all = listOf(expense, income, transfer, adjustment)
        val ms = LedgerCalculator.monthSummary(all, month, ZoneId.of("UTC"))
        assertEquals(1_000L, ms.expenseCents)
        assertEquals(2_000L, ms.incomeCents)
        val breakdown = LedgerCalculator.tagBreakdown(
            all, month, ZoneId.of("UTC"), LedgerEntryType.EXPENSE, emptyList(),
        )
        assertEquals(1_000L, breakdown.sumOf { it.second })
        val summary = LedgerCalculator.summarize(all, 1_700_000_000_000L)
        assertEquals(0, summary.activeOneTimeCount)

        // The Transactions list keeps transfers but drops reconcile
        // adjustments (they live in the account's own entry list).
        val groups = LedgerCalculator.groupByDay(all, month, ZoneId.of("UTC"))
        assertEquals(1, groups.size)
        assertEquals(
            listOf("i", "t", "x"),
            groups[0].entries.map { it.uuid }.sorted(),
        )

        // A day containing only an adjustment produces no group at all.
        val adjustOnly = listOf(adjustment)
        assertTrue(
            LedgerCalculator.groupByDay(adjustOnly, month, ZoneId.of("UTC")).isEmpty(),
        )
    }
}
