package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSummaryConfigTest {

    @Test
    fun absentPreferenceKeepsTheOriginalDefaultItems() {
        assertEquals(HomeSummaryConfig.DEFAULT_ITEMS, HomeSummaryConfig.parseItems(null))
        assertFalse(HomeSummaryItem.DATE in HomeSummaryConfig.DEFAULT_ITEMS)
        assertFalse(HomeSummaryItem.MEMORY in HomeSummaryConfig.DEFAULT_ITEMS)
        assertFalse(HomeSummaryItem.NETWORK in HomeSummaryConfig.DEFAULT_ITEMS)
        assertFalse(HomeSummaryItem.TODAY_SPEND in HomeSummaryConfig.DEFAULT_ITEMS)
    }

    @Test
    fun parseDropsUnknownTokensAndKeepsKnownOnes() {
        val parsed = HomeSummaryConfig.parseItems("BATTERY,NOT_A_REAL_ITEM,DATE,")
        assertEquals(setOf(HomeSummaryItem.BATTERY, HomeSummaryItem.DATE), parsed)
    }

    @Test
    fun formatThenParseRoundTripsInEnumOrder() {
        val items = setOf(
            HomeSummaryItem.ASSETS,
            HomeSummaryItem.DATE,
            HomeSummaryItem.NETWORK,
        )
        val formatted = HomeSummaryConfig.formatItems(items)
        assertEquals("DATE,NETWORK,ASSETS", formatted)
        assertEquals(items, HomeSummaryConfig.parseItems(formatted))
    }
}
