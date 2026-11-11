package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerOverviewOrderTest {
    @Test
    fun missingValueUsesDefaultOrder() {
        assertEquals(LedgerOverviewOrder.DEFAULT, LedgerOverviewOrder.parse(null))
        assertEquals(LedgerOverviewOrder.DEFAULT, LedgerOverviewOrder.parse(""))
    }

    @Test
    fun storedOrderIsKept() {
        assertEquals(
            listOf(
                LedgerOverviewCard.COST,
                LedgerOverviewCard.MONTH,
                LedgerOverviewCard.RECENT,
                LedgerOverviewCard.TAGS,
                LedgerOverviewCard.TREND,
            ),
            LedgerOverviewOrder.parse("COST,MONTH,RECENT,TAGS,TREND"),
        )
    }

    @Test
    fun unknownAndDuplicateCardsAreDroppedAndMissingCardsAppended() {
        assertEquals(
            listOf(
                LedgerOverviewCard.COST,
                LedgerOverviewCard.MONTH,
                LedgerOverviewCard.RECENT,
                LedgerOverviewCard.TAGS,
                LedgerOverviewCard.TREND,
            ),
            LedgerOverviewOrder.parse("COST,MONTH,COST,UNKNOWN"),
        )
    }

    @Test
    fun formatRoundTrips() {
        val order = listOf(
            LedgerOverviewCard.TREND,
            LedgerOverviewCard.COST,
            LedgerOverviewCard.MONTH,
            LedgerOverviewCard.TAGS,
            LedgerOverviewCard.RECENT,
        )
        assertEquals(order, LedgerOverviewOrder.parse(LedgerOverviewOrder.format(order)))
    }

    @Test
    fun homeModuleOrderKeepsStoredAndAppendsMissing() {
        assertEquals(HomeModuleOrder.DEFAULT, HomeModuleOrder.parse(null))
        assertEquals(
            listOf(
                ToolboxModule.LEDGER,
                ToolboxModule.NETWORK,
                ToolboxModule.MONITOR,
                ToolboxModule.TOTP,
                ToolboxModule.FX,
                ToolboxModule.ASTRONOMY,
                ToolboxModule.USAGE,
                ToolboxModule.DEVICE,
                ToolboxModule.LOCATION,
                ToolboxModule.PRIVACY,
            ),
            HomeModuleOrder.parse("LEDGER,NETWORK,LEDGER,HOME,UNKNOWN"),
        )
    }
}
