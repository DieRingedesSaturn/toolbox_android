package com.example.toolbox.ledger

import java.util.UUID
import kotlin.math.roundToLong

/**
 * A balance-bearing account an entry can debit/credit. Entries keep
 * `accountUuid` (and `toAccountUuid` for transfers); a deleted account's
 * uuid stays referenced by entries — display ignores unknown uuids.
 */
data class LedgerAccount(
    val uuid: String,
    val name: String,
    val currency: String,
    /** Starting balance in the account currency; may be negative. */
    val openingBalanceCents: Long,
    /** Balances count from this instant — earlier entries are ignored. */
    val openingAtMillis: Long,
    val colorArgb: Int,
    val emoji: String = "",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deletedAtMillis: Long? = null,
    val syncStatus: LedgerSyncStatus = LedgerSyncStatus.PENDING_PUSH,
)

object LedgerAccounts {

    /** The deterministic default account, shared across devices. */
    val DEFAULT_ACCOUNT_UUID: String = UUID.nameUUIDFromBytes(
        "toolbox-account:default".toByteArray(Charsets.UTF_8),
    ).toString()

    /**
     * The seeded default account. Epoch-0 timestamps so a later-seeding
     * device never overwrites another device's rename/delete via LWW.
     */
    fun defaultAccount(name: String): LedgerAccount = LedgerAccount(
        uuid = DEFAULT_ACCOUNT_UUID,
        name = name,
        currency = "CNY",
        openingBalanceCents = 0L,
        openingAtMillis = 0L,
        colorArgb = 0xFF78909C.toInt(),
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    /**
     * Amount this entry posts to [account], in the account's currency.
     * Legacy entries (v2 rows / old backups) carry no `accountAmountCents`,
     * so fall back to the entry's own amount: same currency → amountCents;
     * CNY account → the recorded CNY base; otherwise amountCents (matches
     * the legacy behavior — new foreign-account entries always store the
     * account amount from the editor).
     */
    private fun postedCents(entry: LedgerEntry, account: LedgerAccount): Long {
        if (entry.accountUuid == account.uuid) {
            if (entry.accountAmountCents != null) return entry.accountAmountCents
            if (entry.currency == account.currency) return entry.amountCents
            if (account.currency == "CNY") {
                return (entry.amountCents * entry.fxRateToCny).roundToLong()
            }
        }
        return entry.amountCents
    }

    /**
     * Balance in the account's currency (cents). Counts live entries at or
     * after `openingAtMillis`; skips other types silently.
     */
    fun balance(account: LedgerAccount, entries: List<LedgerEntry>): Long {
        var cents = account.openingBalanceCents
        for (e in entries) {
            if (e.deletedAtMillis != null ||
                e.occurredAtMillis < account.openingAtMillis
            ) {
                continue
            }
            when {
                e.type == LedgerEntryType.INCOME &&
                    e.accountUuid == account.uuid ->
                    cents += postedCents(e, account)
                e.type == LedgerEntryType.EXPENSE &&
                    e.accountUuid == account.uuid ->
                    cents -= postedCents(e, account)
                e.type == LedgerEntryType.ADJUSTMENT &&
                    e.accountUuid == account.uuid ->
                    cents += postedCents(e, account)
                e.type == LedgerEntryType.TRANSFER &&
                    e.accountUuid == account.uuid ->
                    cents -= e.amountCents
                e.type == LedgerEntryType.TRANSFER &&
                    e.toAccountUuid == account.uuid ->
                    cents += e.toAmountCents ?: e.amountCents
            }
        }
        return cents
    }

    /**
     * Total assets across accounts, in CNY cents. A non-CNY account is
     * converted via [rates]; without rates the account is excluded.
     * Returns (totalCnyCents, allConverted) — allConverted false when some
     * foreign account couldn't be converted.
     */
    fun totalAssetsCnyCents(
        accounts: List<LedgerAccount>,
        entries: List<LedgerEntry>,
        rates: com.example.toolbox.fx.FxRates?,
    ): Pair<Long, Boolean> {
        var total = 0L
        var allConverted = true
        for (account in accounts) {
            if (account.deletedAtMillis != null) continue
            val balance = balance(account, entries)
            if (account.currency == "CNY") {
                total += balance
            } else {
                val rate = rates?.cnyPerUnit(account.currency)
                if (rate != null && rate > 0) {
                    total += (balance * rate).toLong()
                } else {
                    allConverted = false
                }
            }
        }
        return total to allConverted
    }

    /**
     * The account-currency cents for an entry whose own currency differs:
     * `amountCents × entryCnyRate ÷ accountCnyRate` (both CNY-per-unit).
     */
    fun accountAmountForEntry(
        amountCents: Long,
        entryCnyRate: Double,
        accountCnyRate: Double,
    ): Long = (amountCents * entryCnyRate / accountCnyRate).toLong()
}
