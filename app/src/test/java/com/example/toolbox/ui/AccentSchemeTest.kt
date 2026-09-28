package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentSchemeTest {

    @Test
    fun `parseHexColor accepts six hex digits with or without hash`() {
        assertEquals(0xFF9800, parseHexColor("FF9800"))
        assertEquals(0xFF9800, parseHexColor("#ff9800"))
        assertEquals(0x000000, parseHexColor("#000000"))
    }

    @Test
    fun `parseHexColor rejects malformed input`() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor("12345"))
        assertNull(parseHexColor("GGGGGG"))
        assertNull(parseHexColor("#FF98001"))
    }

    @Test
    fun `palette differs between seeds`() {
        assertNotEquals(
            accentPalette(0xF44336, dark = false).primary,
            accentPalette(0x2196F3, dark = false).primary,
        )
    }

    @Test
    fun `palette differs between light and dark`() {
        assertNotEquals(
            accentPalette(0x6750A4, dark = false).primary,
            accentPalette(0x6750A4, dark = true).primary,
        )
    }

    @Test
    fun `light theme primary is darker than its container`() {
        val palette = accentPalette(0xFF9800, dark = false)
        assertTrue(luminance(palette.primary) < luminance(palette.primaryContainer))
    }

    @Test
    fun `dark theme primary is lighter than its container`() {
        val palette = accentPalette(0xFF9800, dark = true)
        assertTrue(luminance(palette.primary) > luminance(palette.primaryContainer))
    }

    @Test
    fun `gray seed still produces a tinted accent`() {
        val gray = accentPalette(0x808080, dark = false)
        assertNotEquals(0xFF808080.toInt(), gray.primary)
    }

    private fun luminance(argb: Int): Float {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
