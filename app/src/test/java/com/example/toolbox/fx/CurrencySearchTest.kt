package com.example.toolbox.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencySearchTest {

    private val codes = listOf("CNY", "EUR", "GBP", "JPY", "USD")
    private val keys = CurrencySearch.keys(codes)

    private fun search(query: String): List<String> =
        CurrencySearch.filter(codes, query, keys)

    @Test
    fun `blank query returns all codes in order`() {
        assertEquals(codes, search(""))
        assertEquals(codes, search("   "))
    }

    @Test
    fun `matches ISO code case-insensitively`() {
        assertEquals(listOf("USD"), search("usd"))
        assertEquals(listOf("JPY"), search("Jp"))
    }

    @Test
    fun `matches English display name`() {
        assertTrue(search("dollar").contains("USD"))
        assertTrue(search("yen").contains("JPY"))
    }

    @Test
    fun `matches Chinese display name`() {
        assertTrue(search("美元").contains("USD"))
        assertTrue(search("日元").contains("JPY"))
        assertTrue(search("人民币").contains("CNY"))
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<String>(), search("zzzz"))
    }
}
