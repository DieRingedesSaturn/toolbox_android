package com.example.toolbox.ledger

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCsvTest {

    private val zone = ZoneId.of("UTC")
    private val headers = listOf(
        "Date", "Type", "Category", "Title", "Amount",
        "Currency", "Rate (CNY)", "CNY amount", "Note", "Cost mode", "UUID",
    )

    private fun buildCsv(entries: List<LedgerEntry>): String = LedgerCsv.build(
        entries = entries,
        headers = headers,
        typeLabel = { it.name },
        tagsLabel = { it.tagUuids.joinToString("、") },
        costModeLabel = { it.name },
        zone = zone,
    )

    private fun entry(
        uuid: String = "uuid-1",
        title: String = "Coffee",
        note: String = "",
        amountCents: Long = 500L,
        currency: String = "CNY",
        fxRateToCny: Double = 1.0,
        fxRateDate: String? = null,
        occurredAtMillis: Long = 1_700_000_000_000L,
        deletedAt: Long? = null,
    ) = LedgerEntry(
        uuid = uuid,
        title = title,
        amountCents = amountCents,
        currency = currency,
        fxRateToCny = fxRateToCny,
        fxRateDate = fxRateDate,
        occurredAtMillis = occurredAtMillis,
        note = note,
        deletedAtMillis = deletedAt,
    )

    @Test
    fun testBomAndHeaderRow() {
        val csv = buildCsv(listOf(entry()))
        assertTrue(
            csv.startsWith(
                "\uFEFFDate,Type,Category,Title,Amount," +
                    "Currency,Rate (CNY),CNY amount,Note,Cost mode,UUID",
            ),
        )
    }

    @Test
    fun testCrlfLineEndings() {
        val csv = buildCsv(listOf(entry(), entry(uuid = "uuid-2")))
        assertTrue(csv.endsWith("\r\n"))
        val withoutCrlf = csv.replace("\r\n", "")
        assertFalse(withoutCrlf.contains('\n'))
        assertFalse(withoutCrlf.contains('\r'))
    }

    @Test
    fun testQuotingCommaQuoteNewline() {
        val csv = buildCsv(
            listOf(entry(title = "a,b", note = "say \"hi\"\nsecond line")),
        )
        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.contains("\"a,b\""))
        assertTrue(dataLine.contains("\"say \"\"hi\"\"\nsecond line\""))
    }

    @Test
    fun testInjectionPrefixOnTextFieldsOnly() {
        val csv = buildCsv(
            listOf(entry(title = "=SUM(A1)", note = "-5", amountCents = 500L)),
        )
        val dataLine = csv.split("\r\n")[1]
        assertTrue(dataLine.contains("'=SUM(A1)"))
        assertTrue(dataLine.contains("'-5"))
        assertTrue(dataLine.contains(",5.00,"))
        assertFalse(dataLine.contains("'5.00"))
    }

    @Test
    fun testAmountFormatting() {
        val csv = buildCsv(listOf(entry(amountCents = 1234L)))
        assertTrue(csv.split("\r\n")[1].contains(",12.34,"))
    }

    @Test
    fun testTombstonesExcluded() {
        val csv = buildCsv(
            listOf(
                entry(uuid = "live"),
                entry(uuid = "dead", deletedAt = 999L),
            ),
        )
        assertTrue(csv.contains("live"))
        assertFalse(csv.contains("dead"))
    }

    @Test
    fun testFxColumnsExported() {
        val csv = buildCsv(
            listOf(
                entry(
                    currency = "USD",
                    amountCents = 1250L,
                    fxRateToCny = 7.1234,
                    fxRateDate = "2026-09-25",
                ),
            ),
        )
        val dataLine = csv.split("\r\n")[1]
        // USD 12.50 at 7.1234 CNY -> rate "7.1234", base amount "89.04"
        assertTrue(dataLine.contains(",USD,7.1234,89.04,"))
    }

    @Test
    fun testDateFormattingInZone() {
        val millis = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val csv = buildCsv(listOf(entry(occurredAtMillis = millis)))
        assertTrue(csv.split("\r\n")[1].startsWith("2024-01-15 10:30,"))
    }
}
