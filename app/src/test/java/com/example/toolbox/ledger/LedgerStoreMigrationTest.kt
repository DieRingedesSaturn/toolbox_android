package com.example.toolbox.ledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LedgerStoreMigrationTest {

    private lateinit var context: Context
    private val dbFile: File
        get() = context.getDatabasePath(DB_NAME)
    private val snapshotDir: File
        get() = File(context.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        snapshotDir.deleteRecursively()
    }

    @Test
    fun testUpgradeFromV4KeepsEntriesAddsEndDateAndSnapshotsTheOldFile() {
        legacyDatabase(version = 4) {
            execSQL(V4_ENTRIES)
            execSQL(TAGS)
            execSQL(ACCOUNTS)
            insertEntry("e1", "Lunch", 2_500L, "FOOD") {
                put("account_uuid", LedgerAccounts.DEFAULT_ACCOUNT_UUID)
            }
        }

        val store = LedgerStore(context)
        val entry = store.queryVisibleEntries().single()
        val columns = store.readableDatabase.columnsOf("ledger_entries")
        store.close()

        assertEquals("Lunch", entry.title)
        assertEquals(2_500L, entry.amountCents)
        assertEquals(LedgerAccounts.DEFAULT_ACCOUNT_UUID, entry.accountUuid)
        assertNull(entry.costEndsAtMillis)
        assertTrue("cost_ends_at" in columns)

        val snapshot = snapshotDir.listFiles().orEmpty().single { it.name.endsWith("-v4.db") }
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
            assertEquals(4, copy.version)
            assertTrue("cost_ends_at" !in copy.columnsOf("ledger_entries"))
            copy.rawQuery("SELECT title FROM ledger_entries", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Lunch", cursor.getString(0))
            }
        }
    }

    @Test
    fun testUpgradeFromV2BackfillsCategoryTagAndDefaultAccount() {
        legacyDatabase(version = 2) {
            execSQL(V2_ENTRIES)
            insertEntry("e1", "Metro", 400L, "TRANSPORT")
        }

        val store = LedgerStore(context)
        val entry = store.queryVisibleEntries().single()
        val liveTags = store.queryTags(includeDeleted = false)
        val accounts = store.queryAccounts()
        store.close()

        assertEquals("Metro", entry.title)
        assertEquals(LedgerAccounts.DEFAULT_ACCOUNT_UUID, entry.accountUuid)
        assertEquals(listOf(LedgerTags.categoryTagUuid(LedgerCategory.TRANSPORT)), entry.tagUuids)
        assertEquals(entry.tagUuids, liveTags.map { it.uuid })
        assertTrue(accounts.any { it.uuid == LedgerAccounts.DEFAULT_ACCOUNT_UUID })
        assertTrue(snapshotDir.listFiles().orEmpty().any { it.name.endsWith("-v2.db") })
    }

    @Test
    fun testUpgradeFromV1KeepsEntriesAndAddsFxColumns() {
        legacyDatabase(version = 1) {
            execSQL(
                V2_ENTRIES.replace("fx_rate_to_cny REAL NOT NULL DEFAULT 1.0,", "")
                    .replace("fx_rate_date TEXT,", ""),
            )
            insertEntry("v1", "V1 entry", 123L, "FOOD")
        }
        LedgerStore(context).use { store ->
            val entry = store.queryVisibleEntries().single()
            assertEquals("V1 entry", entry.title)
            assertEquals(1.0, entry.fxRateToCny, 0.0)
            assertEquals(5, store.readableDatabase.version)
        }
    }

    @Test
    fun testUpgradeFromV3KeepsEntriesAndAddsEndDate() {
        legacyDatabase(version = 3) {
            execSQL(V4_ENTRIES)
            execSQL(TAGS)
            execSQL(ACCOUNTS)
            insertEntry("v3", "V3 entry", 456L, "FOOD")
        }
        LedgerStore(context).use { store ->
            assertEquals("V3 entry", store.queryVisibleEntries().single().title)
            assertEquals(5, store.readableDatabase.version)
        }
    }

    @Test
    fun testBackupFailureStopsUpgradeAndLeavesOldDataReadable() {
        legacyDatabase(version = 2) {
            execSQL(V2_ENTRIES)
            insertEntry("e1", "Original", 789L, "FOOD")
        }
        snapshotDir.parentFile!!.mkdirs()
        snapshotDir.writeText("Cannot create a directory here")
        LedgerStore(context).use { store ->
            org.junit.Assert.assertThrows(LedgerUpgradeBackupException::class.java) {
                store.queryVisibleEntries()
            }
        }
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(2, db.version)
            assertTrue("cost_ends_at" !in db.columnsOf("ledger_entries"))
            db.rawQuery("SELECT title FROM ledger_entries", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Original", cursor.getString(0))
            }
        }
        snapshotDir.delete()
        LedgerStore(context).use { store ->
            assertEquals("Original", store.queryVisibleEntries().single().title)
            assertEquals(5, store.readableDatabase.version)
        }
    }

    @Test
    fun testRestoreMigratesAnIsolatedV4CopyAndLeavesTheSourceUnchanged() {
        legacyDatabase(version = 4) {
            execSQL(V4_ENTRIES)
            execSQL(TAGS)
            execSQL(ACCOUNTS)
            insertEntry("e1", "Restored", 2_500L, "FOOD")
        }
        val snapshot = LedgerDbSnapshots.save(dbFile, snapshotDir, 4, 1_790_000_000_000L)
        val bytes = snapshot.readBytes()
        context.deleteDatabase(DB_NAME)
        LedgerStore(context).use { store ->
            store.upsert(LedgerEntry(title = "Current", amountCents = 100L, occurredAtMillis = OCCURRED_AT))
            store.restoreSnapshot(snapshot)
            assertEquals("Restored", store.queryVisibleEntries().single().title)
            assertEquals(5, store.readableDatabase.version)
            org.junit.Assert.assertArrayEquals(bytes, snapshot.readBytes())
        }
    }

    @Test
    fun testFreshAndCurrentDatabasesTakeNoSnapshot() {
        LedgerStore(context).apply {
            queryVisibleEntries()
            close()
        }
        LedgerStore(context).apply {
            queryVisibleEntries()
            close()
        }

        assertTrue(snapshotDir.listFiles().isNullOrEmpty())
    }

    private fun legacyDatabase(version: Int, build: SQLiteDatabase.() -> Unit) {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.build()
            db.version = version
        }
    }

    private fun SQLiteDatabase.insertEntry(
        uuid: String,
        title: String,
        cents: Long,
        category: String,
        extra: ContentValues.() -> Unit = {},
    ) {
        insertOrThrow(
            "ledger_entries",
            null,
            ContentValues().apply {
                put("uuid", uuid)
                put("title", title)
                put("amount_cents", cents)
                put("currency", "CNY")
                put("type", LedgerEntryType.EXPENSE.name)
                put("category", category)
                put("occurred_at", OCCURRED_AT)
                put("note", "")
                put("cost_tracking_mode", CostTrackingMode.NONE.name)
                put("salvage_value_cents", 0L)
                put("billing_cycle", BillingCycle.MONTHLY.name)
                put("custom_cycle_days", 30)
                put("is_active_cost", 1)
                put("created_at", OCCURRED_AT)
                put("updated_at", OCCURRED_AT)
                put("sync_status", LedgerSyncStatus.SYNCED.name)
                put("server_revision", 0L)
                extra()
            },
        )
    }

    private fun SQLiteDatabase.columnsOf(table: String): Set<String> =
        rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }

    private companion object {
        const val DB_NAME = "toolbox_ledger.db"
        const val OCCURRED_AT = 1_790_000_000_000L

        val V2_ENTRIES = """
            CREATE TABLE ledger_entries (
                uuid TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                fx_rate_to_cny REAL NOT NULL DEFAULT 1.0,
                fx_rate_date TEXT,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL,
                cost_tracking_mode TEXT NOT NULL,
                salvage_value_cents INTEGER NOT NULL,
                target_days INTEGER,
                retired_at INTEGER,
                billing_cycle TEXT NOT NULL,
                custom_cycle_days INTEGER NOT NULL,
                is_active_cost INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL,
                server_revision INTEGER NOT NULL
            )
        """.trimIndent()

        val V4_ENTRIES = """
            CREATE TABLE ledger_entries (
                uuid TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                fx_rate_to_cny REAL NOT NULL DEFAULT 1.0,
                fx_rate_date TEXT,
                fx_rate_source TEXT,
                parent_uuid TEXT,
                link_type TEXT,
                renewal_index INTEGER,
                disposal_type TEXT,
                tag_uuids TEXT NOT NULL DEFAULT '[]',
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL,
                cost_tracking_mode TEXT NOT NULL,
                salvage_value_cents INTEGER NOT NULL,
                target_days INTEGER,
                retired_at INTEGER,
                billing_cycle TEXT NOT NULL,
                custom_cycle_days INTEGER NOT NULL,
                custom_cycle_unit TEXT NOT NULL DEFAULT 'DAYS',
                is_active_cost INTEGER NOT NULL,
                account_uuid TEXT,
                account_amount_cents INTEGER,
                to_account_uuid TEXT,
                to_amount_cents INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL,
                server_revision INTEGER NOT NULL
            )
        """.trimIndent()

        val TAGS = """
            CREATE TABLE ledger_tags (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL
            )
        """.trimIndent()

        val ACCOUNTS = """
            CREATE TABLE ledger_accounts (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                currency TEXT NOT NULL,
                opening_balance_cents INTEGER NOT NULL,
                opening_at INTEGER NOT NULL,
                color INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                is_archived INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL
            )
        """.trimIndent()
    }
}
