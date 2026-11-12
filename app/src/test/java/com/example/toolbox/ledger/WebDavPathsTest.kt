package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavPathsTest {

    @Test
    fun toolFoldersAreSeparateIdempotentAndKeepEncodedParents() {
        val base = "https://h/a%20b/"
        val ledger = WebDavPaths.toolFolderUrl(base, BackupTool.LEDGER)
        assertEquals("https://h/a%20b/Toolbox/ledger/", ledger)
        assertEquals(ledger, WebDavPaths.toolFolderUrl(ledger, BackupTool.LEDGER))
        assertEquals("https://h/a%20b/Toolbox/totp/", WebDavPaths.toolFolderUrl(ledger, BackupTool.TOTP))
        assertEquals(listOf("https://h/a%20b/Toolbox/", base),
            WebDavPaths.legacyFolderUrls(base, BackupTool.LEDGER))
    }

    @Test
    fun testNormalizeFolderUrlAddsTrailingSlash() {
        assertEquals(
            "https://h/dav/Toolbox/",
            WebDavPaths.normalizeFolderUrl("https://h/dav/Toolbox"),
        )
    }

    @Test
    fun testNormalizeFolderUrlCaseInsensitiveScheme() {
        assertEquals(
            "HTTPS://h/x/",
            WebDavPaths.normalizeFolderUrl("HTTPS://h/x/"),
        )
    }

    @Test
    fun testNormalizeFolderUrlTrimsWhitespace() {
        assertEquals(
            "https://h/x/",
            WebDavPaths.normalizeFolderUrl(" https://h/x "),
        )
    }

    @Test
    fun testNormalizeFolderUrlRejectsNonHttps() {
        assertNull(WebDavPaths.normalizeFolderUrl("http://h/x"))
        assertNull(WebDavPaths.normalizeFolderUrl("ftp://h"))
    }

    @Test
    fun testNormalizeFolderUrlRejectsEmptyAndMissingHost() {
        assertNull(WebDavPaths.normalizeFolderUrl(""))
        assertNull(WebDavPaths.normalizeFolderUrl("   "))
        assertNull(WebDavPaths.normalizeFolderUrl("https://"))
    }

    @Test
    fun testNormalizeFolderUrlEncodesNonAscii() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/%E8%AE%B0%E8%B4%A6/",
            WebDavPaths.normalizeFolderUrl("https://dav.jianguoyun.com/dav/记账"),
        )
    }

    @Test
    fun testNormalizeFolderUrlEncodesSpaces() {
        assertEquals(
            "https://h/My%20Ledger/",
            WebDavPaths.normalizeFolderUrl("https://h/My Ledger"),
        )
    }

    @Test
    fun testNormalizeFolderUrlKeepsExistingEncoding() {
        assertEquals(
            "https://h/a%20b/",
            WebDavPaths.normalizeFolderUrl("https://h/a%20b/"),
        )
    }

    @Test
    fun testToolboxFolderUrlAddsSubfolder() {
        assertEquals(
            "https://h/dav/Toolbox/",
            WebDavPaths.toolboxFolderUrl("https://h/dav/"),
        )
        assertEquals(
            "https://h/Toolbox/",
            WebDavPaths.toolboxFolderUrl("https://h"),
        )
    }

    @Test
    fun testToolboxFolderUrlIsIdempotent() {
        assertEquals(
            "https://h/dav/Toolbox/",
            WebDavPaths.toolboxFolderUrl("https://h/dav/Toolbox/"),
        )
        assertEquals(
            "https://h/dav/toolbox/",
            WebDavPaths.toolboxFolderUrl("https://h/dav/toolbox/"),
        )
    }

    @Test
    fun testFileUrlJoinsAndEncodes() {
        assertEquals(
            "https://h/dav/ledger.json",
            WebDavPaths.fileUrl("https://h/dav/", "ledger.json"),
        )
        assertEquals(
            "https://h/dav/a%20b.json",
            WebDavPaths.fileUrl("https://h/dav/", "a b.json"),
        )
    }

    @Test
    fun testBasicAuthHeaderNonAsciiPassword() {
        assertEquals(
            "Basic dTpww6Rzcw==",
            WebDavPaths.basicAuthHeader("u", "päss"),
        )
    }
}
