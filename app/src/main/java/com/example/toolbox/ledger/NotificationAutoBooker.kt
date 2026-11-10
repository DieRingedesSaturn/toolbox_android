package com.example.toolbox.ledger

import android.content.Context
import com.example.toolbox.ui.AppPreferences

/**
 * Books a confident payment notification straight into the ledger when the
 * user enabled auto-book for that source. Only explicit expense/income
 * payments qualify: transfers stay suggestions (they need two accounts),
 * low-confidence matches and possible duplicates are handed back to the
 * user, and every booked entry can be undone from the notification list.
 */
object NotificationAutoBooker {
    enum class Outcome {
        BOOKED,
        ALREADY_BOOKED,
        NOT_A_PAYMENT,
        LOW_CONFIDENCE,
        TRANSFER,
        DISABLED,
        DUPLICATE,
    }

    /** Books [candidate] when its source opted in; performs the SQLite write itself. */
    fun book(context: Context, candidate: NotificationLedgerCandidate): Outcome {
        val payment = candidate.payment ?: return Outcome.NOT_A_PAYMENT
        if (candidate.autoBookedEntryUuid != null) return Outcome.ALREADY_BOOKED
        if (payment.direction == NotificationPaymentDirection.TRANSFER_OUT ||
            payment.direction == NotificationPaymentDirection.TRANSFER_IN
        ) return Outcome.TRANSFER
        if (!payment.confident) return Outcome.LOW_CONFIDENCE
        val notificationStore = NotificationLedgerStore(context)
        val defaults = notificationStore.defaultsFor(candidate.packageName)
        if (!defaults.autoBook) return Outcome.DISABLED

        return LedgerStore(context).use { ledgerStore ->
            val accounts = ledgerStore.queryAccounts(includeDeleted = true)
            val tags = ledgerStore.queryTags(includeDeleted = true)
            val resolved = defaults.resolve(accounts, tags)
            val accountUuid = resolved.accountFor(accounts, AppPreferences(context).ledgerLastAccount())
            val title = payment.label?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_TITLE_CHARS)
                ?: PaymentNotificationParser.channelLabel(candidate.packageName)
                ?: candidate.title.trim().take(MAX_TITLE_CHARS)
            val entry = LedgerEntry(
                title = title,
                amountCents = payment.amountCents,
                type = when (payment.direction) {
                    NotificationPaymentDirection.EXPENSE -> LedgerEntryType.EXPENSE
                    else -> LedgerEntryType.INCOME
                },
                occurredAtMillis = candidate.occurredAtMillis,
                accountUuid = accountUuid,
                tagUuids = resolved.tagUuids,
            )
            if (LedgerDuplicateMatcher.find(entry, ledgerStore.queryVisibleEntries()).isNotEmpty()) {
                return Outcome.DUPLICATE
            }
            ledgerStore.upsert(entry)
            AppPreferences(context).saveLedgerLastAccount(accountUuid)
            notificationStore.markAutoBooked(candidate.id, entry.uuid)
            Outcome.BOOKED
        }
    }

    /** Soft-deletes the booked entry and clears the candidate's marker. */
    fun undo(context: Context, candidateId: String, entryUuid: String): Boolean {
        val removed = runCatching {
            LedgerStore(context).use { it.softDelete(entryUuid) }
        }.isSuccess
        if (!removed) return false
        NotificationLedgerStore(context).clearAutoBook(candidateId)
        return true
    }

    private const val MAX_TITLE_CHARS = 60
}
