package com.example.toolbox.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentColor {
    DYNAMIC,
    EVERFOREST,
    SAGE,
    CUSTOM,
}

@Composable
fun ToolboxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.DYNAMIC,
    customAccentRgb: Int = DEFAULT_CUSTOM_ACCENT_RGB,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkTheme },
        )
        onDispose { }
    }

    val colorScheme = when {
        accentColor == AccentColor.EVERFOREST && darkTheme -> everforestDarkColorScheme()
        accentColor == AccentColor.EVERFOREST -> everforestLightColorScheme()

        accentColor == AccentColor.SAGE && darkTheme -> sageDarkColorScheme()
        accentColor == AccentColor.SAGE -> sageLightColorScheme()

        accentColor == AccentColor.CUSTOM -> {
            val palette = accentPalette(customAccentRgb, darkTheme)
            if (darkTheme) {
                darkColorScheme(
                    primary = Color(palette.primary),
                    onPrimary = Color(palette.onPrimary),
                    primaryContainer = Color(palette.primaryContainer),
                    onPrimaryContainer = Color(palette.onPrimaryContainer),
                    secondary = Color(palette.secondary),
                    onSecondary = Color(palette.onSecondary),
                    secondaryContainer = Color(palette.secondaryContainer),
                    onSecondaryContainer = Color(palette.onSecondaryContainer),
                    tertiary = Color(palette.tertiary),
                    onTertiary = Color(palette.onTertiary),
                    tertiaryContainer = Color(palette.tertiaryContainer),
                    onTertiaryContainer = Color(palette.onTertiaryContainer),
                    outline = Color(0xFF49454F),
                    outlineVariant = Color(0xFF3B3740),
                )
            } else {
                lightColorScheme(
                    primary = Color(palette.primary),
                    onPrimary = Color(palette.onPrimary),
                    primaryContainer = Color(palette.primaryContainer),
                    onPrimaryContainer = Color(palette.onPrimaryContainer),
                    secondary = Color(palette.secondary),
                    onSecondary = Color(palette.onSecondary),
                    secondaryContainer = Color(palette.secondaryContainer),
                    onSecondaryContainer = Color(palette.onSecondaryContainer),
                    tertiary = Color(palette.tertiary),
                    onTertiary = Color(palette.onTertiary),
                    tertiaryContainer = Color(palette.tertiaryContainer),
                    onTertiaryContainer = Color(palette.onTertiaryContainer),
                    outline = Color(0xFFCAC4D0),
                    outlineVariant = Color(0xFFE7E0EC),
                )
            }
        }

        accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

fun AccentColor.swatch(customRgb: Int = DEFAULT_CUSTOM_ACCENT_RGB): Color = when (this) {
    AccentColor.DYNAMIC -> Color(0xFF6750A4)
    AccentColor.EVERFOREST -> Color(0xFFA7C080)
    AccentColor.SAGE -> Color(0xFF7FAF97)
    AccentColor.CUSTOM -> Color(customRgb or (0xFF shl 24))
}

private fun sageLightColorScheme() = lightColorScheme(
    primary = Color(0xFF345C70),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBD8E4),
    onPrimaryContainer = Color(0xFF17333D),
    secondary = Color(0xFF7FAF97),
    onSecondary = Color(0xFF17372A),
    secondaryContainer = Color(0xFFB7D5C9),
    onSecondaryContainer = Color(0xFF214033),
    tertiary = Color(0xFFA9706C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7B8B5),
    onTertiaryContainer = Color(0xFF402422),
    background = Color(0xFFD7D9C8),
    onBackground = Color(0xFF4F554F),
    surface = Color(0xFFCBD4BC),
    onSurface = Color(0xFF46514A),
    surfaceVariant = Color(0xFFC3CFB6),
    onSurfaceVariant = Color(0xFF566259),
    surfaceContainerLowest = Color(0xFFEEE7D5),
    surfaceContainerLow = Color(0xFFE1DFCF),
    surfaceContainer = Color(0xFFC9D3B9),
    surfaceContainerHigh = Color(0xFFBFCBB0),
    surfaceContainerHighest = Color(0xFFB5C3A6),
    outline = Color(0xFFB8C6AC),
    outlineVariant = Color(0xFFC3CFB6),
)

private fun sageDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF82B8CE),
    onPrimary = Color(0xFF0A2A35),
    primaryContainer = Color(0xFF2C4E5B),
    onPrimaryContainer = Color(0xFFC8E2EB),
    secondary = Color(0xFF91C0A8),
    onSecondary = Color(0xFF123528),
    secondaryContainer = Color(0xFF334D40),
    onSecondaryContainer = Color(0xFFC9E4D6),
    tertiary = Color(0xFFD7A9A5),
    onTertiary = Color(0xFF432522),
    tertiaryContainer = Color(0xFF5A3A37),
    onTertiaryContainer = Color(0xFFF1D8D4),
    background = Color(0xFF1E2420),
    onBackground = Color(0xFFD4DCCF),
    surface = Color(0xFF252E28),
    onSurface = Color(0xFFD8E1D3),
    surfaceVariant = Color(0xFF37433A),
    onSurfaceVariant = Color(0xFFAEBBB0),
    surfaceContainerLowest = Color(0xFF151A17),
    surfaceContainerLow = Color(0xFF202722),
    surfaceContainer = Color(0xFF2B352E),
    surfaceContainerHigh = Color(0xFF354239),
    surfaceContainerHighest = Color(0xFF405046),
    outline = Color(0xFF46564B),
    outlineVariant = Color(0xFF354239),
)

private fun everforestDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFA7C080),
    onPrimary = Color(0xFF232A2E),
    primaryContainer = Color(0xFF37423D),
    onPrimaryContainer = Color(0xFFD3C6AA),
    secondary = Color(0xFF83C092),
    onSecondary = Color(0xFF232A2E),
    secondaryContainer = Color(0xFF333E38),
    onSecondaryContainer = Color(0xFFD3C6AA),
    tertiary = Color(0xFFDBBC7F),
    onTertiary = Color(0xFF232A2E),
    tertiaryContainer = Color(0xFF453F32),
    onTertiaryContainer = Color(0xFFD3C6AA),
    background = Color(0xFF272E33),
    onBackground = Color(0xFFD3C6AA),
    surface = Color(0xFF2D353B),
    onSurface = Color(0xFFD3C6AA),
    surfaceVariant = Color(0xFF3D484D),
    onSurfaceVariant = Color(0xFF9DA9A0),
    surfaceContainerLowest = Color(0xFF1E2327),
    surfaceContainerLow = Color(0xFF272E33),
    surfaceContainer = Color(0xFF343F44),
    surfaceContainerHigh = Color(0xFF3D484D),
    surfaceContainerHighest = Color(0xFF475258),
    outline = Color(0xFF4F585E),
    outlineVariant = Color(0xFF3D484D),
)

private fun everforestLightColorScheme() = lightColorScheme(
    primary = Color(0xFF3A7D44),
    onPrimary = Color(0xFFFDF6E3),
    primaryContainer = Color(0xFFD3DEC8),
    onPrimaryContainer = Color(0xFF232A2E),
    secondary = Color(0xFF3A94C5),
    onSecondary = Color(0xFFFDF6E3),
    secondaryContainer = Color(0xFFD0E6F0),
    onSecondaryContainer = Color(0xFF232A2E),
    tertiary = Color(0xFFD26A1B),
    onTertiary = Color(0xFFFDF6E3),
    background = Color(0xFFFDF6E3),
    onBackground = Color(0xFF4C565C),
    surface = Color(0xFFF4F0D9),
    onSurface = Color(0xFF3C474D),
    surfaceVariant = Color(0xFFEFEBD4),
    onSurfaceVariant = Color(0xFF708089),
    surfaceContainerLowest = Color(0xFFF8F1DC),
    surfaceContainerLow = Color(0xFFF4ECD6),
    surfaceContainer = Color(0xFFEFEBD4),
    surfaceContainerHigh = Color(0xFFE6E2CC),
    surfaceContainerHighest = Color(0xFFDFDBC5),
    outline = Color(0xFFDFDBC5),
    outlineVariant = Color(0xFFEFEBD4),
)
