package com.example.toolbox.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {
    @Test
    fun rfc6238TestVectorsSha1() {
        // RFC 6238 Appendix B: ASCII secret "12345678901234567890" -> Base32:
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val entry8 = TotpEntry(
            uuid = "u1",
            issuer = "RFC6238",
            accountName = "test",
            secret = secret,
            algorithm = TotpAlgorithm.SHA1,
            digits = 8,
            period = 30,
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
        )
        assertEquals("94287082", TotpGenerator.generateCode(entry8, 59_000L))
        assertEquals("07081804", TotpGenerator.generateCode(entry8, 1_111_111_109_000L))
        assertEquals("14050471", TotpGenerator.generateCode(entry8, 1_111_111_111_000L))
        assertEquals("89005924", TotpGenerator.generateCode(entry8, 1_234_567_890_000L))
    }

    @Test
    fun parseOtpauthUriExtractsIssuerAndAccountAndParams() {
        val uri = "otpauth://totp/Cloudflare:ops%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Cloudflare&algorithm=SHA256&digits=8&period=60"
        val parsed = TotpGenerator.parseOtpauthUri(uri, nowMillis = 100L)
        assertEquals("Cloudflare", parsed.issuer)
        assertEquals("ops@example.com", parsed.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", parsed.secret)
        assertEquals(TotpAlgorithm.SHA256, parsed.algorithm)
        assertEquals(8, parsed.digits)
        assertEquals(60, parsed.period)

        val rebuilt = TotpGenerator.buildOtpauthUri(parsed)
        val reparsed = TotpGenerator.parseOtpauthUri(rebuilt)
        assertEquals(parsed.issuer, reparsed.issuer)
        assertEquals(parsed.accountName, reparsed.accountName)
        assertEquals(parsed.secret, reparsed.secret)
        assertEquals(parsed.algorithm, reparsed.algorithm)
        assertEquals(parsed.digits, reparsed.digits)
        assertEquals(parsed.period, reparsed.period)
    }

    @Test
    fun serviceBadgeDistinguishesKnownServices() {
        val cf = TotpGenerator.serviceBadge("Cloudflare", "user@example.com")
        val gh = TotpGenerator.serviceBadge("GitHub", "octocat")
        assertEquals("CF", cf.first)
        assertEquals("GH", gh.first)
        assertTrue(cf.second != gh.second)
    }
}
