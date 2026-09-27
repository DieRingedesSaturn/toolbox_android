package com.example.toolbox.ledger

/**
 * Built-in bookkeeping categories shown in the editor, transaction rows,
 * and the monthly expense breakdown. Persisted by enum name; unknown or
 * legacy values (e.g. the old "DIGITAL" default) fall back to [OTHER].
 */
enum class LedgerCategory(
    val emoji: String,
    val colorRgb: Long,
    val isIncome: Boolean,
) {
    FOOD("🍜", 0xFFFF7043, false),
    TRANSPORT("🚇", 0xFF42A5F5, false),
    SHOPPING("🛍️", 0xFFEC407A, false),
    HOUSING("🏠", 0xFF8D6E63, false),
    ELECTRONICS("📱", 0xFF5C6BC0, false),
    ENTERTAINMENT("🎮", 0xFFAB47BC, false),
    HEALTH("💊", 0xFF66BB6A, false),
    EDUCATION("📚", 0xFFFFA726, false),
    SALARY("💼", 0xFF26A69A, true),
    BONUS("🎁", 0xFFEF5350, true),
    INVESTMENT("📈", 0xFF7E57C2, true),
    OTHER("📦", 0xFF78909C, false),
    ;

    companion object {
        fun fromStorage(value: String?): LedgerCategory =
            entries.find { it.name == value } ?: OTHER

        fun forType(type: LedgerEntryType): List<LedgerCategory> =
            if (type == LedgerEntryType.INCOME) {
                entries.filter { it.isIncome || it == OTHER }
            } else {
                entries.filter { !it.isIncome }
            }
    }
}

val LedgerEntry.categoryEnum: LedgerCategory
    get() = LedgerCategory.fromStorage(category)
