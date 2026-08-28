package com.example.toolbox.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorOverlayThemeTest {

    @Test
    fun darkThemeResolvesDarkBackgroundAndLightText() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.DARK,
            opacity = 0.80f,
        )
        // 0.8 * 255 = 204 = 0xCC
        assertEquals(0xCC1E1E1E.toInt(), colors.backgroundColor)
        assertEquals(0xFFF0F2F5.toInt(), colors.primaryTextColor)
        assertEquals(0xFFA0A6AC.toInt(), colors.secondaryTextColor)
        assertEquals(0x26FFFFFF, colors.gridColor)
    }

    @Test
    fun lightThemeResolvesLightBackgroundAndDarkText() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.LIGHT,
            opacity = 0.80f,
        )
        assertEquals(0xCCFFFFFF.toInt(), colors.backgroundColor)
        assertEquals(0xFF202124.toInt(), colors.primaryTextColor)
        assertEquals(0xFF5F6368.toInt(), colors.secondaryTextColor)
        assertEquals(0x3D303038, colors.gridColor)
    }

    @Test
    fun everforestThemeResolvesEverforestPaletteColors() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.EVERFOREST,
            opacity = 0.85f,
        )
        // 0.85 * 255 = 217 = 0xD9
        assertEquals(0xD92D353B.toInt(), colors.backgroundColor)
        assertEquals(0xFFD3C6AA.toInt(), colors.primaryTextColor)
        assertEquals(0xFF9DA9A0.toInt(), colors.secondaryTextColor)
        assertEquals(0x2AD3C6AA, colors.gridColor)
    }

    @Test
    fun blackThemeResolvesPureBlackBackgroundAndWhiteText() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.BLACK,
            opacity = 0.90f,
        )
        // 0.90 * 255 = 230 = 0xE6
        assertEquals(0xE6000000.toInt(), colors.backgroundColor)
        assertEquals(0xFFFFFFFF.toInt(), colors.primaryTextColor)
        assertEquals(0xFFB0B0B0.toInt(), colors.secondaryTextColor)
        assertEquals(0x33FFFFFF, colors.gridColor)
    }

    @Test
    fun customDarkColorAdaptsToLightText() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.CUSTOM,
            customRgb = 0x121E2C, // Deep Navy
            opacity = 0.80f,
        )
        assertEquals(0xCC121E2C.toInt(), colors.backgroundColor)
        assertEquals(0xFFF0F2F5.toInt(), colors.primaryTextColor)
        assertEquals(0xFFA0A6AC.toInt(), colors.secondaryTextColor)
        assertEquals(0x26FFFFFF, colors.gridColor)
    }

    @Test
    fun customLightColorAdaptsToDarkText() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.CUSTOM,
            customRgb = 0xF4F0D9, // Everforest Light Cream
            opacity = 0.80f,
        )
        assertEquals(0xCCF4F0D9.toInt(), colors.backgroundColor)
        assertEquals(0xFF202124.toInt(), colors.primaryTextColor)
        assertEquals(0xFF5F6368.toInt(), colors.secondaryTextColor)
        assertEquals(0x3D303038, colors.gridColor)
    }

    @Test
    fun isDarkCorrectlyIdentifiesLuminance() {
        assertTrue(MonitorOverlayThemeHelper.isDark(0x000000))
        assertTrue(MonitorOverlayThemeHelper.isDark(0x1E1E1E))
        assertTrue(MonitorOverlayThemeHelper.isDark(0x2D353B)) // Everforest Dark
        assertTrue(MonitorOverlayThemeHelper.isDark(0x121E2C)) // Deep Navy

        assertFalse(MonitorOverlayThemeHelper.isDark(0xFFFFFF))
        assertFalse(MonitorOverlayThemeHelper.isDark(0xF4F0D9)) // Everforest Cream
        assertFalse(MonitorOverlayThemeHelper.isDark(0xE0E0E0))
    }

    @Test
    fun opacityIsClampedBetweenMinimumAndMaximum() {
        val zeroOpacity = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.DARK,
            opacity = 0.0f,
        )
        // Min 0.0f -> alpha = 0 (100% transparent)
        val alphaZero = (zeroOpacity.backgroundColor ushr 24) and 0xFF
        assertEquals(0, alphaZero)

        val negativeOpacity = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.DARK,
            opacity = -0.5f,
        )
        val alphaNegative = (negativeOpacity.backgroundColor ushr 24) and 0xFF
        assertEquals(0, alphaNegative)

        val tooHigh = MonitorOverlayThemeHelper.resolveColors(
            theme = MonitorOverlayTheme.DARK,
            opacity = 1.5f,
        )
        // Max clamped to 1.0 -> alpha = 255 = 0xFF
        val alphaTooHigh = (tooHigh.backgroundColor ushr 24) and 0xFF
        assertEquals(255, alphaTooHigh)
    }
}
