package com.example.toolbox.ledger

/** A suggestion only: two legitimate payments can have the same amount and time. */
object LedgerDuplicateMatcher {
    const val WINDOW_MILLIS = 5L * 60 * 1_000

    fun find(entry: LedgerEntry, existing: List<LedgerEntry>): List<LedgerEntry> {
        if (entry.deletedAtMillis != null || entry.amountCents <= 0 ||
            entry.type !in listOf(LedgerEntryType.EXPENSE, LedgerEntryType.INCOME)
        ) return emptyList()
        val lower = if (entry.occurredAtMillis < Long.MIN_VALUE + WINDOW_MILLIS) {
            Long.MIN_VALUE
        } else entry.occurredAtMillis - WINDOW_MILLIS
        val upper = if (entry.occurredAtMillis > Long.MAX_VALUE - WINDOW_MILLIS) {
            Long.MAX_VALUE
        } else entry.occurredAtMillis + WINDOW_MILLIS
        return existing.filter {
            it.uuid != entry.uuid && it.deletedAtMillis == null &&
                it.type == entry.type && it.amountCents == entry.amountCents &&
                it.currency == entry.currency &&
                (it.accountUuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID) ==
                (entry.accountUuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID) &&
                it.occurredAtMillis in lower..upper
        }.sortedByDescending { it.occurredAtMillis }
    }
}
