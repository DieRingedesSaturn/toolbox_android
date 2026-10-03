package com.example.toolbox.ledger

data class LedgerQuickPick(
    val type: LedgerEntryType,
    val title: String,
    val amountCents: Long,
    val currency: String,
    val tagUuids: List<String>,
    val accountUuid: String?,
    val uses: Int,
)

object LedgerQuickPicks {
    const val LOOKBACK_DAYS = 90L
    const val MIN_USES = 2
    const val MAX_PICKS = 8

    fun from(entries: List<LedgerEntry>, nowMillis: Long): List<LedgerQuickPick> {
        val since = nowMillis - LOOKBACK_DAYS * LedgerCalculator.MILLIS_PER_DAY
        return entries
            .filter {
                it.deletedAtMillis == null &&
                    it.parentUuid == null &&
                    (it.type == LedgerEntryType.EXPENSE || it.type == LedgerEntryType.INCOME) &&
                    it.costTrackingMode == CostTrackingMode.NONE &&
                    it.title.isNotBlank() &&
                    it.occurredAtMillis in since..nowMillis
            }
            .groupBy { it.type to it.title.trim().lowercase() }
            .values
            .filter { it.size >= MIN_USES }
            .map { group -> group.size to group.maxBy { it.occurredAtMillis } }
            .sortedWith(
                compareByDescending<Pair<Int, LedgerEntry>> { it.first }
                    .thenByDescending { it.second.occurredAtMillis },
            )
            .take(MAX_PICKS)
            .map { (uses, latest) ->
                LedgerQuickPick(
                    type = latest.type,
                    title = latest.title.trim(),
                    amountCents = latest.amountCents,
                    currency = latest.currency,
                    tagUuids = latest.tagUuids,
                    accountUuid = latest.accountUuid,
                    uses = uses,
                )
            }
    }
}
