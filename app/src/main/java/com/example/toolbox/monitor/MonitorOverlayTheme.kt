package com.example.toolbox.monitor

import kotlin.math.roundToInt

enum class MonitorOverlayTheme {
    DARK,
    LIGHT,
    EVERFOREST,
    BLACK,
    CUSTOM,
}

data class MonitorOverlayColors(
    val backgroundColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val gridColor: Int,
)

object MonitorOverlayThemeHelper {
    const val DEFAULT_CUSTOM_COLOR_RGB = 0x2D353B
    const val DEFAULT_OPACITY = 0.80f

    val PRESET_CUSTOM_COLORS = listOf(
        0x2D353B, // Everforest Dark
        0x3A4B3D, // Everforest Green
        0xF4F0D9, // Everforest Cream
        0x121E2C, // Deep Navy
        0x1A233A, // Midnight Blue
        0x0F2E1E, // Emerald Dark
        0x2A1532, // Plum Dark
        0x321418, // Wine Red
        0x252526, // Warm Charcoal
        0x283038, // Slate Gray
        0xFDFDFD, // Crisp White
    )

    fun resolveColors(
        theme: MonitorOverlayTheme,
        customRgb: Int = DEFAULT_CUSTOM_COLOR_RGB,
        opacity: Float = DEFAULT_OPACITY,
    ): MonitorOverlayColors {
        val safeOpacity = opacity.coerceIn(0.0f, 1.0f)
        val alpha = (safeOpacity * 255).roundToInt()

        return when (theme) {
            MonitorOverlayTheme.DARK -> {
                MonitorOverlayColors(
                    backgroundColor = (alpha shl 24) or 0x1E1E1E,
                    primaryTextColor = 0xFFF0F2F5.toInt(),
                    secondaryTextColor = 0xFFA0A6AC.toInt(),
                    gridColor = 0x26FFFFFF,
                )
            }

            MonitorOverlayTheme.LIGHT -> {
                MonitorOverlayColors(
                    backgroundColor = (alpha shl 24) or 0xFFFFFF,
                    primaryTextColor = 0xFF202124.toInt(),
                    secondaryTextColor = 0xFF5F6368.toInt(),
                    gridColor = 0x3D303038,
                )
            }

            MonitorOverlayTheme.EVERFOREST -> {
                MonitorOverlayColors(
                    backgroundColor = (alpha shl 24) or 0x2D353B,
                    primaryTextColor = 0xFFD3C6AA.toInt(),
                    secondaryTextColor = 0xFF9DA9A0.toInt(),
                    gridColor = 0x2AD3C6AA,
                )
            }

            MonitorOverlayTheme.BLACK -> {
                MonitorOverlayColors(
                    backgroundColor = (alpha shl 24) or 0x000000,
                    primaryTextColor = 0xFFFFFFFF.toInt(),
                    secondaryTextColor = 0xFFB0B0B0.toInt(),
                    gridColor = 0x33FFFFFF,
                )
            }

            MonitorOverlayTheme.CUSTOM -> {
                val cleanRgb = customRgb and 0x00FFFFFF
                val bg = (alpha shl 24) or cleanRgb
                val isDark = isDark(cleanRgb)
                if (isDark) {
                    MonitorOverlayColors(
                        backgroundColor = bg,
                        primaryTextColor = 0xFFF0F2F5.toInt(),
                        secondaryTextColor = 0xFFA0A6AC.toInt(),
                        gridColor = 0x26FFFFFF,
                    )
                } else {
                    MonitorOverlayColors(
                        backgroundColor = bg,
                        primaryTextColor = 0xFF202124.toInt(),
                        secondaryTextColor = 0xFF5F6368.toInt(),
                        gridColor = 0x3D303038,
                    )
                }
            }
        }
    }

    fun isDark(rgb: Int): Boolean {
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 128
    }
}
