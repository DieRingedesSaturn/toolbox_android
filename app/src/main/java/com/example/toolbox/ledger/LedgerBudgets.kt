package com.example.toolbox.ledger

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.YearMonth

data class LedgerBudgets(
    val monthlyCents: Long? = null,
    val tagCents: Map<String, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = monthlyCents == null && tagCents.isEmpty()

    companion object {
        fun parseTagBudgets(raw: String?): Map<String, Long> = raw.orEmpty()
            .split(';')
            .mapNotNull { pair ->
                val uuid = pair.substringBefore('=', "").trim()
                val cents = pair.substringAfter('=', "").trim().toLongOrNull()
                if (uuid.isEmpty() || cents == null || cents <= 0L) null else uuid to cents
            }
            .toMap()

        fun formatTagBudgets(tagCents: Map<String, Long>): String = tagCents.entries
            .filter { it.value > 0L }
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value}" }
    }
}

data class BudgetProgress(
    val budgetCents: Long,
    val spentCents: Long,
) {
    val remainingCents: Long
        get() = budgetCents - spentCents
    val fraction: Float
        get() = if (budgetCents <= 0L) 0f else spentCents.toFloat() / budgetCents
    val isOver: Boolean
        get() = spentCents > budgetCents

    fun dailyAllowanceCents(today: LocalDate, month: YearMonth): Long? {
        if (YearMonth.from(today) != month || remainingCents <= 0L) return null
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        return remainingCents / daysLeft
    }
}

class LedgerBudgetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LedgerBudgets = LedgerBudgets(
        monthlyCents = prefs.getLong(KEY_MONTHLY, 0L).takeIf { it > 0L },
        tagCents = LedgerBudgets.parseTagBudgets(prefs.getString(KEY_TAGS, null)),
    )

    fun save(budgets: LedgerBudgets) {
        prefs.edit {
            val monthly = budgets.monthlyCents?.takeIf { it > 0L }
            if (monthly != null) putLong(KEY_MONTHLY, monthly) else remove(KEY_MONTHLY)
            putString(KEY_TAGS, LedgerBudgets.formatTagBudgets(budgets.tagCents))
        }
    }

    companion object {
        private const val PREFS_NAME = "ledger_budgets"
        private const val KEY_MONTHLY = "monthly_cents"
        private const val KEY_TAGS = "tag_cents"
    }
}
