package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackgroundTest {
    @Test
    fun mostlyBlueImageSamplesABlueSeed() {
        val pixels = IntArray(100) { index ->
            if (index % 10 == 0) 0xFF808080.toInt() else 0xFF2A4FBF.toInt()
        }
        val seed = AppBackground.dominantSeed(pixels)
        val red = (seed shr 16) and 0xFF
        val green = (seed shr 8) and 0xFF
        val blue = seed and 0xFF
        assertTrue("blue channel should dominate: $seed", blue > red && blue > green)
    }

    @Test
    fun greyImageStillReturnsAValidSeed() {
        val pixels = IntArray(64) { 0xFF9A9A9A.toInt() }
        val seed = AppBackground.dominantSeed(pixels)
        assertTrue("seed must be a 24-bit RGB value: $seed", seed in 0..0xFFFFFF)
    }

    @Test
    fun emptyInputFallsBackToMidGrey() {
        assertEquals(0x808080, AppBackground.dominantSeed(IntArray(0)))
    }

    @Test
    fun boxBlurPixelsSmoothsSharpContrastEdge() {
        val width = 16
        val height = 16
        val pixels = IntArray(width * height) { index ->
            if (index % width < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        AppBackground.boxBlurPixels(pixels, width, height, radius = 3, passes = 3)
        val centerLeftRed = (pixels[8 * width + 7] shr 16) and 0xFF
        val centerRightRed = (pixels[8 * width + 8] shr 16) and 0xFF
        assertTrue("left side of edge should brighten: $centerLeftRed", centerLeftRed in 10..200)
        assertTrue("right side of edge should darken: $centerRightRed", centerRightRed in 55..245)
        assertTrue("left should remain darker than right", centerLeftRed < centerRightRed)
    }
}
