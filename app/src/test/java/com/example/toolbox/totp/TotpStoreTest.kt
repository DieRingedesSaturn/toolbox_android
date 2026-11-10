package com.example.toolbox.totp

import android.util.AtomicFile
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
