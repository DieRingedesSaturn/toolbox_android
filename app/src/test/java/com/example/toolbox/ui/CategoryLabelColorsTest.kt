package com.example.toolbox.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.toolbox.usage.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLabelColorsTest {
    @Test
    fun everyCategoryMeetsSmallTextContrastAcrossPresetsAndCustomSeeds() {
        val schemes = mutableListOf<Pair<String, ColorScheme>>(
            "Sage light" to sageLightColorScheme(), "Sage dark" to sageDarkColorScheme(),
            "Everforest light" to everforestLightColorScheme(), "Everforest dark" to everforestDarkColorScheme(),
            "Material light" to lightColorScheme(), "Material dark" to darkColorScheme(),
        )
        (ACCENT_PRESET_COLORS + listOf(0x000000, 0xFFFFFF, 0x808080)).forEach { seed ->
            schemes += "Custom $seed light" to seededColorScheme(seed, false)
            schemes += "Custom $seed dark" to seededColorScheme(seed, true)
        }
        schemes.forEach { (name, scheme) ->
            AppCategory.entries.forEach { category ->
                val colors = categoryLabelColors(Color(category.colorRgb), scheme.surfaceContainer, scheme.onSurfaceVariant)
                assertEquals("$name / $category needs an opaque local container", 1f, colors.container.alpha, 0f)
                assertTrue("$name / $category contrast is below 4.5:1", contrast(colors.content, colors.container) >= 4.5)
            }
        }
    }

    @Test
    fun previouslyFailingLightGameAndDarkShoppingLabelsChooseReadableText() {
        val light = sageLightColorScheme()
        val dark = everforestDarkColorScheme()
        assertEquals(Color.Black,
            categoryLabelColors(Color(AppCategory.GAME.colorRgb), light.surfaceContainer, light.onSurfaceVariant).content)
        assertEquals(Color.White,
            categoryLabelColors(Color(AppCategory.SHOPPING.colorRgb), dark.surfaceContainer, dark.onSurfaceVariant).content)
    }

    @Test
    fun translucentWallpaperSurfacesCannotChangeTheEvaluatedLabelBackground() {
        val colors = categoryLabelColors(Color(AppCategory.GAME.colorRgb), Color.White.copy(alpha = 0.1f), Color.Gray)
        assertEquals(1f, colors.container.alpha, 0f)
        assertTrue(contrast(colors.content, colors.container) >= 4.5)
    }

    @Test
    fun alreadyReadableThemeForegroundIsPreserved() {
        val scheme = sageDarkColorScheme()
        val colors = categoryLabelColors(Color(AppCategory.ALL.colorRgb), scheme.surfaceContainer, scheme.onSurfaceVariant)
        assertEquals(scheme.onSurfaceVariant, colors.content)
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
