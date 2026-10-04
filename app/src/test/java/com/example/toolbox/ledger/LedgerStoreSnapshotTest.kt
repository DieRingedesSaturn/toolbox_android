package com.example.toolbox.ledger

import android.content.Context
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LedgerStoreSnapshotTest {
    private lateinit var context: Context
    private val dir: File
        get() = File(context.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME)
    private val dbFile: File
        get() = context.getDatabasePath(LedgerStore.DATABASE_NAME)
    private val time = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(LedgerStore.DATABASE_NAME)
        dir.deleteRecursively()
    }

    private fun entry(title: String) = LedgerEntry(
        title = title,
        amountCents = 123L,
        occurredAtMillis = time,
    )

    private fun snapshot(): File {
        LedgerStore(context).use { it.upsert(entry("Restored")) }
        return LedgerDbSnapshots.save(dbFile, dir, 5, time).also {
            context.deleteDatabase(LedgerStore.DATABASE_NAME)
        }
    }

    @Test
    fun testRestoreOldestOfThreeKeepsCurrentDataAsARecoveryCopy() {
        val source = snapshot()
        for (day in 1L..2L) {
            LedgerDbSnapshots.save(source, dir, 5, time + day * 86_400_000L)
        }
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            store.restoreSnapshot(source, time + 3 * 86_400_000L)
            assertEquals("Restored", store.queryVisibleEntries().single().title)
            assertEquals(3, LedgerDbSnapshots.list(dir).size)
            val backup = LedgerDbSnapshots.list(dir).first().file
            store.restoreSnapshot(backup, time + 4 * 86_400_000L)
            assertEquals("Current", store.queryVisibleEntries().single().title)
        }
    }

    @Test
    fun testCorruptOrUnsupportedSnapshotsNeverReplaceTheCurrentLedger() {
        val corrupt = File(context.cacheDir, "corrupt.db").apply { writeText("not a database") }
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            assertThrows(Exception::class.java) { store.restoreSnapshot(corrupt) }
            assertEquals("Current", store.queryVisibleEntries().single().title)
            assertEquals("not a database", corrupt.readText())
        }
        val unsupported = snapshot()
        android.database.sqlite.SQLiteDatabase.openDatabase(
            unsupported.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { it.version = 99 }
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            assertThrows(IllegalStateException::class.java) { store.restoreSnapshot(unsupported) }
            assertEquals("Current", store.queryVisibleEntries().single().title)
        }
    }

    @Test
    fun testInsertFailureRollsBackDeletesAndPreservesTheRecoverySource() {
        val source = snapshot()
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            store.writableDatabase.execSQL(
                """
                CREATE TRIGGER reject_restore BEFORE INSERT ON ledger_entries
                WHEN NEW.title = 'Restored'
                BEGIN SELECT RAISE(ABORT, 'Simulated restore write failure'); END
                """.trimIndent(),
            )
            assertThrows(Exception::class.java) { store.restoreSnapshot(source) }
            assertEquals("Current", store.queryVisibleEntries().single().title)
            assertTrue(source.isFile)
        }
        LedgerStore(context).use { store ->
            assertEquals("Current", store.queryVisibleEntries().single().title)
        }
    }

    @Test
    fun testBackupFailureKeepsCurrentLedgerAndTheSelectedCopy() {
        val source = snapshot()
        val bytes = source.readBytes()
        dir.deleteRecursively()
        val selected = File(context.cacheDir, "selected.db").apply { writeBytes(bytes) }
        dir.writeText("Cannot create a directory here")
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            assertThrows(Exception::class.java) { store.restoreSnapshot(selected) }
            assertEquals("Current", store.queryVisibleEntries().single().title)
            org.junit.Assert.assertArrayEquals(bytes, selected.readBytes())
        }
    }

    @Test
    fun testSnapshotWalIsReadWithoutTouchingTheSourceFiles() {
        lateinit var source: File
        LedgerStore(context).use { store ->
            store.setWriteAheadLoggingEnabled(true)
            store.writableDatabase.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            store.upsert(entry("WAL payment"))
            assertTrue(File(dbFile.path + "-wal").length() > 0L)
            source = LedgerDbSnapshots.save(dbFile, dir, 5, time)
        }
        val sourceBytes = source.readBytes()
        val sourceWal = File(source.path + "-wal")
        val walBytes = sourceWal.readBytes()
        context.deleteDatabase(LedgerStore.DATABASE_NAME)
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            store.restoreSnapshot(source)
            assertEquals("WAL payment", store.queryVisibleEntries().single().title)
            org.junit.Assert.assertArrayEquals(sourceBytes, source.readBytes())
            org.junit.Assert.assertArrayEquals(walBytes, sourceWal.readBytes())
        }
    }

    @Test
    fun testRestoreAndRecoveryCopyPreserveAllRowsAndMetadata() {
        val tag = LedgerTag(
            uuid = "tag", name = "Travel", colorArgb = 123, sortOrder = 2,
            createdAtMillis = time, updatedAtMillis = time + 1,
            deletedAtMillis = time + 2, syncStatus = LedgerSyncStatus.SYNCED,
        )
        val account = LedgerAccount(
            uuid = "account", name = "USD account", currency = "USD",
            openingBalanceCents = 20_000L, openingAtMillis = time, colorArgb = 456,
            createdAtMillis = time, updatedAtMillis = time + 3,
            deletedAtMillis = time + 4, syncStatus = LedgerSyncStatus.SYNCED,
        )
        val parent = entry("Foreign subscription").copy(
            uuid = "parent", currency = "USD", fxRateToCny = 6.5,
            fxRateDate = "2026-10-01", fxRateSource = FX_SOURCE_MANUAL,
            note = "Preserve this", accountUuid = account.uuid,
            accountAmountCents = 123L, tagUuids = listOf(tag.uuid),
            costTrackingMode = CostTrackingMode.PERIODIC_SUBSCRIPTION,
            costEndsAtMillis = time + 10 * 86_400_000L,
            createdAtMillis = time, updatedAtMillis = time + 5,
            syncStatus = LedgerSyncStatus.SYNCED, serverRevision = 42L,
        )
        val child = parent.copy(
            uuid = "child", parentUuid = parent.uuid,
            linkType = LINK_TYPE_RENEWAL, renewalIndex = 1,
            costTrackingMode = CostTrackingMode.NONE,
            deletedAtMillis = time + 6, updatedAtMillis = time + 6,
        )
        lateinit var expected: LedgerSyncPayload
        LedgerStore(context).use { store ->
            store.importSyncPayload(
                LedgerSyncPayload(entries = listOf(parent, child), tags = listOf(tag), accounts = listOf(account)),
            )
            expected = LedgerSyncPayload(
                entries = store.queryAllEntriesForSync(),
                tags = store.queryTags(includeDeleted = true),
                accounts = store.queryAccounts(includeDeleted = true),
            )
        }
        val source = LedgerDbSnapshots.save(dbFile, dir, 5, time)
        context.deleteDatabase(LedgerStore.DATABASE_NAME)
        LedgerStore(context).use { store ->
            val current = entry("Current")
            store.upsert(current)
            val currentRows = store.queryAllEntriesForSync()
            store.restoreSnapshot(source, time + 86_400_000L)
            assertEquals(expected.entries, store.queryAllEntriesForSync())
            assertEquals(expected.tags, store.queryTags(includeDeleted = true))
            assertEquals(expected.accounts, store.queryAccounts(includeDeleted = true))
            val recovery = LedgerDbSnapshots.list(dir).first().file
            store.restoreSnapshot(recovery, time + 2 * 86_400_000L)
            assertEquals(currentRows, store.queryAllEntriesForSync())
        }
    }

    @Test
    fun testAlreadyOpenHelperSeesRestoredDataAndCanStillWrite() {
        val source = snapshot()
        LedgerStore(context).use { store ->
            store.upsert(entry("Current"))
            LedgerStore(context).use { other ->
                assertEquals("Current", other.queryVisibleEntries().single().title)
                store.restoreSnapshot(source)
                assertEquals("Restored", other.queryVisibleEntries().single().title)
                other.upsert(entry("After restore"))
                assertEquals(2, store.queryVisibleEntries().size)
            }
        }
    }
}
