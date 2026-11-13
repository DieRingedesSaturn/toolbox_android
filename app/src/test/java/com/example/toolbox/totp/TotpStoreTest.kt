package com.example.toolbox.totp

import android.util.AtomicFile
import androidx.core.content.edit
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TotpStoreTest {
    private lateinit var cipher: FakeTotpCipher
    private lateinit var store: TotpStore
    private lateinit var vaultFile: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("totp_prefs", 0).edit(commit = true) { clear() }
        context.noBackupFilesDir.mkdirs()
        vaultFile = File(context.noBackupFilesDir, TotpStore.LOCAL_FILE_NAME)
        vaultFile.delete()
        File(vaultFile.path + ".bak").delete()
        cipher = FakeTotpCipher()
        store = TotpStore(context, AtomicFile(vaultFile), cipher)
    }

    @Test
    fun missingVaultReadsEmptyAndUpsertKeepsEveryToken() {
        assertTrue(store.queryAllForSync().isEmpty())
        store.upsert(entry("one", "11111111-1111-1111-1111-111111111111"))
        store.upsert(entry("two", "22222222-2222-2222-2222-222222222222"))
        assertEquals(
            listOf("one", "two"),
            store.queryVisible().map { it.issuer }.sorted(),
        )
    }

    @Test
    fun decryptFailureDoesNotLetUpsertSyncOrReplaceWipeTheFile() {
        store.upsert(entry("kept", "11111111-1111-1111-1111-111111111111"))
        val before = vaultFile.readBytes()
        cipher.failDecrypt = true

        assertFails { store.queryAllForSync() }
        assertFails { store.queryVisible() }
        assertFails { store.upsert(entry("new", "22222222-2222-2222-2222-222222222222")) }
        assertFails { store.softDelete("11111111-1111-1111-1111-111111111111") }
        assertFails {
            store.mergeAndSave(listOf(entry("remote", "33333333-3333-3333-3333-333333333333")))
        }
        assertFails { store.replaceAll(emptyList()) }
        assertArrayEquals(before, vaultFile.readBytes())

        cipher.failDecrypt = false
        assertEquals(listOf("kept"), store.queryVisible().map { it.issuer })
    }

    @Test
    fun parseFailureDoesNotOverwriteTheVault() {
        store.upsert(entry("kept", "11111111-1111-1111-1111-111111111111"))
        val before = vaultFile.readBytes()
        cipher.decryptOverride = "not-json"
        assertFails { store.queryAllForSync() }
        assertFails { store.replaceAll(emptyList()) }
        assertFails { store.mergeAndSave(emptyList()) }
        assertArrayEquals(before, vaultFile.readBytes())
    }

    @Test
    fun explicitReplaceCanOverwriteAnUnreadableVault() {
        store.upsert(entry("kept", "11111111-1111-1111-1111-111111111111"))
        cipher.failDecrypt = true
        store.replaceAll(
            listOf(entry("restored", "44444444-4444-4444-4444-444444444444")),
            overwriteUnreadable = true,
        )
        cipher.failDecrypt = false
        assertEquals(listOf("restored"), store.queryVisible().map { it.issuer })
    }

    @Test
    fun truncatedFileIsNotTreatedAsEmpty() {
        vaultFile.writeBytes(byteArrayOf(1, 2, 3))
        assertFails { store.queryAllForSync() }
        assertFails { store.upsert(entry("new", "22222222-2222-2222-2222-222222222222")) }
        assertArrayEquals(byteArrayOf(1, 2, 3), vaultFile.readBytes())
    }

    @Test
    fun syncMergeKeepsEditsAndNewTokensMadeAfterUploadStarted() {
        val old = entry("old", "11111111-1111-1111-1111-111111111111").copy(updatedAtMillis = 100L)
        store.upsert(old)
        val uploaded = store.queryAllForSync()
        store.upsert(old.copy(issuer = "edited", updatedAtMillis = 200L))
        store.upsert(entry("new", "22222222-2222-2222-2222-222222222222"))
        store.mergeAndSave(uploaded)
        assertEquals(setOf("edited", "new"), store.queryVisible().map { it.issuer }.toSet())
    }

    @Test
    fun restoreCreatesNewRevisionsAndTombstonesThatSurviveNextSync() {
        val old = entry("old", "11111111-1111-1111-1111-111111111111").copy(updatedAtMillis = 100L)
        val remote = listOf(old.copy(issuer = "remote", updatedAtMillis = 9000L),
            entry("omitted", "22222222-2222-2222-2222-222222222222").copy(updatedAtMillis = 8000L))
        store.replaceAll(remote)
        store.restore(listOf(old), nowMillis = 500L)
        store.mergeAndSave(remote)
        assertEquals(listOf("old"), store.queryVisible().map { it.issuer })
        assertTrue(store.queryAllForSync().all { it.updatedAtMillis == 9001L })
        assertEquals(9001L, store.queryAllForSync().single { it.issuer == "omitted" }.deletedAtMillis)
        store.upsert(store.queryVisible().single().copy(issuer = "edited", updatedAtMillis = 600L))
        assertEquals(9002L, store.queryVisible().single().updatedAtMillis)
    }

    @Test
    fun missingPassphraseAndLegacyFallbackRemainDistinctFromFailure() {
        assertNull(store.readSavedDefaultBackupPassphrase().getOrThrow())
        store.savePassphrase("legacy-test-passphrase")
        assertEquals("legacy-test-passphrase", store.readSavedDefaultBackupPassphrase().getOrThrow())
        store.saveDefaultBackupPassphrase("default-test-passphrase")
        assertEquals("default-test-passphrase", store.readSavedDefaultBackupPassphrase().getOrThrow())
    }

    @Test
    fun failedStrictReadPreservesCiphertextAndCanBeRetried() {
        store.saveDefaultBackupPassphrase("default-test-passphrase")
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("totp_prefs", 0)
        val before = prefs.all.toMap()
        cipher.failDecrypt = true
        assertThrows(TotpPassphraseReadException::class.java) {
            store.readSavedDefaultBackupPassphrase().getOrThrow()
        }
        assertEquals(before, prefs.all)
        // Existing nullable callers retain their previous best-effort behavior.
        assertNull(store.savedDefaultBackupPassphrase())
        cipher.failDecrypt = false
        assertEquals("default-test-passphrase", store.readSavedDefaultBackupPassphrase().getOrThrow())
    }

    @Test
    fun unreadableDefaultDoesNotSilentlyFallBackToAnotherPasswordInStrictMode() {
        store.savePassphrase("legacy-test-passphrase")
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("totp_prefs", 0)
        prefs.edit(commit = true) { putString("default_backup_passphrase_enc", "!") }
        assertThrows(TotpPassphraseReadException::class.java) {
            store.readSavedDefaultBackupPassphrase().getOrThrow()
        }
        assertEquals("legacy-test-passphrase", store.savedDefaultBackupPassphrase())
        assertEquals("!", prefs.getString("default_backup_passphrase_enc", null))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("expected TotpLocalVaultException")
        } catch (_: TotpLocalVaultException) {
            // The vault must stay untouched.
        }
    }

    private fun entry(issuer: String, uuid: String) = TotpEntry(
        uuid = uuid,
        issuer = issuer,
        accountName = "user",
        secret = "JBSWY3DPEHPK3PXP",
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    private class FakeTotpCipher : TotpLocalCipher {
        var failDecrypt = false
        var decryptOverride: String? = null

        override fun encrypt(plainText: String): ByteArray = plainText.toByteArray(Charsets.UTF_8)

        override fun decrypt(raw: ByteArray): String? {
            if (failDecrypt) return null
            decryptOverride?.let { return it }
            if (raw.isEmpty()) return null
            return raw.toString(Charsets.UTF_8)
        }
    }
}
