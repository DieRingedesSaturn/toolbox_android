package com.example.toolbox.ledger

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSnapshotsTest {

    @Test
    fun testSnapshotNameUsesZone() {
        val millis = ZonedDateTime.of(2024, 3, 5, 8, 9, 10, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        assertEquals(
            "ledger-backup-20240305-080910.json",
            WebDavSnapshots.snapshotName(millis, ZoneId.of("UTC")),
        )
        assertEquals(
            "ledger-backup-20240305-160910.json",
            WebDavSnapshots.snapshotName(millis, ZoneId.of("Asia/Shanghai")),
        )
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
