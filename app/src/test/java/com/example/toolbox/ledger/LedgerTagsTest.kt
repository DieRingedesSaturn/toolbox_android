package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTagsTest {

    private fun entry(
        uuid: String,
        tagUuids: List<String> = emptyList(),
        updatedAt: Long = 0L,
    ) = LedgerEntry(
        uuid = uuid,
        title = uuid,
        amountCents = 100L,
        occurredAtMillis = 0,
        updatedAtMillis = updatedAt,
        tagUuids = tagUuids,
    )

    private fun tag(
        uuid: String,
        name: String = uuid,
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = LedgerTag(
        uuid = uuid,
        name = name,
        colorArgb = 0xFF112233.toInt(),
        sortOrder = 0,
        createdAtMillis = 0,
        updatedAtMillis = updatedAt,
        deletedAtMillis = deletedAt,
    )

    @Test
    fun testCategoryTagUuidsDeterministic() {
        assertEquals(
            LedgerTags.categoryTagUuid(LedgerCategory.FOOD),
            LedgerTags.categoryTagUuid(LedgerCategory.FOOD),
        )
        assertFalse(
            LedgerTags.categoryTagUuid(LedgerCategory.FOOD) ==
                LedgerTags.categoryTagUuid(LedgerCategory.SHOPPING),
        )
    }

    @Test
    fun testDefaultTagsCoverAllCategoriesLocalized() {
        val zh = ToolboxStringsForTest.names(AppLanguageForTest.CHINESE)
        val en = ToolboxStringsForTest.names(AppLanguageForTest.ENGLISH)
        val zhTags = LedgerTags.defaultTags(zh)
        val enTags = LedgerTags.defaultTags(en)
        assertEquals(LedgerCategory.entries.size, zhTags.size)
        assertEquals(zhTags.map { it.uuid }, enTags.map { it.uuid })
        assertEquals("餐饮", zhTags.first().name)
        assertEquals("Food", enTags.first().name)
        assertTrue(zhTags.all { it.colorArgb != 0 })
    }

    @Test
    fun testDefaultTagsStampedEpochZero() {
        val names = ToolboxStringsForTest.names(AppLanguageForTest.CHINESE)
        assertTrue(
            LedgerTags.defaultTags(names).all {
                it.createdAtMillis == 0L && it.updatedAtMillis == 0L &&
                    it.syncStatus == LedgerSyncStatus.PENDING_PUSH
            },
        )
    }

    @Test
    fun testSeededDefaultsNeverBeatRemoteEdits() {
        val names = ToolboxStringsForTest.names(AppLanguageForTest.ENGLISH)
        val seeded = LedgerTags.defaultTags(names)
        // Remote renamed FOOD and tombstoned OTHER; seeded defaults (updatedAt 0)
        // must always lose to real edits.
        val renamed = seeded.first { it.name == "Food" }
            .copy(name = "Eats", updatedAtMillis = 5_000L)
        val tombstoned = seeded.first { it.name == "Other" }
            .copy(updatedAtMillis = 6_000L, deletedAtMillis = 6_000L)
        val merged = LedgerSyncMerge.mergeTags(seeded, listOf(renamed, tombstoned))
        assertEquals("Eats", merged.single { it.uuid == renamed.uuid }.name)
        assertEquals(
            6_000L,
            merged.single { it.uuid == tombstoned.uuid }.deletedAtMillis,
        )
    }

    @Test
    fun testBackfillMapsLegacyCategory() {
        val digital = entry("e", tagUuids = emptyList())
            .copy(category = "DIGITAL") // legacy category name
        assertEquals(
            listOf(LedgerTags.categoryTagUuid(LedgerCategory.OTHER)),
            LedgerTags.backfillTagUuids(digital),
        )
        val food = entry("f").copy(category = LedgerCategory.FOOD.name)
        assertEquals(
            listOf(LedgerTags.categoryTagUuid(LedgerCategory.FOOD)),
            LedgerTags.backfillTagUuids(food),
        )
    }

    @Test
    fun testMatchesAnyAllEmpty() {
        val e = entry("e", tagUuids = listOf("a", "b"))
        assertTrue(LedgerTags.matches(e, emptySet(), TagFilterMode.ANY))
        assertTrue(LedgerTags.matches(e, emptySet(), TagFilterMode.ALL))
        assertTrue(LedgerTags.matches(e, setOf("a", "x"), TagFilterMode.ANY))
        assertFalse(LedgerTags.matches(e, setOf("x"), TagFilterMode.ANY))
        assertTrue(LedgerTags.matches(e, setOf("a", "b"), TagFilterMode.ALL))
        assertFalse(LedgerTags.matches(e, setOf("a", "x"), TagFilterMode.ALL))
    }

    @Test
    fun testMergeTagsLwwAndTombstone() {
        val localTag = tag("t1", name = "old", updatedAt = 10L)
        val remoteTag = tag("t1", name = "new", updatedAt = 20L)
        val localOnly = tag("t2", updatedAt = 5L)
        val remoteTombstone = tag("t3", updatedAt = 30L, deletedAt = 30L)
        val merged = LedgerSyncMerge.mergeTags(
            listOf(localTag, localOnly),
            listOf(remoteTag, remoteTombstone),
        )
        assertEquals("new", merged.single { it.uuid == "t1" }.name)
        assertEquals(30L, merged.single { it.uuid == "t3" }.deletedAtMillis)
        assertTrue(merged.all { it.syncStatus == LedgerSyncStatus.SYNCED })
        assertEquals(3, merged.size)
    }

    @Test
    fun testPlanReplaceTombstonesLocalOnlyTags() {
        val local = listOf(entry("e1"))
        val incoming = listOf(entry("e1"))
        val localTags = listOf(tag("keep", updatedAt = 1L), tag("gone", updatedAt = 2L))
        val plan = LedgerBackup.planReplace(
            local,
            incoming,
            localTags = localTags,
            incomingTags = listOf(tag("keep", updatedAt = 9L)),
            nowMillis = 100L,
        )
        assertEquals(1, plan.deletedTagCount)
        val writes = plan.tagWrites.associateBy { it.uuid }
        assertEquals(100L, writes.getValue("keep").updatedAtMillis)
        assertEquals(100L, writes.getValue("gone").deletedAtMillis)
        assertEquals(
            LedgerSyncStatus.PENDING_PUSH,
            writes.getValue("gone").syncStatus,
        )
    }

    @Test
    fun testSerializerTagUuidsAndTagsRoundTrip() {
        val e = entry(
            "e1",
            tagUuids = listOf("tag-a", "tag-b"),
        ).copy(
            parentUuid = "p",
            linkType = LINK_TYPE_RENEWAL,
            renewalIndex = 3,
            fxRateSource = "ecb",
        )
        val parent = entry("p").copy(disposalType = DISPOSAL_SOLD)
        val tag = tag("tag-a", name = "餐饮")
        val payload = LedgerSyncPayload(entries = listOf(e, parent), tags = listOf(tag))
        val restored = LedgerSyncSerializer.fromJsonString(
            LedgerSyncSerializer.toJsonString(payload),
        )
        val re = restored.entries.single { it.uuid == "e1" }
        assertEquals(listOf("tag-a", "tag-b"), re.tagUuids)
        assertEquals("p", re.parentUuid)
        assertEquals(LINK_TYPE_RENEWAL, re.linkType)
        assertEquals(3, re.renewalIndex)
        assertEquals("ecb", re.fxRateSource)
        assertEquals(DISPOSAL_SOLD, restored.entries.single { it.uuid == "p" }.disposalType)
        assertEquals("餐饮", restored.tags.single().name)
        assertEquals(LEDGER_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun testV2EntryWithoutTagUuidsBackfillsFromCategory() {
        val obj = LedgerSyncSerializer.entryToJson(
            entry("e").copy(category = LedgerCategory.FOOD.name),
        )
        obj.remove("tagUuids")
        val restored = LedgerSyncSerializer.jsonToEntry(obj)
        assertEquals(
            listOf(LedgerTags.categoryTagUuid(LedgerCategory.FOOD)),
            restored.tagUuids,
        )
        assertNull(restored.parentUuid)
        assertNull(restored.linkType)
        assertNull(restored.disposalType)
        assertNull(restored.fxRateSource)
    }

    @Test
    fun testPayloadWithoutTagsDefaultsEmpty() {
        val payload = LedgerSyncSerializer.fromJsonString(
            """{"schemaVersion":2,"entries":[]}""",
        )
        assertTrue(payload.tags.isEmpty())
    }
}


class LedgerTagOpsTest {

    private fun defaultTag(category: LedgerCategory, name: String = category.name) =
        LedgerTags.defaultTags(mapOf(category to name))
            .first { it.uuid == LedgerTags.categoryTagUuid(category) }

    private fun entry(
        uuid: String,
        tagUuids: List<String>,
        deletedAt: Long? = null,
        type: LedgerEntryType = LedgerEntryType.EXPENSE,
        baseCents: Long = 0,
        occurredAt: Long = 0L,
    ) = LedgerEntry(
        uuid = uuid,
        title = uuid,
        amountCents = baseCents,
        type = type,
        tagUuids = tagUuids,
        deletedAtMillis = deletedAt,
        occurredAtMillis = occurredAt,
    )

    @Test
    fun testUnusedDefaultTagUuids() {
        val food = defaultTag(LedgerCategory.FOOD)
        val used = defaultTag(LedgerCategory.TRANSPORT)
        val edited = defaultTag(LedgerCategory.OTHER)
            .copy(updatedAtMillis = 1_000L, name = "Renamed")
        val custom = LedgerTag(
            uuid = "custom-1", name = "Mine", colorArgb = 1, sortOrder = 99,
            createdAtMillis = 5L, updatedAtMillis = 5L,
        )
        val tombstone = defaultTag(LedgerCategory.HEALTH)
            .copy(deletedAtMillis = 9L)
        val live = entry("e1", listOf(used.uuid))
        val deleted = entry("e2", listOf(food.uuid), deletedAt = 3L)

        val unused = LedgerTags.unusedDefaultTagUuids(
            listOf(food, used, edited, custom, tombstone),
            listOf(live, deleted),
        )
        // food: unused + untouched → retired; used: referenced → kept;
        // edited (stamp > 0) → kept; custom → never; tombstone → untouched.
        assertEquals(setOf(food.uuid), unused)
    }

    @Test
    fun testReplaceAddRemoveTag() {
        // Dedupe: A→B where B already exists leaves just B.
        assertEquals(
            listOf("b"),
            LedgerTags.replaceTag(listOf("a", "b"), "a", "b"),
        )
        assertEquals(
            listOf("a", "b"),
            LedgerTags.replaceTag(listOf("a", "x"), "x", "b"),
        )
        assertEquals(listOf("a"), LedgerTags.addTag(listOf("a"), "a"))
        assertEquals(listOf("a", "b"), LedgerTags.addTag(listOf("a"), "b"))
        assertEquals(listOf("b"), LedgerTags.removeTag(listOf("a", "b"), "a"))
        assertEquals(listOf("a"), LedgerTags.removeTag(listOf("a"), "z"))
    }

    @Test
    fun testTagStatsExcludesNonLiveAndTransfers() {
        val zone = java.time.ZoneId.of("UTC")
        val month = java.time.YearMonth.of(2024, 3)
        val at = month.atDay(10).atStartOfDay(zone).toInstant().toEpochMilli()
        val tag = "t1"
        val expense = entry("e1", listOf(tag), baseCents = 1_000)
            .copy(occurredAtMillis = at)
        val income = entry("e2", listOf(tag), baseCents = 2_000)
            .copy(occurredAtMillis = at, type = LedgerEntryType.INCOME)
        val transfer = entry("e3", listOf(tag), baseCents = 9_000)
            .copy(occurredAtMillis = at, type = LedgerEntryType.TRANSFER)
        val adjust = entry("e4", listOf(tag), baseCents = 8_000)
            .copy(occurredAtMillis = at, type = LedgerEntryType.ADJUSTMENT)
        val deletedRow = entry("e5", listOf(tag), deletedAt = 1L)
            .copy(occurredAtMillis = at)
        val otherMonth = entry("e6", listOf(tag), baseCents = 500)
            .copy(occurredAtMillis = month.atDay(1).minusMonths(1)
                .atStartOfDay(zone).toInstant().toEpochMilli())

        val stats = LedgerTags.stats(
            listOf(expense, income, transfer, adjust, deletedRow, otherMonth),
            tag, month, zone,
        )
        // All live rows carrying the tag count; transfers/adjustments
        // contribute nothing to the expense/income sums.
        assertEquals(5, stats.entryCount)
        assertEquals(1_000L, stats.monthExpenseCents)
        assertEquals(2_000L, stats.monthIncomeCents)
        assertEquals(1_500L, stats.totalExpenseCents)
        assertEquals(2_000L, stats.totalIncomeCents)
    }
}

/** Minimal category-name maps for both languages without a Context. */
private object ToolboxStringsForTest {
    fun names(language: AppLanguageForTest): Map<LedgerCategory, String> =
        when (language) {
            AppLanguageForTest.CHINESE -> mapOf(
                LedgerCategory.FOOD to "餐饮",
                LedgerCategory.TRANSPORT to "交通",
                LedgerCategory.SHOPPING to "购物",
                LedgerCategory.HOUSING to "居住",
                LedgerCategory.ELECTRONICS to "数码",
                LedgerCategory.ENTERTAINMENT to "娱乐",
                LedgerCategory.HEALTH to "医疗",
                LedgerCategory.EDUCATION to "教育",
                LedgerCategory.SALARY to "工资",
                LedgerCategory.BONUS to "奖金",
                LedgerCategory.INVESTMENT to "理财",
                LedgerCategory.OTHER to "其他",
            )
            AppLanguageForTest.ENGLISH -> mapOf(
                LedgerCategory.FOOD to "Food",
                LedgerCategory.TRANSPORT to "Transport",
                LedgerCategory.SHOPPING to "Shopping",
                LedgerCategory.HOUSING to "Housing",
                LedgerCategory.ELECTRONICS to "Electronics",
                LedgerCategory.ENTERTAINMENT to "Entertainment",
                LedgerCategory.HEALTH to "Health",
                LedgerCategory.EDUCATION to "Education",
                LedgerCategory.SALARY to "Salary",
                LedgerCategory.BONUS to "Bonus",
                LedgerCategory.INVESTMENT to "Investment",
                LedgerCategory.OTHER to "Other",
            )
        }
}

private enum class AppLanguageForTest { CHINESE, ENGLISH }
