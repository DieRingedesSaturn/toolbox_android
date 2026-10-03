package com.example.toolbox.fx

object FxPinnedCurrencies {
    private val CODE = Regex("[A-Z]{3}")

    fun parse(raw: String?): List<String> = raw.orEmpty()
        .split(',')
        .map { it.trim().uppercase() }
        .filter { CODE.matches(it) && it !in LEDGER_CURRENCIES }
        .distinct()

    fun format(codes: List<String>): String = codes.joinToString(",")

    fun toggle(pinned: List<String>, code: String): List<String> = when {
        code in LEDGER_CURRENCIES -> pinned
        code in pinned -> pinned - code
        else -> pinned + code
    }

    fun featured(pinned: List<String>): List<String> = (LEDGER_CURRENCIES + pinned).distinct()
}
