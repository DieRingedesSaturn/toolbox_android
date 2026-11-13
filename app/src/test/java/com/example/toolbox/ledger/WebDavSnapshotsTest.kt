package com.example.toolbox.ledger

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSnapshotsTest {

    @Test
    fun snapshotTimesSupportOldAndUniqueNamesAndRejectInvalidDates() {
        val old = "ledger-backup-20240305-080910.json"
        val newer = "ledger-backup-20240305-080910-123-abcdef12.json"
        assertEquals(LocalDateTime.of(2024, 3, 5, 8, 9, 10), WebDavHistory.snapshotTime(BackupTool.LEDGER, old))
        assertEquals(123_000_000, WebDavHistory.snapshotTime(BackupTool.LEDGER, newer)!!.nano)
        assertNull(WebDavHistory.snapshotTime(BackupTool.LEDGER, "ledger-backup-20240230-080910.json"))
        assertNull(WebDavHistory.snapshotTime(BackupTool.TOTP, old))
        assertNull(WebDavHistory.snapshotTime(BackupTool.LEDGER, "../ledger.json"))
    }

    @Test
    fun testSnapshotNameUsesZone() {
        val millis = ZonedDateTime.of(2024, 3, 5, 8, 9, 10, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val utc = WebDavSnapshots.snapshotName(millis, ZoneId.of("UTC"))
        val shanghai = WebDavSnapshots.snapshotName(millis, ZoneId.of("Asia/Shanghai"))
        assertTrue(utc.startsWith("ledger-backup-20240305-080910-000-"))
        assertTrue(shanghai.startsWith("ledger-backup-20240305-160910-000-"))
        assertTrue(WebDavSnapshots.NAME_REGEX.matches(utc))
        assertFalse(utc == WebDavSnapshots.snapshotName(millis, ZoneId.of("UTC")))
    }

    @Test
    fun testIndexJsonRoundTrip() {
        val names = listOf(
            "ledger-backup-20240305-080910.json",
            "ledger-backup-20240306-080910.json",
        )
        assertEquals(names, WebDavSnapshots.parseIndex(WebDavSnapshots.indexJson(names)))
    }

    @Test
    fun testParseIndexGarbageAndNull() {
        assertTrue(WebDavSnapshots.parseIndex(null).isEmpty())
        assertTrue(WebDavSnapshots.parseIndex("").isEmpty())
        assertTrue(WebDavSnapshots.parseIndex("not json").isEmpty())
        assertTrue(WebDavSnapshots.parseIndex("{\"other\":1}").isEmpty())
    }

    @Test
    fun testParseIndexDropsNonMatchingNames() {
        val json = WebDavSnapshots.indexJson(
            listOf("ledger-backup-20240305-080910.json", "evil.json", "../x"),
        )
        assertEquals(
            listOf("ledger-backup-20240305-080910.json"),
            WebDavSnapshots.parseIndex(json),
        )
    }

    @Test
    fun testPruneKeepsNewestTen() {
        val existing = (1..10).map {
            "ledger-backup-20240305-0000%02d.json".format(it)
        }
        val newName = "ledger-backup-20240305-000011.json"
        val (keep, delete) = WebDavSnapshots.prune(existing, newName)
        assertEquals(WebDavSnapshots.KEEP, keep.size)
        assertFalse(keep.contains(existing.first()))
        assertEquals(newName, keep.last())
        assertEquals(listOf(existing.first()), delete)
    }

    @Test
    fun testPruneNeverDeletesNonMatchingNames() {
        val existing = listOf("not-a-snapshot", "ledger-backup-20240305-000001.json")
        val (keep, delete) = WebDavSnapshots.prune(
            existing,
            "ledger-backup-20240305-000002.json",
        )
        assertTrue(delete.all { WebDavSnapshots.NAME_REGEX.matches(it) })
        assertFalse(keep.contains("not-a-snapshot"))
    }
}
