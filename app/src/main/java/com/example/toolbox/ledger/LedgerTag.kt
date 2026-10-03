package com.example.toolbox.ledger

import java.util.UUID

enum class TagFilterMode {
    ANY,
    ALL,
}

data class LedgerTag(
    val uuid: String,
    val name: String,
    val colorArgb: Int,
    val emoji: String = "",
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val syncStatus: LedgerSyncStatus = LedgerSyncStatus.PENDING_PUSH,
)

/**
 * Tags replace the fixed category list. The 12 built-in categories become the
 * initial tag set with deterministic uuids, so two devices migrating
 * independently converge to identical rows through the LWW merge.
 */
object LedgerTags {

    fun categoryTagUuid(category: LedgerCategory): String =
        UUID.nameUUIDFromBytes(
            "toolbox-tag:${category.name}".toByteArray(Charsets.UTF_8),
        ).toString()

    /**
     * The initial tag set, localized for the current app language. Pure —
     * [names] maps each category to its display name.
     */
    fun defaultTags(
        names: Map<LedgerCategory, String>,
    ): List<LedgerTag> = LedgerCategory.entries.mapIndexed { index, category ->
        LedgerTag(
            uuid = categoryTagUuid(category),
            name = names[category] ?: category.name,
            colorArgb = (0xFF000000L or category.colorRgb).toInt(),
            emoji = category.emoji,
            sortOrder = index,
            // Epoch-0 timestamps: a device that seeds later must never
            // overwrite an earlier rename/delete on another device via LWW.
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
        )
    }

    /**
     * Tag uuids for a stored entry that predates tags: exactly one tag derived
     * from its (legacy) category, e.g. a stored "DIGITAL" maps to OTHER.
     */
    fun backfillTagUuids(entry: LedgerEntry): List<String> =
        listOf(categoryTagUuid(entry.categoryEnum))

    fun matches(
        entry: LedgerEntry,
        selected: Set<String>,
        mode: TagFilterMode,
    ): Boolean {
        if (selected.isEmpty()) return true
        return when (mode) {
            TagFilterMode.ANY -> entry.tagUuids.any { it in selected }
            TagFilterMode.ALL -> selected.all { it in entry.tagUuids }
        }
    }

    /**
     * Unused untouched default tags: still carrying the epoch-0 stamp (never
     * edited or merged), live, and not referenced by any live entry. These
     * get tombstoned by the DB v4 upgrade. A custom tag (updatedAt > 0 at
     * creation) is never matched; tombstones are left alone.
     */
    fun unusedDefaultTagUuids(
        tags: List<LedgerTag>,
        liveEntries: List<LedgerEntry>,
    ): Set<String> = unusedDefaultTagUuids(
        tags,
        liveEntries
            .filter { it.deletedAtMillis == null }
            .flatMapTo(HashSet()) { it.tagUuids },
    )

    fun unusedDefaultTagUuids(
        tags: List<LedgerTag>,
        used: Set<String>,
    ): Set<String> {
        return tags.filter {
            it.updatedAtMillis == 0L &&
                it.deletedAtMillis == null &&
                it.uuid !in used
        }.mapTo(HashSet()) { it.uuid }
    }

    /** Replace [from] with [to] in a tag list (dedupes, keeps order). */
    fun replaceTag(tagUuids: List<String>, from: String, to: String): List<String> =
        buildList {
            for (uuid in tagUuids) {
                val mapped = if (uuid == from) to else uuid
                if (!contains(mapped)) add(mapped)
            }
        }

    /** Append [tagUuid] unless already present. */
    fun addTag(tagUuids: List<String>, tagUuid: String): List<String> =
        if (tagUuid in tagUuids) tagUuids else tagUuids + tagUuid

    /** Drop [tagUuid] if present. */
    fun removeTag(tagUuids: List<String>, tagUuid: String): List<String> =
        tagUuids.filter { it != tagUuid }

    /** Per-tag sums for the detail view (live entries, EXPENSE/INCOME only). */
    data class TagStats(
        val entryCount: Int,
        val monthExpenseCents: Long,
        val monthIncomeCents: Long,
        val totalExpenseCents: Long,
        val totalIncomeCents: Long,
    )

    fun stats(
        entries: List<LedgerEntry>,
        tagUuid: String,
        month: java.time.YearMonth,
        zone: java.time.ZoneId,
    ): TagStats {
        var count = 0
        var monthExpense = 0L
        var monthIncome = 0L
        var totalExpense = 0L
        var totalIncome = 0L
        for (entry in entries) {
            if (entry.deletedAtMillis != null || tagUuid !in entry.tagUuids) continue
            count++
            val inMonth = java.time.YearMonth.from(
                java.time.Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone),
            ) == month
            when (entry.type) {
                LedgerEntryType.EXPENSE -> {
                    totalExpense += entry.baseAmountCents
                    if (inMonth) monthExpense += entry.baseAmountCents
                }
                LedgerEntryType.INCOME -> {
                    totalIncome += entry.baseAmountCents
                    if (inMonth) monthIncome += entry.baseAmountCents
                }
                // Transfers and adjustments never count as spending/income.
                LedgerEntryType.TRANSFER,
                LedgerEntryType.ADJUSTMENT,
                -> Unit
            }
        }
        return TagStats(
            entryCount = count,
            monthExpenseCents = monthExpense,
            monthIncomeCents = monthIncome,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
        )
    }
}
