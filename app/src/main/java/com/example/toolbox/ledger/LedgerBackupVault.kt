package com.example.toolbox.ledger

import com.example.toolbox.totp.TotpVaultCrypto
import org.json.JSONObject

/** Tool-specific authenticated envelope; legacy plaintext remains readable for migration. */
class LedgerBackupVault(private val passphrase: String) {
    init { require(TotpVaultCrypto.validatePassphrase(passphrase)) }

    fun encode(plain: String): String {
        checkPayloadSize(plain)
        return TotpVaultCrypto.encryptPayload(plain, passphrase, FORMAT, AAD)
    }

    fun decode(body: String): String {
        val normalized = body.removePrefix("\uFEFF")
        val root = JSONObject(normalized)
        val format = root.optString("format")
        if (format.isEmpty()) {
            // Never interpret a damaged encrypted envelope as an empty legacy ledger.
            if (root.has("cipher") || root.has("kdf") || root.optJSONArray("entries") == null) {
                throw BackupException(BackupFailure.INVALID_FILE)
            }
            return normalized.also(::checkPayloadSize)
        }
        return TotpVaultCrypto.decryptPayload(normalized, passphrase, mapOf(FORMAT to AAD), MAX_BYTES).also {
            checkPayloadSize(it)
        }
    }

    private fun checkPayloadSize(body: String) {
        if (body.toByteArray(Charsets.UTF_8).size > LedgerBackup.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
    }

    companion object {
        const val FORMAT = "toolbox-ledger-vault"
        private const val AAD = "toolbox-ledger-vault:v1"
        const val MAX_BYTES = 40 * 1024 * 1024
    }
}
