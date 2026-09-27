package com.example.toolbox.ledger

import com.example.toolbox.fx.FxRates
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAccountsTest {

    private val zone = ZoneId.of("UTC")

    private fun account(
        uuid: String,
        currency: String = "CNY",
        openingCents: Long = 0L,
        openingAt: Long = 0L,
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
        archived: Boolean = false,
    ) = LedgerAccount(
        uuid = uuid,
        name = uuid,
        currency = currency,
        openingBalanceCents = openingCents,
        openingAtMillis = openingAt,
        colorArgb = 0xFF000000.toInt(),
        updatedAtMillis = updatedAt,
        deletedAtMillis = deletedAt,
        isArchived = archived,
    )

    private fun entry(
        uuid: String,
        type: LedgerEntryType,
        amountCents: Long,
        currency: String = "CNY",
        occurredAt: Long = 0L,
        accountUuid: String? = null,
        accountAmountCents: Long? = null,
        toAccountUuid: String? = null,
        toAmountCents: Long? = null,
        deletedAt: Long? = null,
    ) = LedgerEntry(
        uuid = uuid,
        title = uuid,
        amountCents = amountCents,
        currency = currency,
        fxRateToCny = 1.0,
        type = type,
        occurredAtMillis = occurredAt,
        deletedAtMillis = deletedAt,
        accountUuid = accountUuid,
        accountAmountCents = accountAmountCents,
        toAccountUuid = toAccountUuid,
        toAmountCents = toAmountCents,
    )

    // ---------- balance ----------

    @Test
    fun testOpeningBalanceCounts() {
        val acc = account("a", openingCents = 50_000L)
        assertEquals(50_000L, LedgerAccounts.balance(acc, emptyList()))
    }

    @Test
    fun testEntriesBeforeOpeningAreExcluded() {
        val acc = account("a", openingCents = 10_000L, openingAt = 1_000L)
        val before = entry(
            "e1", LedgerEntryType.EXPENSE, 9_999L,
            occurredAt = 500L, accountUuid = "a",
        )
        val after = entry(
            "e2", LedgerEntryType.EXPENSE, 1_000L,
            occurredAt = 2_000L, accountUuid = "a",
        )
        assertEquals(9_000L, LedgerAccounts.balance(acc, listOf(before, after)))
    }

    @Test
    fun testIncomeAndExpensePost() {
        val acc = account("a", openingCents = 0L)
        val entries = listOf(
            entry("i", LedgerEntryType.INCOME, 10_000L, occurredAt = 1L, accountUuid = "a"),
            entry("x", LedgerEntryType.EXPENSE, 3_500L, occurredAt = 2L, accountUuid = "a"),
            // another account's rows must not leak in
            entry("o", LedgerEntryType.EXPENSE, 99_999L, occurredAt = 3L, accountUuid = "b"),
        )
        assertEquals(6_500L, LedgerAccounts.balance(acc, entries))
    }

    @Test
    fun testTransferBothSides() {
        val a = account("a", openingCents = 10_000L)
        val b = account("b", currency = "USD")
        val transfer = entry(
            "t", LedgerEntryType.TRANSFER, 2_000L,
            occurredAt = 1L, accountUuid = "a",
            toAccountUuid = "b", toAmountCents = 28_000L,
        )
        assertEquals(8_000L, LedgerAccounts.balance(a, listOf(transfer)))
        assertEquals(28_000L, LedgerAccounts.balance(b, listOf(transfer)))
    }

    @Test
    fun testAdjustmentSigned() {
        val acc = account("a", openingCents = 1_000L)
        val up = entry(
            "u", LedgerEntryType.ADJUSTMENT, 500L,
            occurredAt = 1L, accountUuid = "a",
        )
        val down = entry(
            "d", LedgerEntryType.ADJUSTMENT, -300L,
            occurredAt = 2L, accountUuid = "a",
        )
        assertEquals(1_200L, LedgerAccounts.balance(acc, listOf(up, down)))
    }

    @Test
    fun testForeignAccountUsesAccountAmount() {
        val acc = account("usd", currency = "USD", openingCents = 0L)
        // Entry recorded in CNY but posts 500 JPY to the account.
        val e = entry(
            "f", LedgerEntryType.EXPENSE, 100L,
            currency = "CNY", occurredAt = 1L,
            accountUuid = "usd", accountAmountCents = 2_100L,
        )
        assertEquals(-2_100L, LedgerAccounts.balance(acc, listOf(e)))
    }

    @Test
    fun testLegacyJpyEntryOnCnyAccountConverts() {
        // v2 legacy row: foreign currency, no accountAmountCents.
        val acc = account("cny", currency = "CNY", openingCents = 0L)
        val e = entry(
            "jpy", LedgerEntryType.EXPENSE, 286_000L,
            currency = "JPY", occurredAt = 1L, accountUuid = "cny",
            accountAmountCents = null,
        ).copy(fxRateToCny = 0.042628)
        // 286000 × 0.042628 = 12191.608 → 12192 CNY cents.
        assertEquals(-12_192L, LedgerAccounts.balance(acc, listOf(e)))
    }

    @Test
    fun testLegacyUsdIncomeOnCnyAccountConverts() {
        val acc = account("cny", currency = "CNY", openingCents = 0L)
        val e = entry(
            "usd", LedgerEntryType.INCOME, 2_000L,
            currency = "USD", occurredAt = 1L, accountUuid = "cny",
            accountAmountCents = null,
        ).copy(fxRateToCny = 6.7115)
        // 2000 × 6.7115 = 13423 CNY cents.
        assertEquals(13_423L, LedgerAccounts.balance(acc, listOf(e)))
    }

    @Test
    fun testExplicitAccountAmountBeatsFxConversion() {
        val acc = account("cny", currency = "CNY", openingCents = 0L)
        val e = entry(
            "jpy", LedgerEntryType.EXPENSE, 286_000L,
            currency = "JPY", occurredAt = 1L, accountUuid = "cny",
            accountAmountCents = 555L,
        ).copy(fxRateToCny = 0.042628)
        assertEquals(-555L, LedgerAccounts.balance(acc, listOf(e)))
    }

    @Test
    fun testDeletedEntriesDontCount() {
        val acc = account("a")
        val e = entry(
            "x", LedgerEntryType.EXPENSE, 100L,
            occurredAt = 1L, accountUuid = "a", deletedAt = 9L,
        )
        assertEquals(0L, LedgerAccounts.balance(acc, listOf(e)))
    }

    // ---------- total assets ----------

    private fun rates(vararg pairs: Pair<String, Double>) = FxRates(
        date = "2026-10-22",
        eurRates = mapOf(*pairs) + ("CNY" to 7.1),
        fetchedAtMillis = 0L,
    )

    // FxRates stores EUR-based rates; cnyPerUnit(code) = eurRates[CNY]/eurRates[code].
    // Use eur values that give USD→CNY = 7.1 exactly: eurRates USD=1.0, CNY=7.1.

    @Test
    fun testTotalAssetsAllCny() {
        val a = account("a", openingCents = 1_000L)
        val b = account("b", openingCents = 500L)
        val (total, all) = LedgerAccounts.totalAssetsCnyCents(
            listOf(a, b),
            emptyList(),
            null,
        )
        assertEquals(1_500L, total)
        assertTrue(all)
    }

    @Test
    fun testTotalAssetsForeignConvertsWithRate() {
        val cny = account("c", openingCents = 10_000L)
        val usd = account("u", currency = "USD", openingCents = 700L)
        // eurRates USD=1.0 + CNY=7.1 → cnyPerUnit(USD) = 7.1; $7.00 → ¥49.70.
        val (total, all) = LedgerAccounts.totalAssetsCnyCents(
            listOf(cny, usd),
            emptyList(),
            rates("USD" to 1.0),
        )
        assertEquals(14_970L, total)
        assertTrue(all)
    }

    @Test
    fun testTotalAssetsWithoutRateExcludesForeign() {
        val cny = account("c", openingCents = 10_000L)
        val usd = account("u", currency = "USD", openingCents = 700L)
        val (total, all) = LedgerAccounts.totalAssetsCnyCents(
            listOf(cny, usd),
            emptyList(),
            null,
        )
        assertEquals(10_000L, total)
        assertFalse(all)
    }

    @Test
    fun testCrossRateAutoFill() {
        // 1,000 entry cents at CNY rate 1.0 → account units at 7.1 CNY/unit:
        // 1000 / 7.1 = 140.84… → truncated to 140.
        assertEquals(
            140L,
            LedgerAccounts.accountAmountForEntry(1_000L, 1.0, 7.1),
        )
        // Entry in JPY (0.048 CNY/unit) into USD account (7.1 CNY/unit):
        // 10000 × 0.048 / 7.1 = 67.60… → 67.
        assertEquals(
            67L,
            LedgerAccounts.accountAmountForEntry(10_000L, 0.048, 7.1),
        )
    }

    // ---------- default account ----------

    @Test
    fun testDefaultAccountIsDeterministicAndEpochZero() {
        val zh = LedgerAccounts.defaultAccount("默认账户")
        val en = LedgerAccounts.defaultAccount("Default account")
        assertEquals(LedgerAccounts.DEFAULT_ACCOUNT_UUID, zh.uuid)
        assertEquals(zh.uuid, en.uuid)
        assertEquals("CNY", zh.currency)
        assertEquals(0L, zh.createdAtMillis)
        assertEquals(0L, zh.updatedAtMillis)
        assertEquals(0L, zh.openingAtMillis)
        assertEquals("默认账户", zh.name)
        assertEquals("Default account", en.name)
    }

    // ---------- merge / replace ----------

    @Test
    fun testMergeAccountsLwwAndTombstone() {
        val seeded = LedgerAccounts.defaultAccount("默认账户") // updatedAt = 0
        val renamed = seeded.copy(name = "Cash", updatedAtMillis = 1_000L)
        val merged = LedgerSyncMerge.mergeAccounts(listOf(seeded), listOf(renamed))
        assertEquals("Cash", merged.single().name)

        val tombstoned = seeded.copy(deletedAtMillis = 2_000L, updatedAtMillis = 2_000L)
        val merged2 = LedgerSyncMerge.mergeAccounts(listOf(seeded), listOf(tombstoned))
        assertEquals(2_000L, merged2.single().deletedAtMillis)
        assertEquals(LedgerSyncStatus.SYNCED, merged2.single().syncStatus)
    }

    @Test
    fun testPlanReplaceAccountTombstones() {
        val localOnly = account("old")
        val incoming = account("new")
        val plan = LedgerBackup.planReplace(
            local = emptyList(),
            incoming = emptyList(),
            localAccounts = listOf(localOnly),
            incomingAccounts = listOf(incoming),
            nowMillis = 5_000L,
        )
        val written = plan.accountWrites.associateBy { it.uuid }
        assertEquals(5_000L, written["new"]?.updatedAtMillis)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, written["new"]?.syncStatus)
        assertEquals(5_000L, written["old"]?.deletedAtMillis)
        assertEquals(1, plan.deletedAccountCount)
    }

    // ---------- serializer ----------

    @Test
    fun testSerializerAccountAndEntryFieldsRoundTrip() {
        val acc = LedgerAccount(
            uuid = "acct-1",
            name = "Travel",
            currency = "USD",
            openingBalanceCents = -12_345L,
            openingAtMillis = 1_726_000_000_000L,
            colorArgb = 0xFF83C092.toInt(),
            emoji = "💼",
            sortOrder = 3,
            isArchived = true,
            createdAtMillis = 11L,
            updatedAtMillis = 22L,
            deletedAtMillis = 33L,
        )
        val transfer = entry(
            "t1", LedgerEntryType.TRANSFER, 5_000L,
            accountUuid = "acct-1", toAccountUuid = "acct-2", toAmountCents = 700L,
        )
        val payload = LedgerSyncPayload(
            entries = listOf(transfer),
            accounts = listOf(acc),
        )
        val round = LedgerSyncSerializer.fromJsonString(
            LedgerSyncSerializer.toJsonString(payload, indentSpaces = 0),
        )
        val r = round.accounts.single()
        assertEquals(acc, r)
        val rt = round.entries.single()
        assertEquals("acct-1", rt.accountUuid)
        assertEquals("acct-2", rt.toAccountUuid)
        assertEquals(700L, rt.toAmountCents)
        assertEquals(LedgerEntryType.TRANSFER, rt.type)
    }

    @Test
    fun testV2EntryDefaultsToDefaultAccount() {
        val json = """
            {"schemaVersion": 2, "entries": [{
              "uuid": "e1", "title": "old", "amountCents": 100,
              "currency": "CNY", "fxRateToCny": 1.0, "type": "EXPENSE",
              "category": "FOOD", "occurredAtMillis": 1000,
              "costTrackingMode": "NONE"
            }]}
        """.trimIndent()
        val payload = LedgerSyncSerializer.fromJsonString(json)
        assertEquals(
            LedgerAccounts.DEFAULT_ACCOUNT_UUID,
            payload.entries.single().accountUuid,
        )
    }
}
