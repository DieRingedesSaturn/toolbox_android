package com.example.toolbox.ledger

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV export for spreadsheets (Excel/WPS). UTF-8 BOM + CRLF line endings +
 * RFC 4180 quoting. The amount column stays a positive plain decimal; the
 * type column carries the sign meaning.
 */
object LedgerCsv {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val BOM = "\uFEFF"
    private const val CRLF = "\r\n"
    private val INJECTION_PREFIX_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun build(
        entries: List<LedgerEntry>,
        headers: List<String>,
        typeLabel: (LedgerEntryType) -> String,
        tagsLabel: (LedgerEntry) -> String,
        costModeLabel: (CostTrackingMode) -> String,
        zone: ZoneId,
        accountLabel: (LedgerEntry) -> String = { "" },
        toAccountLabel: (LedgerEntry) -> String = { "" },
    ): String {
        val out = StringBuilder(BOM)
        out.appendRow(headers.map { field(it) })
        for (entry in entries) {
            if (entry.deletedAtMillis != null) continue
            out.appendRow(
                listOf(
                    field(
                        Instant.ofEpochMilli(entry.occurredAtMillis)
                            .atZone(zone)
                            .format(DATE_FORMATTER),
                    ),
                    field(typeLabel(entry.type)),
                    field(tagsLabel(entry), injectGuard = true),
                    field(entry.title, injectGuard = true),
                    field(String.format(Locale.US, "%.2f", entry.amountCents / 100.0)),
                    field(entry.currency),
                    field(
                        String.format(
                            Locale.US,
                            "%.6f",
                            entry.fxRateToCny,
                        ).trimEnd('0').trimEnd('.'),
                    ),
                    field(
                        String.format(Locale.US, "%.2f", entry.baseAmountCents / 100.0),
                    ),
                    field(entry.note, injectGuard = true),
                    field(costModeLabel(entry.costTrackingMode)),
                    field(accountLabel(entry), injectGuard = true),
                    field(toAccountLabel(entry), injectGuard = true),
                    field(entry.uuid),
                ),
            )
        }
        return out.toString()
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        append(fields.joinToString(","))
        append(CRLF)
    }

    private fun field(value: String, injectGuard: Boolean = false): String {
        var text = value
        if (injectGuard && text.isNotEmpty() && text.first() in INJECTION_PREFIX_CHARS) {
            text = "'$text"
        }
        return if (text.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }
}
