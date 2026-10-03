package com.example.toolbox.fx

import org.junit.Assert.assertEquals
import org.junit.Test

class FxPinnedCurrenciesTest {

    @Test
    fun testParseKeepsOrderAndDropsInvalidDuplicateAndLedgerCodes() {
        assertEquals(
            listOf("HKD", "KRW", "GBP"),
            FxPinnedCurrencies.parse(" hkd,KRW,,GB,usd,HKD,GBP,KRW1"),
        )
        assertEquals(emptyList<String>(), FxPinnedCurrencies.parse(null))
    }

    @Test
    fun testFormatRoundTrips() {
        val pinned = listOf("HKD", "GBP")
        assertEquals(pinned, FxPinnedCurrencies.parse(FxPinnedCurrencies.format(pinned)))
    }

    @Test
    fun testToggleAppendsRemovesAndIgnoresLedgerCurrencies() {
        assertEquals(listOf("HKD", "GBP"), FxPinnedCurrencies.toggle(listOf("HKD"), "GBP"))
        assertEquals(listOf("GBP"), FxPinnedCurrencies.toggle(listOf("HKD", "GBP"), "HKD"))
        assertEquals(listOf("HKD"), FxPinnedCurrencies.toggle(listOf("HKD"), "JPY"))
    }

    @Test
    fun testFeaturedPutsLedgerCurrenciesFirst() {
        assertEquals(
            LEDGER_CURRENCIES + listOf("HKD", "GBP"),
            FxPinnedCurrencies.featured(listOf("HKD", "GBP")),
        )
    }
}
