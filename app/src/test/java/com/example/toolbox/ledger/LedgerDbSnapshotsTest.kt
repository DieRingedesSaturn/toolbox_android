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

    @Test
    fun testListParsesNamesAndSortsNewestFirst() {
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME).apply { mkdirs() }
        File(dir, "toolbox_ledger-20261003-153000-v4.db").writeText("a")
        File(dir, "toolbox_ledger-20261005-093000-v5.db").writeText("b")
        File(dir, "toolbox_ledger-20261004-123000-v4.db").writeText("c")
        File(dir, "toolbox_ledger-20261005-093000-v5.db-wal").writeText("wal")
        File(dir, "notes.txt").writeText("keep")
        File(dir, "toolbox_ledger-badname.db").writeText("junk")

        val snapshots = LedgerDbSnapshots.list(dir, utc)

        assertEquals(3, snapshots.size)
        assertEquals(millis(5, 9), snapshots[0].takenAtMillis)
        assertEquals(5, snapshots[0].schemaVersion)
        assertEquals(millis(4, 12), snapshots[1].takenAtMillis)
        assertEquals(4, snapshots[1].schemaVersion)
        assertEquals(millis(3, 15), snapshots[2].takenAtMillis)
    }

    @Test
    fun testStagedCopyPreservesSourceAndCleansUpOnFailure() {
        val source = temp.newFile("snapshot.db").apply { writeText("snapshot") }
        File(source.path + "-wal").writeText("wal pages")
        var work: File? = null

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            LedgerDbSnapshots.withStagedCopy(source, temp.root) { staged ->
                work = staged.parentFile
                assertEquals("snapshot", staged.readText())
                assertEquals("wal pages", File(staged.path + "-wal").readText())
                staged.writeText("changed only in staging")
                error("Validation failed")
            }
        }

        assertEquals("snapshot", source.readText())
        assertEquals("wal pages", File(source.path + "-wal").readText())
        assertFalse(work!!.exists())
    }

    @Test
    fun testSavingCurrentCopyCanDeferPruningTheRecoverySource() {
        val db = temp.newFile("toolbox_ledger.db").apply { writeText("old data") }
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME)
        val oldest = LedgerDbSnapshots.save(db, dir, 4, millis(1, 9), utc)
        for (day in 2..3) LedgerDbSnapshots.save(db, dir, 4, millis(day, 9), utc)
        db.writeText("current data")

        LedgerDbSnapshots.save(db, dir, 5, millis(4, 9), utc, pruneAfterSave = false)

        assertTrue(oldest.isFile)
        assertEquals("old data", oldest.readText())
        assertEquals(4, LedgerDbSnapshots.list(dir).size)
        LedgerDbSnapshots.prune(dir, LedgerDbSnapshots.KEEP)
        assertEquals(3, LedgerDbSnapshots.list(dir).size)
    }

    @Test
    fun testTwoSavesInTheSameSecondNeverOverwriteACopy() {
        val db = temp.newFile("toolbox_ledger.db").apply { writeText("first") }
        val dir = File(temp.root, LedgerDbSnapshots.DIR_NAME)
        val first = LedgerDbSnapshots.save(db, dir, 5, millis(3, 15), utc)
        db.writeText("second")

        val second = LedgerDbSnapshots.save(db, dir, 5, millis(3, 15), utc)

        assertTrue(first != second)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
        assertEquals(second, LedgerDbSnapshots.list(dir, utc).first().file)
        assertTrue(dir.listFiles().orEmpty().none { it.name.startsWith(".pending-") })
    }
}
