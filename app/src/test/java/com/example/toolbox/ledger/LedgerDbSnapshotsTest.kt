package com.example.toolbox.ledger

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LedgerDbSnapshotsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val utc = ZoneId.of("UTC")

    private fun millis(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 10, day, hour, 30, 0, 0, utc).toInstant().toEpochMilli()

    @Test
    fun testFileNameSortsChronologicallyAndCarriesVersion() {
        assertEquals(
            "toolbox_ledger-20261003-153000-v4.db",
            LedgerDbSnapshots.fileName(4, millis(3, 15), utc),
        )
    }

    @Test
    fun testSaveCopiesDatabaseAndNonEmptyWal() {
        val db = temp.newFile("toolbox_ledger.db").apply { writeText("main") }
        File(db.path + "-wal").writeText("wal pages")
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME)

        val saved = LedgerDbSnapshots.save(db, dir, 4, millis(3, 15), utc)

        assertEquals("main", saved.readText())
        assertEquals("wal pages", File(saved.path + "-wal").readText())
    }

    @Test
    fun testSaveSkipsEmptyWal() {
        val db = temp.newFile("toolbox_ledger.db").apply { writeText("main") }
        File(db.path + "-wal").createNewFile()
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME)

        val saved = LedgerDbSnapshots.save(db, dir, 4, millis(3, 15), utc)

        assertFalse(File(saved.path + "-wal").exists())
    }

    @Test
    fun testKeepsOnlyNewestSnapshotsAndLeavesOtherFiles() {
        val db = temp.newFile("toolbox_ledger.db").apply { writeText("main") }
        File(db.path + "-wal").writeText("wal")
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME)
        val unrelated = File(dir.apply { mkdirs() }, "notes.txt").apply { writeText("keep") }

        val saved = (1..5).map { day -> LedgerDbSnapshots.save(db, dir, day, millis(day, 9), utc) }

        val remaining = dir.listFiles().orEmpty().map { it.name }.toSet()
        val expected = saved.takeLast(LedgerDbSnapshots.KEEP)
            .flatMap { listOf(it.name, it.name + "-wal") }
            .toSet() + unrelated.name
        assertEquals(expected, remaining)
        assertTrue(unrelated.exists())
    }
}
