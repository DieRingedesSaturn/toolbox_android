package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerCategoryTest {

    @Test
    fun testFromStorageKnownValue() {
        assertEquals(LedgerCategory.FOOD, LedgerCategory.fromStorage("FOOD"))
        assertEquals(LedgerCategory.SALARY, LedgerCategory.fromStorage("SALARY"))
    }

    @Test
    fun testFromStorageLegacyDigitalMapsToOther() {
        assertEquals(LedgerCategory.OTHER, LedgerCategory.fromStorage("DIGITAL"))
    }

    @Test
    fun testFromStorageNullAndGarbage() {
        assertEquals(LedgerCategory.OTHER, LedgerCategory.fromStorage(null))
        assertEquals(LedgerCategory.OTHER, LedgerCategory.fromStorage("NO_SUCH_CATEGORY"))
        assertEquals(LedgerCategory.OTHER, LedgerCategory.fromStorage(""))
    }

    @Test
    fun testForTypeFiltersAndKeepsOther() {
        val expense = LedgerCategory.forType(LedgerEntryType.EXPENSE)
        assertEquals(
            listOf(
                LedgerCategory.FOOD,
                LedgerCategory.TRANSPORT,
                LedgerCategory.SHOPPING,
                LedgerCategory.HOUSING,
                LedgerCategory.ELECTRONICS,
                LedgerCategory.ENTERTAINMENT,
                LedgerCategory.HEALTH,
                LedgerCategory.EDUCATION,
                LedgerCategory.OTHER,
            ),
            expense,
        )

        val income = LedgerCategory.forType(LedgerEntryType.INCOME)
        assertEquals(
            listOf(
                LedgerCategory.SALARY,
                LedgerCategory.BONUS,
                LedgerCategory.INVESTMENT,
                LedgerCategory.OTHER,
            ),
            income,
        )
    }
}
