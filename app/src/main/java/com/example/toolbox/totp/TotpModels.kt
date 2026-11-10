package com.example.toolbox.totp

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

enum class TotpAlgorithm(val wireName: String, val uriName: String, val macAlgorithm: String) {
    SHA1("SHA-1", "SHA1", "HmacSHA1"),
    SHA256("SHA-256", "SHA256", "HmacSHA256"),
    SHA512("SHA-512", "SHA512", "HmacSHA512"),
    ;

    companion object {
        fun fromString(raw: String?): TotpAlgorithm? {
            if (raw.isNullOrBlank()) return SHA1
            return when (raw.trim().uppercase(Locale.US).replace("-", "")) {
                "SHA1" -> SHA1
                "SHA256" -> SHA256
                "SHA512" -> SHA512
                else -> null
            }
        }
    }
}

class TotpInputException(message: String) : IllegalArgumentException(message)

data class TotpEntry(
    val uuid: String = UUID.randomUUID().toString(),
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
    val deletedAtMillis: Long? = null,
) {
    val displayTitle: String
        get() = issuer.ifBlank { accountName.ifBlank { "TOTP" } }

    val displaySubtitle: String
        get() = if (issuer.isNotBlank() && accountName.isNotBlank() && accountName != issuer) {
            accountName
        } else {
            ""
        }
}

object TotpGenerator {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val SUPPORTED_DIGITS = listOf(6, 8)
    val SUPPORTED_PERIODS = listOf(15, 30, 60)

    fun normalizeBase32(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\s-]"), "")
            .trimEnd('=')
            .uppercase(Locale.US)
        if (cleaned.isEmpty()) {
            throw TotpInputException("EMPTY_SECRET")
        }
        if (!cleaned.all { it in 'A'..'Z' || it in '2'..'7' }) {
            throw TotpInputException("INVALID_BASE32")
        }
        return cleaned
    }

    fun base32Decode(raw: String): ByteArray {
        val normalized = normalizeBase32(raw)
        val out = ByteArray(normalized.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (ch in normalized) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) throw TotpInputException("INVALID_BASE32")
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out[outIndex++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
                buffer = if (bitsLeft == 0) 0 else buffer and ((1 shl bitsLeft) - 1)
            }
        }
        if (outIndex == 0) {
            throw TotpInputException("SECRET_TOO_SHORT")
        }
        return if (outIndex == out.size) out else out.copyOf(outIndex)
    }

    fun generateCode(
        entry: TotpEntry,
        timestampMillis: Long = System.currentTimeMillis(),
    ): String = generateCode(
        secret = entry.secret,
        algorithm = entry.algorithm,
        digits = entry.digits,
        period = entry.period,
        timestampMillis = timestampMillis,
    )

    fun generateCode(
        secret: String,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        digits: Int = 6,
        period: Int = 30,
        timestampMillis: Long = System.currentTimeMillis(),
    ): String {
        require(digits in SUPPORTED_DIGITS) { "Unsupported digits: $digits" }
        require(period in SUPPORTED_PERIODS) { "Unsupported period: $period" }
        val keyBytes = base32Decode(secret)
        val counter = Math.floorDiv(timestampMillis / 1000L, period.toLong())
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(algorithm.macAlgorithm)
        mac.init(SecretKeySpec(keyBytes, algorithm.macAlgorithm))
        val hash = mac.doFinal(counterBytes)
        val offset = hash[hash.lastIndex].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val mod = if (digits == 8) 100_000_000L else 1_000_000L
        val code = (binary.toLong() and 0xFFFFFFFFL) % mod
        return code.toString().padStart(digits, '0')
    }

    fun formatDisplayCode(code: String): String = when (code.length) {
        6 -> "${code.substring(0, 3)} ${code.substring(3)}"
        8 -> "${code.substring(0, 4)} ${code.substring(4)}"
        else -> code
    }

    fun remainingSeconds(
        period: Int,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Int {
        val safePeriod = period.coerceAtLeast(1)
        val elapsedSec = Math.floorMod(timestampMillis / 1000L, safePeriod.toLong()).toInt()
        return safePeriod - elapsedSec
    }

    fun progress(
        period: Int,
        timestampMillis: Long = System.currentTimeMillis(),
    ): Float {
        val safePeriod = (period.coerceAtLeast(1) * 1000L)
        val elapsedMillis = Math.floorMod(timestampMillis, safePeriod)
        return (1f - elapsedMillis.toFloat() / safePeriod.toFloat()).coerceIn(0f, 1f)
    }

    fun parseOtpauthUri(raw: String, nowMillis: Long = System.currentTimeMillis()): TotpEntry {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw TotpInputException("INVALID_OTPAUTH_URI")
        if (!uri.scheme.equals("otpauth", ignoreCase = true)) {
            throw TotpInputException("INVALID_OTPAUTH_URI")
        }
        if (!uri.host.equals("totp", ignoreCase = true)) {
            throw TotpInputException("UNSUPPORTED_OTPAUTH_TYPE")
        }
        val params = parseQueryParams(uri.rawQuery)
        val secretRaw = params["secret"] ?: throw TotpInputException("EMPTY_SECRET")
        val secret = normalizeBase32(secretRaw)
        val rawPath = uri.rawPath.orEmpty().removePrefix("/")
        val decodedLabel = decodeComponent(rawPath)
        val queryIssuer = params["issuer"]?.trim().orEmpty()
        var issuer = queryIssuer
        var account = decodedLabel.trim()
        if (':' in decodedLabel) {
            val idx = decodedLabel.indexOf(':')
            val prefixIssuer = decodedLabel.substring(0, idx).trim()
            account = decodedLabel.substring(idx + 1).trim()
            if (issuer.isEmpty()) {
                issuer = prefixIssuer
            }
        }
        if (issuer.isNotEmpty() && account.startsWith("$issuer:", ignoreCase = true)) {
            account = account.substring(issuer.length + 1).trim()
        }
        val algorithm = TotpAlgorithm.fromString(params["algorithm"])
            ?: throw TotpInputException("UNSUPPORTED_ALGORITHM")
        val digits = params["digits"]?.toIntOrNull()?.takeIf { it in SUPPORTED_DIGITS }
            ?: if (params.containsKey("digits")) throw TotpInputException("UNSUPPORTED_DIGITS") else 6
        val period = params["period"]?.toIntOrNull()?.takeIf { it in SUPPORTED_PERIODS }
            ?: if (params.containsKey("period")) throw TotpInputException("UNSUPPORTED_PERIOD") else 30

        return TotpEntry(
            issuer = issuer,
            accountName = account.ifEmpty { issuer.ifEmpty { "Account" } },
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
    }

    fun buildOtpauthUri(entry: TotpEntry): String {
        val issuer = entry.issuer.trim()
        val account = entry.accountName.trim().ifEmpty { "Account" }
        val label = if (issuer.isNotEmpty()) {
            "${encodeComponent(issuer)}:${encodeComponent(account)}"
        } else {
            encodeComponent(account)
        }
        return buildString {
            append("otpauth://totp/")
            append(label)
            append("?secret=").append(normalizeBase32(entry.secret))
            if (issuer.isNotEmpty()) {
                append("&issuer=").append(encodeComponent(issuer))
            }
            append("&algorithm=").append(entry.algorithm.uriName)
            append("&digits=").append(entry.digits)
            append("&period=").append(entry.period)
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) decodeComponent(pair.substring(0, idx)) else decodeComponent(pair)
            val value = if (idx >= 0) decodeComponent(pair.substring(idx + 1)) else ""
            result[key.lowercase(Locale.US)] = value
        }
        return result
    }

    private fun decodeComponent(value: String): String =
        runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrDefault(value)

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private val BRAND_COLORS = listOf(
        0xFF3A7D44.toInt(),
        0xFF3A94C5.toInt(),
        0xFFD26A1B.toInt(),
        0xFF8C569B.toInt(),
        0xFF2B7A78.toInt(),
        0xFFC43E1C.toInt(),
        0xFF5C6BC0.toInt(),
        0xFF6D4C41.toInt(),
    )

    /** Returns (monogram, rgbColorInt) for visually distinguishing services like Cloudflare, GitHub, etc. */
    fun serviceBadge(issuer: String, accountName: String): Pair<String, Int> {
        val name = issuer.trim().ifEmpty { accountName.trim() }.ifEmpty { "2F" }
        val lower = name.lowercase(Locale.US)
        val knownColor: Int? = when {
            "cloudflare" in lower -> 0xFFF38020.toInt()
            "github" in lower -> 0xFF57606A.toInt()
            "gitlab" in lower -> 0xFFE24329.toInt()
            "google" in lower -> 0xFF4285F4.toInt()
            "microsoft" in lower || "azure" in lower -> 0xFF0078D4.toInt()
            "amazon" in lower || "aws" in lower -> 0xFFFF9900.toInt()
            "steam" in lower -> 0xFF1B2838.toInt()
            "discord" in lower -> 0xFF5865F2.toInt()
            "telegram" in lower -> 0xFF26A5E4.toInt()
            "apple" in lower || "icloud" in lower -> 0xFF555555.toInt()
            "bitwarden" in lower -> 0xFF175DDC.toInt()
            else -> null
        }
        val color = knownColor ?: BRAND_COLORS[abs(lower.hashCode()) % BRAND_COLORS.size]
        val words = name.split(Regex("[\\s._@-]+")).filter { it.isNotBlank() }
        val monogram = when {
            "cloudflare" in lower -> "CF"
            "github" in lower -> "GH"
            "gitlab" in lower -> "GL"
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.US)
            name.length >= 2 && name[0].code < 128 && name[1].code < 128 ->
                name.substring(0, 2).uppercase(Locale.US)
            else -> name.take(1).uppercase(Locale.US)
        }
        return monogram to color
    }
}
