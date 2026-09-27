package com.example.toolbox.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FxRatesTest {

    private val sample = """
        {"amount":1.0,"base":"EUR","date":"2026-09-25",
         "rates":{"CNY":7.6551,"JPY":179.7,"USD":1.1403}}
    """.trimIndent()

    private fun assertParseFails(json: String) {
        try {
            FxRatesJson.parseFrankfurter(json, 0L)
            fail("expected FxException INVALID_RESPONSE")
        } catch (e: FxException) {
            assertEquals(FxFailure.INVALID_RESPONSE, e.failure)
        }
    }

    @Test
    fun testParseFrankfurterSampleAddsEur() {
        val rates = FxRatesJson.parseFrankfurter(sample, 1234L)
        assertEquals("2026-09-25", rates.date)
        assertEquals(1234L, rates.fetchedAtMillis)
        assertEquals(7.6551, rates.eurRates["CNY"]!!, 0.0001)
        assertEquals(1.0, rates.eurRates["EUR"]!!, 0.0001)
        assertEquals(4, rates.eurRates.size)
        assertEquals(listOf("CNY", "EUR", "JPY", "USD"), rates.currencies)
    }

    @Test
    fun testParseFrankfurterRejectsInvalid() {
        // wrong base
        assertParseFails(sample.replace("\"base\":\"EUR\"", "\"base\":\"USD\""))
        // missing date
        assertParseFails("""{"base":"EUR","rates":{"CNY":7.6}}""")
        // empty rates
        assertParseFails("""{"base":"EUR","date":"2026-09-25","rates":{}}""")
        // non-positive rate
        assertParseFails(
            """{"base":"EUR","date":"2026-09-25","rates":{"CNY":0.0}}""",
        )
        // garbage
        assertParseFails("not json")
    }

    @Test
    fun testCnyPerUnitAndConvert() {
        val rates = FxRatesJson.parseFrankfurter(sample, 0L)
        // USD -> CNY = 7.6551 / 1.1403
        assertEquals(7.6551 / 1.1403, rates.cnyPerUnit("USD")!!, 0.00001)
        assertEquals(7.6551, rates.cnyPerUnit("EUR")!!, 0.0001)
        assertEquals(1.0, rates.cnyPerUnit("CNY")!!, 0.0001)
        // EUR base passthrough: USD -> JPY = 100/1.1403*179.7
        assertEquals(
            100.0 / 1.1403 * 179.7,
            rates.convert(100.0, "USD", "JPY")!!,
            0.001,
        )
        assertEquals(
            100.0 / 1.1403 * 7.6551,
            rates.convert(100.0, "USD", "CNY")!!,
            0.001,
        )
        assertEquals(null, rates.cnyPerUnit("XXX"))
    }

    @Test
    fun testFxRatesJsonRoundTrip() {
        val rates = FxRatesJson.parseFrankfurter(sample, 4242L)
        val restored = FxRatesJson.fromJson(FxRatesJson.toJson(rates))
        assertEquals(rates.date, restored.date)
        assertEquals(rates.fetchedAtMillis, restored.fetchedAtMillis)
        assertEquals(rates.eurRates, restored.eurRates)
        assertTrue(restored.currencies.contains("EUR"))
    }

    @Test
    fun testParseCurrencyApiDropsCryptoAndUppercases() {
        val json = """
            {"date":"2026-09-22","eur":{"cny":7.68368156,"jpy":180.75,
             "usd":1.1474,"btc":0.00002,"eth":0.5}}
        """.trimIndent()
        val rates = FxRatesJson.parseCurrencyApi(json, 99L)
        assertEquals("2026-09-22", rates.date)
        assertEquals(FxSource.CURRENCY_API, rates.source)
        assertTrue(rates.eurRates.containsKey("CNY"))
        assertTrue(rates.eurRates.containsKey("USD"))
        assertTrue(!rates.eurRates.containsKey("BTC"))
        assertEquals(1.1474, rates.eurRates.getValue("USD"), 0.0001)
    }

    @Test
    fun testFxRatesJsonSourceRoundTripAndDefault() {
        val ecbRates = FxRatesJson.parseFrankfurter(sample, 7L)
            .copy(source = FxSource.ECB)
        val restored = FxRatesJson.fromJson(FxRatesJson.toJson(ecbRates))
        assertEquals(FxSource.ECB, restored.source)
        // Old cache entries without a source field default to FRANKFURTER.
        val legacy = """{"date":"2026-09-25","fetchedAtMillis":1,"rates":{"CNY":7.6}}"""
        assertEquals(
            FxSource.FRANKFURTER,
            FxRatesJson.fromJson(legacy).source,
        )
    }

    @Test
    fun testNearestPrefersClosestAtOrBefore() {
        val older = FxRatesJson.parseFrankfurter(sample, 1L)
        val newer = FxRatesJson.parseFrankfurter(
            sample.replace("2026-09-25", "2026-09-30"),
            2L,
        )
        val nearest = FxRatesMath.nearest(
            listOf(older, newer),
            java.time.LocalDate.of(2026, 9, 28),
        )
        assertEquals("2026-09-25", nearest!!.date)
        // Nothing at-or-before → closest after.
        val after = FxRatesMath.nearest(
            listOf(older, newer),
            java.time.LocalDate.of(2026, 9, 20),
        )
        assertEquals("2026-09-25", after!!.date)
        assertTrue(FxRatesMath.nearest(emptyList(), java.time.LocalDate.now()) == null)
    }
}
