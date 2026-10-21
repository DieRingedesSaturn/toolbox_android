package com.example.toolbox.ui

/** Cards the user can reorder on the Ledger Overview tab. */
enum class LedgerOverviewCard {
    MONTH,
    RECENT,
    TAGS,
    TREND,
    COST,
}

internal object LedgerOverviewOrder {
    /** Recent entries sit above the analytics cards until the user changes it. */
    val DEFAULT: List<LedgerOverviewCard> = listOf(
        LedgerOverviewCard.MONTH,
        LedgerOverviewCard.RECENT,
        LedgerOverviewCard.TAGS,
        LedgerOverviewCard.TREND,
        LedgerOverviewCard.COST,
    )

    /** Keeps known cards, drops duplicates, and appends cards added in later versions. */
    fun parse(raw: String?): List<LedgerOverviewCard> {
        val stored = raw.orEmpty()
            .split(',')
            .mapNotNull { name ->
                LedgerOverviewCard.values().firstOrNull { it.name == name.trim() }
            }
            .distinct()
        return stored + DEFAULT.filterNot { it in stored }
    }

    fun format(order: List<LedgerOverviewCard>): String =
        order.joinToString(",") { it.name }
}
