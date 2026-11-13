package com.example.toolbox.totp

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

enum class TotpVaultFailure {
    PASSPHRASE_TOO_SHORT,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    WRONG_PASSPHRASE_OR_CORRUPTED,
    CRYPTO_ERROR,
}

class TotpVaultException(
    val failure: TotpVaultFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

data class TotpMergeResult(
    val entries: List<TotpEntry>,
    val pulledCount: Int,
    val pushedCount: Int,
)

object TotpSyncMerge {
    fun merge(local: List<TotpEntry>, remote: List<TotpEntry>): TotpMergeResult {
        val result = com.example.toolbox.ledger.LastWriteWins.merge(
            local,
            remote,
            id = { it.uuid },
            updatedAt = { it.updatedAtMillis },
            sort = { items ->
                items.sortedWith(
                    compareBy<TotpEntry> { it.issuer.lowercase() }
                        .thenBy { it.accountName.lowercase() }
                        .thenBy { it.createdAtMillis },
                )
            },
        )
        return TotpMergeResult(
            entries = result.items,
            pulledCount = result.pulledCount,
            pushedCount = result.pushedCount,
        )
    }
}

object TotpVaultCrypto {
    const val FORMAT_TOOLBOX = "toolbox-totp-vault"
    const val FORMAT_DORIYA = "doriya-vault"
    const val VERSION = 1
    const val AAD_TOOLBOX = "toolbox-totp-vault:v1"
    const val AAD_DORIYA = "doriya-vault:v1"
    const val KDF_NAME = "PBKDF2-SHA256"
    const val CIPHER_NAME = "AES-256-GCM"
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 5_000_000
    const val MIN_PASSPHRASE_LENGTH = 8
    const val MAX_BYTES = 1 * 1024 * 1024

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    private val random = SecureRandom()

    fun validatePassphrase(passphrase: String): Boolean =
        passphrase.codePointCount(0, passphrase.length) >= MIN_PASSPHRASE_LENGTH

    fun encryptVault(
        entries: List<TotpEntry>,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        return encryptPayload(serializeEntriesJson(entries, nowMillis), passphrase,
            FORMAT_TOOLBOX, AAD_TOOLBOX, iterations)
    }

    fun encryptPayload(
        plaintextJson: String,
        passphrase: String,
        format: String,
        aad: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        if (!validatePassphrase(passphrase)) {
            throw TotpVaultException(TotpVaultFailure.PASSPHRASE_TOO_SHORT)
        }
        val safeIterations = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val plaintext = plaintextJson.toByteArray(Charsets.UTF_8)
        val ciphertext = try {
            val key = deriveKey(passphrase, salt, safeIterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            throw TotpVaultException(TotpVaultFailure.CRYPTO_ERROR, e)
        }
        val envelope = JSONObject()
            .put("format", format)
            .put("version", VERSION)
            .put(
                "kdf",
                JSONObject()
                    .put("name", KDF_NAME)
                    .put("iterations", safeIterations)
                    .put("salt", Base64.getEncoder().encodeToString(salt)),
            )
            .put(
                "cipher",
                JSONObject()
                    .put("name", CIPHER_NAME)
                    .put("iv", Base64.getEncoder().encodeToString(iv))
                    .put("data", Base64.getEncoder().encodeToString(ciphertext)),
            )
        return envelope.toString(2)
    }

    fun decryptVault(envelopeJson: String, passphrase: String): List<TotpEntry> {
        return parseEntriesJson(decryptPayload(envelopeJson, passphrase,
            mapOf(FORMAT_TOOLBOX to AAD_TOOLBOX, FORMAT_DORIYA to AAD_DORIYA), MAX_BYTES))
    }

    fun decryptPayload(
        envelopeJson: String,
        passphrase: String,
        formats: Map<String, String>,
        maxBytes: Int,
    ): String {
        if (passphrase.isEmpty()) {
            throw TotpVaultException(TotpVaultFailure.PASSPHRASE_TOO_SHORT)
        }
        if (envelopeJson.toByteArray(Charsets.UTF_8).size > maxBytes) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        val root = try {
            JSONObject(envelopeJson)
        } catch (e: Exception) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT, e)
        }
        val format = root.optString("format")
        val aad = formats[format] ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        val version = root.optInt("version", -1)
        if (version != VERSION) {
            throw TotpVaultException(TotpVaultFailure.UNSUPPORTED_VERSION)
        }
        val kdf = root.optJSONObject("kdf")
            ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        if (kdf.optString("name") != KDF_NAME) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        val iterations = kdf.optInt("iterations", -1)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        val salt = decodeBase64(kdf.optString("salt"), SALT_BYTES)
        val cipherObj = root.optJSONObject("cipher")
            ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        if (cipherObj.optString("name") != CIPHER_NAME) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        val iv = decodeBase64(cipherObj.optString("iv"), IV_BYTES)
        val data = decodeBase64(cipherObj.optString("data"), null)
        if (data.size < GCM_TAG_BYTES) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        val decryptedBytes = try {
            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw TotpVaultException(TotpVaultFailure.WRONG_PASSPHRASE_OR_CORRUPTED, e)
        } catch (e: Exception) {
            throw TotpVaultException(TotpVaultFailure.WRONG_PASSPHRASE_OR_CORRUPTED, e)
        }
        return String(decryptedBytes, Charsets.UTF_8)
    }

    internal fun serializeEntriesJson(
        entries: List<TotpEntry>,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
                .put("id", entry.uuid)
                .put("issuer", entry.issuer.trim())
                .put("account", entry.accountName.trim())
                .put("secret", TotpGenerator.normalizeBase32(entry.secret))
                .put("algorithm", entry.algorithm.wireName)
                .put("digits", entry.digits)
                .put("period", entry.period)
                .put("createdAtMillis", entry.createdAtMillis)
                .put("updatedAtMillis", entry.updatedAtMillis)
            if (entry.deletedAtMillis != null) {
                obj.put("deletedAtMillis", entry.deletedAtMillis)
            }
            array.put(obj)
        }
        return JSONObject()
            .put("version", VERSION)
            .put("updatedAtMillis", nowMillis)
            .put("accounts", array)
            .toString()
    }

    internal fun parseEntriesJson(rawJson: String): List<TotpEntry> {
        val doc = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT, e)
        }
        if (doc.optInt("version", -1) != VERSION) {
            throw TotpVaultException(TotpVaultFailure.UNSUPPORTED_VERSION)
        }
        val accounts = doc.optJSONArray("accounts")
            ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        val seenIds = HashSet<String>()
        return buildList {
            for (i in 0 until accounts.length()) {
                val item = accounts.optJSONObject(i)
                    ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
                val id = item.optString("id").trim()
                if (id.isEmpty() || !seenIds.add(id)) {
                    throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
                }
                val issuer = item.optString("issuer").trim()
                val accountName = item.optString("account").trim()
                val secret = try {
                    TotpGenerator.normalizeBase32(item.optString("secret"))
                } catch (e: Exception) {
                    throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT, e)
                }
                val algorithm = TotpAlgorithm.fromString(item.optString("algorithm"))
                    ?: throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
                val digits = item.optInt("digits", 6)
                val period = item.optInt("period", 30)
                if (digits !in TotpGenerator.SUPPORTED_DIGITS || period !in TotpGenerator.SUPPORTED_PERIODS) {
                    throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
                }
                val createdAtMillis = parseTimeMillis(item, "createdAtMillis", "createdAt")
                val updatedAtMillis = parseTimeMillis(item, "updatedAtMillis", "updatedAt")
                    .coerceAtLeast(createdAtMillis)
                val deletedAtMillis = if (item.has("deletedAtMillis") && !item.isNull("deletedAtMillis")) {
                    item.optLong("deletedAtMillis")
                } else {
                    null
                }
                add(
                    TotpEntry(
                        uuid = id,
                        issuer = issuer,
                        accountName = accountName,
                        secret = secret,
                        algorithm = algorithm,
                        digits = digits,
                        period = period,
                        createdAtMillis = createdAtMillis,
                        updatedAtMillis = updatedAtMillis,
                        deletedAtMillis = deletedAtMillis,
                    ),
                )
            }
        }
    }

    private fun parseTimeMillis(obj: JSONObject, millisKey: String, isoKey: String): Long {
        if (obj.has(millisKey)) {
            return obj.optLong(millisKey, System.currentTimeMillis())
        }
        val iso = obj.optString(isoKey).trim()
        if (iso.isNotEmpty()) {
            runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()?.let { return it }
        }
        return System.currentTimeMillis()
    }

    private fun decodeBase64(value: String, expectedBytes: Int?): ByteArray {
        if (value.isBlank()) throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        val decoded = try {
            Base64.getDecoder().decode(value.trim())
        } catch (e: Exception) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT, e)
        }
        if (expectedBytes != null && decoded.size != expectedBytes) {
            throw TotpVaultException(TotpVaultFailure.INVALID_FORMAT)
        }
        return decoded
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val raw = factory.generateSecret(spec).encoded
            SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
