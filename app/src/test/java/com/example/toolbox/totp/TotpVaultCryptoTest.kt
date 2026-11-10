package com.example.toolbox.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TotpVaultCryptoTest {
    @Test
    fun encryptAndDecryptRoundTripWithSmallestWorkFactor() {
        val entries = listOf(
            TotpEntry(
                uuid = "id-1",
                issuer = "GitHub",
                accountName = "octocat",
                secret = "JBSWY3DPEHPK3PXP",
                algorithm = TotpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
                deletedAtMillis = null,
            ),
            TotpEntry(
                uuid = "id-2",
                issuer = "Cloudflare",
                accountName = "ops@example.com",
                secret = "GEZDGNBVGY3TQOJQ",
                algorithm = TotpAlgorithm.SHA256,
                digits = 8,
                period = 60,
                createdAtMillis = 1500L,
                updatedAtMillis = 3000L,
                deletedAtMillis = 3000L,
            ),
        )
        val encrypted = TotpVaultCrypto.encryptVault(
            entries = entries,
            passphrase = "correct-horse-battery-staple",
            iterations = 100_000,
        )
        val decrypted = TotpVaultCrypto.decryptVault(
            envelopeJson = encrypted,
            passphrase = "correct-horse-battery-staple",
        )
        assertEquals(entries, decrypted)
    }

    @Test
    fun wrongPassphraseThrowsVaultException() {
        val entries = listOf(
            TotpEntry(
                uuid = "id-1",
                issuer = "GitHub",
                accountName = "octocat",
                secret = "JBSWY3DPEHPK3PXP",
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
            ),
        )
        val encrypted = TotpVaultCrypto.encryptVault(
            entries = entries,
            passphrase = "secret-password",
            iterations = 100_000,
        )
        try {
            TotpVaultCrypto.decryptVault(
                envelopeJson = encrypted,
                passphrase = "wrong-password",
            )
            fail("Expected TotpVaultException")
        } catch (e: TotpVaultException) {
            assertEquals(TotpVaultFailure.WRONG_PASSPHRASE_OR_CORRUPTED, e.failure)
        }
    }

    @Test
    fun mergeFollowsLastWriteWinsIncludingTombstones() {
        val local = listOf(
            TotpEntry(
                uuid = "u1",
                issuer = "GitHub",
                accountName = "old",
                secret = "JBSWY3DPEHPK3PXP",
                createdAtMillis = 100L,
                updatedAtMillis = 100L,
            ),
            TotpEntry(
                uuid = "u2",
                issuer = "Cloudflare",
                accountName = "stay",
                secret = "JBSWY3DPEHPK3PXP",
                createdAtMillis = 100L,
                updatedAtMillis = 500L,
            ),
        )
        val remote = listOf(
            TotpEntry(
                uuid = "u1",
                issuer = "GitHub",
                accountName = "new",
                secret = "JBSWY3DPEHPK3PXP",
                createdAtMillis = 100L,
                updatedAtMillis = 300L,
            ),
            TotpEntry(
                uuid = "u2",
                issuer = "Cloudflare",
                accountName = "stay",
                secret = "JBSWY3DPEHPK3PXP",
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
                deletedAtMillis = 200L,
            ),
        )
        val outcome = TotpSyncMerge.merge(local, remote)
        val byId = outcome.entries.associateBy { it.uuid }
        assertEquals("new", byId.getValue("u1").accountName)
        assertTrue(byId.getValue("u2").deletedAtMillis == null)
        assertEquals(1, outcome.pulledCount)
        assertEquals(1, outcome.pushedCount)
    }
}
