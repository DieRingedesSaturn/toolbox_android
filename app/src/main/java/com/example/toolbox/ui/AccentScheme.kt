package com.example.toolbox.ui

import kotlin.math.abs

/** Default seed for the custom accent theme (the old "Blue" preset). */
const val DEFAULT_CUSTOM_ACCENT_RGB = 0x415F91

/** Preset swatch seeds offered in Settings when the custom accent is picked. */
val ACCENT_PRESET_COLORS = listOf(
    0xF44336, // red
    0xE91E63, // pink
    0x9C27B0, // purple
    0x6750A4, // m3 baseline purple
    0x3F51B5, // indigo
    0x2196F3, // blue
    0x009688, // teal
    0x4CAF50, // green
    0xCDDC39, // lime
    0xFF9800, // orange
    0x795548, // brown
    0x607D8B, // slate
)

/**
 * Accent colors derived from a user-picked seed, in ARGB ints so the math
 * stays free of framework types and unit-testable.
 */
data class AccentPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
)

/**
 * Approximates a Material tonal scheme from [seedRgb] with plain HSV math:
 * the seed hue drives primary/secondary/container tones, and tertiary is a
 * 60° hue shift (the same shift M3 uses between primary and tertiary).
 * Surfaces stay neutral so text contrast is not affected by the pick.
 */
fun accentPalette(seedRgb: Int, dark: Boolean): AccentPalette {
    val (h, s, _) = hsvOf(seedRgb)
    // A floor keeps near-gray seeds visibly tinted instead of falling back
    // to monochrome.
    val sat = s.coerceAtLeast(0.25f)
    fun tone(satScale: Float, value: Float, hue: Float = h): Int =
        hsvToInt(hue, (sat * satScale).coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    return if (dark) {
        AccentPalette(
            primary = tone(0.55f, 0.85f),
            onPrimary = tone(0.7f, 0.2f),
            primaryContainer = tone(0.5f, 0.32f),
            onPrimaryContainer = tone(0.25f, 0.92f),
            secondary = tone(0.3f, 0.8f),
            onSecondary = tone(0.6f, 0.2f),
            secondaryContainer = tone(0.35f, 0.28f),
            onSecondaryContainer = tone(0.2f, 0.9f),
            tertiary = tone(0.4f, 0.8f, h + 60f),
            onTertiary = tone(0.6f, 0.2f, h + 60f),
            tertiaryContainer = tone(0.35f, 0.3f, h + 60f),
            onTertiaryContainer = tone(0.2f, 0.9f, h + 60f),
        )
    } else {
        AccentPalette(
            primary = tone(0.85f, 0.45f),
            onPrimary = tone(0.2f, 0.98f),
            primaryContainer = tone(0.35f, 0.92f),
            onPrimaryContainer = tone(0.8f, 0.22f),
            secondary = tone(0.4f, 0.5f),
            onSecondary = tone(0.2f, 0.98f),
            secondaryContainer = tone(0.2f, 0.93f),
            onSecondaryContainer = tone(0.6f, 0.25f),
            tertiary = tone(0.5f, 0.55f, h + 60f),
            onTertiary = tone(0.2f, 0.98f, h + 60f),
            tertiaryContainer = tone(0.25f, 0.93f, h + 60f),
            onTertiaryContainer = tone(0.5f, 0.3f, h + 60f),
        )
    }
}

/** Parse "RRGGBB" or "#RRGGBB" into 0xRRGGBB; null when malformed. */
fun parseHexColor(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (!digits.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return digits.toInt(16)
}

private fun hsvOf(rgb: Int): FloatArray {
    val r = ((rgb ushr 16) and 0xFF) / 255f
    val g = ((rgb ushr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> (((g - b) / d) % 6f + 6f) % 6f * 60f
        max == g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
}

private fun hsvToInt(h: Float, s: Float, v: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
    val m = v - c
    val (rr, gg, bb) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = rr + m
    val g = gg + m
    val b = bb + m
    fun chan(f: Float) = (f * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (chan(r) shl 16) or (chan(g) shl 8) or chan(b)
}
