package com.example.toolbox.ledger

import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpVaultException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerBackupVaultTest {
    private val vault = LedgerBackupVault("test-passphrase")
    private val plain = LedgerSyncSerializer.toJsonString(LedgerSyncPayload(entries = listOf(
        LedgerEntry(uuid = "test-entry", title = "private title", amountCents = 100L, occurredAtMillis = 100L),
    )))

    @Test
    fun encryptedRoundTripAndLegacyMigration() {
        val encrypted = vault.encode(plain)
        assertFalse(encrypted.contains("private title"))
        assertEquals(plain, vault.decode(encrypted))
        assertEquals(plain, vault.decode(plain))
        assertEquals(LedgerBackupVault.FORMAT, JSONObject(encrypted).getString("format"))
    }

    @Test
    fun wrongPasswordTamperingAndDifferentToolAreRejected() {
        val encrypted = vault.encode(plain)
        assertThrows(TotpVaultException::class.java) { LedgerBackupVault("wrong-password").decode(encrypted) }
        val modified = JSONObject(encrypted).put("format", TotpVaultCrypto.FORMAT_TOOLBOX).toString()
        assertThrows(TotpVaultException::class.java) { vault.decode(modified) }
        assertThrows(TotpVaultException::class.java) {
            TotpVaultCrypto.decryptVault(encrypted, "test-passphrase")
        }
        val tampered = JSONObject(encrypted)
        val cipher = tampered.getJSONObject("cipher")
        val withoutFormat = JSONObject(encrypted).apply { remove("format") }.toString()
        assertThrows(BackupException::class.java) { vault.decode(withoutFormat) }
        assertThrows(BackupException::class.java) { vault.decode("{}") }
        val ciphertext = cipher.getString("data")
        cipher.put("data", (if (ciphertext[0] == 'A') "B" else "A") + ciphertext.drop(1))
        assertThrows(TotpVaultException::class.java) { vault.decode(tampered.toString()) }
    }
}
