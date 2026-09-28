package com.example.toolbox.fx

import java.util.Currency
import java.util.Locale

/**
 * Bilingual currency lookup for the converter. Each ISO code is matched
 * against the code itself plus its English and Chinese display names, so
 * "usd", "dollar", and "美元" all find USD regardless of UI language.
 */
object CurrencySearch {

    // NUL separates the fields so a query can never match across a boundary.
    fun key(code: String): String {
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
            ?: return code.lowercase(Locale.US)
        return buildString {
            append(code.lowercase(Locale.US))
            append('\u0000')
            append(currency.getDisplayName(Locale.US).lowercase(Locale.US))
            append('\u0000')
            append(currency.getDisplayName(Locale.SIMPLIFIED_CHINESE))
        }
    }

    fun keys(codes: Collection<String>): Map<String, String> =
        codes.associateWith(::key)

    fun filter(codes: List<String>, query: String, keys: Map<String, String>): List<String> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return codes
        return codes.filter { keys[it]?.contains(q) == true }
    }
}
