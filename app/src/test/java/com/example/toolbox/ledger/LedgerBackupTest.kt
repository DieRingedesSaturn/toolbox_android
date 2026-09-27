package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LedgerBackupTest {

    private fun entry(
        uuid: String,
        updatedAt: Long,
        title: String = uuid,
        deletedAt: Long? = null,
    ) = LedgerEntry(
        uuid = uuid,
        title = title,
        amountCents = 100L,
        occurredAtMillis = updatedAt,
        updatedAtMillis = updatedAt,
        deletedAtMillis = deletedAt,
    )

    private fun assertParseFails(failure: BackupFailure, json: String) {
        try {
            LedgerBackup.parse(json)
            fail("expected BackupException $failure")
        } catch (e: BackupException) {
            assertEquals(failure, e.failure)
        }
    }

    @Test
    fun testParseValidPayload() {
        val json = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(entries = listOf(entry("a", 100L))),
        )
        val payload = LedgerBackup.parse(json)
        assertEquals(1, payload.entries.size)
        assertEquals("a", payload.entries.single().uuid)
    }

    @Test
    fun testParseStripsLeadingUtf8Bom() {
        val json = "\uFEFF" + LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(entries = listOf(entry("a", 100L))),
        )
        assertEquals("a", LedgerBackup.parse(json).entries.single().uuid)
    }

    @Test
    fun testParseGarbageThrowsInvalidFile() {
        assertParseFails(BackupFailure.INVALID_FILE, "not a backup")
        assertParseFails(BackupFailure.INVALID_FILE, "{unclosed")
    }

    @Test
    fun testParseCurrentSchemaAccepted() {
        val json = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(
                schemaVersion = LEDGER_SCHEMA_VERSION,
                entries = emptyList(),
            ),
        )
        assertEquals(LEDGER_SCHEMA_VERSION, LedgerBackup.parse(json).schemaVersion)
    }

    @Test
    fun testParseNewerSchemaThrows() {
        val json = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(
                schemaVersion = LEDGER_SCHEMA_VERSION + 1,
                entries = emptyList(),
            ),
        )
        assertParseFails(BackupFailure.NEWER_SCHEMA, json)
    }

    @Test
    fun testSerializeRoundTripKeepsFxFields() {
        val foreign = LedgerEntry(
            uuid = "fx",
            title = "fx",
            amountCents = 1250L,
            currency = "USD",
            fxRateToCny = 7.1234,
            fxRateDate = "2026-09-25",
            occurredAtMillis = 1_700_000_000_000L,
        )
        val json = LedgerSyncSerializer.toJsonString(
            LedgerSyncPayload(entries = listOf(foreign)),
        )
        val restored = LedgerBackup.parse(json).entries.single()
        assertEquals(7.1234, restored.fxRateToCny, 0.0001)
        assertEquals("2026-09-25", restored.fxRateDate)
    }

    @Test
    fun testV1JsonWithoutFxFieldsDefaults() {
        // A schema-1 file written before fx fields existed stays importable.
        val json = """
            {"schemaVersion":1,"clientTimestampMillis":1700000000000,
             "baseServerRevision":0,"entries":[{
             "uuid":"old","title":"old","amountCents":100,"currency":"CNY",
             "type":"EXPENSE","category":"OTHER","occurredAtMillis":1700000000000,
             "note":"","costTrackingMode":"NONE","salvageValueCents":0,
             "targetDays":null,"retiredAtMillis":null,"billingCycle":"MONTHLY",
             "customCycleDays":30,"isActiveCost":true,"createdAtMillis":1700000000000,
             "updatedAtMillis":1700000000000,"deletedAtMillis":null,
             "syncStatus":"SYNCED","serverRevision":1}]}
        """.trimIndent()
        val restored = LedgerBackup.parse(json).entries.single()
        assertEquals(1.0, restored.fxRateToCny, 0.0001)
        assertNull(restored.fxRateDate)
        assertEquals(100L, restored.baseAmountCents)
    }

    @Test
    fun testParseEmptyEntriesOk() {
        val payload = LedgerBackup.parse(
            LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = emptyList())),
        )
        assertTrue(payload.entries.isEmpty())
    }

    @Test
    fun testPreviewMergeCounts() {
        val local = listOf(
            entry("updated", updatedAt = 100L),
            entry("unchanged", updatedAt = 100L),
            entry("tie", updatedAt = 100L),
        )
        val incoming = listOf(
            entry("updated", updatedAt = 200L),
            entry("unchanged", updatedAt = 50L),
            entry("tie", updatedAt = 100L),
            entry("added", updatedAt = 10L),
        )
        val preview = LedgerBackup.previewMerge(local, incoming)
        assertEquals(1, preview.added)
        assertEquals(1, preview.updated)
        assertEquals(2, preview.unchanged)
    }

    @Test
    fun testPlanReplaceTombstonesRestampsAndCounts() {
        val now = 5_000L
        val local = listOf(
            entry("in-file", updatedAt = 50L),
            entry("local-only", updatedAt = 60L),
            entry("old-tombstone", updatedAt = 70L, deletedAt = 70L),
        )
        val incoming = listOf(
            entry("in-file", updatedAt = 100L),
            entry("new-live", updatedAt = 100L),
            entry("file-tombstone", updatedAt = 100L, deletedAt = 100L),
        )
        val plan = LedgerBackup.planReplace(local, incoming, nowMillis = now)

        assertEquals(1, plan.deletedCount)
        assertEquals(2, plan.keptCount)

        val writes = plan.writes.associateBy { it.uuid }
        // Incoming entries re-stamped and pending; file tombstone preserved.
        assertEquals(now, writes.getValue("in-file").updatedAtMillis)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, writes.getValue("in-file").syncStatus)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, writes.getValue("new-live").syncStatus)
        assertEquals(100L, writes.getValue("file-tombstone").deletedAtMillis)
        // Local-only live entry tombstoned at now.
        val tombstoned = writes.getValue("local-only")
        assertEquals(now, tombstoned.deletedAtMillis)
        assertEquals(now, tombstoned.updatedAtMillis)
        assertEquals(LedgerSyncStatus.PENDING_PUSH, tombstoned.syncStatus)
        assertTrue(!tombstoned.isActiveCost)
        // Pre-existing local tombstone not in the file is left untouched.
        assertNull(writes["old-tombstone"])
    }

    @Test
    fun testReplaceConvergesWithRemoteOnNextSync() {
        val now = 1_000L
        val fileEntries = listOf(
            entry("a", updatedAt = 100L, title = "file-a"),
            entry("b", updatedAt = 100L, title = "file-b"),
        )
        // Local matches the remote: older versions plus an extra local entry.
        val remote = listOf(
            entry("a", updatedAt = 50L, title = "old-a"),
            entry("b", updatedAt = 50L, title = "old-b"),
            entry("extra", updatedAt = 50L, title = "extra"),
        )
        val local = remote.toList()

        val plan = LedgerBackup.planReplace(local, fileEntries, nowMillis = now)
        val localAfterReplace = plan.writes

        val merged = LedgerSyncMerge.merge(localAfterReplace, remote).entries
        val live = merged.filter { it.deletedAtMillis == null }
        assertEquals(
            fileEntries.map { it.uuid }.sorted(),
            live.map { it.uuid }.sorted(),
        )
        assertEquals("file-a", live.single { it.uuid == "a" }.title)
        val extra = merged.single { it.uuid == "extra" }
        assertNotNull(extra.deletedAtMillis)
    }
}
