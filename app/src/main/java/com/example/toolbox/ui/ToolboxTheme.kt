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
    BLUE,
    GREEN,
    ORANGE,
    PURPLE,
}

@Composable
fun ToolboxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.DYNAMIC,
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

        accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        accentColor == AccentColor.DYNAMIC && darkTheme -> darkColorScheme()
        accentColor == AccentColor.DYNAMIC -> lightColorScheme()
        darkTheme -> darkColorScheme(primary = accentColor.primary(true))
        else -> lightColorScheme(primary = accentColor.primary(false))
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

fun AccentColor.swatch(): Color = when (this) {
    AccentColor.DYNAMIC -> Color(0xFF6750A4)
    AccentColor.EVERFOREST -> Color(0xFFA7C080)
    AccentColor.BLUE -> Color(0xFF415F91)
    AccentColor.GREEN -> Color(0xFF386A3F)
    AccentColor.ORANGE -> Color(0xFF8B5000)
    AccentColor.PURPLE -> Color(0xFF735184)
}

private fun AccentColor.primary(dark: Boolean): Color = when (this) {
    AccentColor.DYNAMIC -> if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
    AccentColor.EVERFOREST -> if (dark) Color(0xFFA7C080) else Color(0xFF4F6452)
    AccentColor.BLUE -> if (dark) Color(0xFFAAC7FF) else Color(0xFF415F91)
    AccentColor.GREEN -> if (dark) Color(0xFFA0D5A2) else Color(0xFF386A3F)
    AccentColor.ORANGE -> if (dark) Color(0xFFFFB95A) else Color(0xFF8B5000)
    AccentColor.PURPLE -> if (dark) Color(0xFFE5B9F2) else Color(0xFF735184)
}

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
    primary = Color(0xFF4F6452),
    onPrimary = Color(0xFFFDF6E3),
    primaryContainer = Color(0xFFD3DEC8),
    onPrimaryContainer = Color(0xFF232A2E),
    secondary = Color(0xFF3A94C5),
    onSecondary = Color(0xFFFDF6E3),
    secondaryContainer = Color(0xFFD0E6F0),
    onSecondaryContainer = Color(0xFF232A2E),
    tertiary = Color(0xFFDF69BA),
    onTertiary = Color(0xFFFDF6E3),
    background = Color(0xFFFDF6E3),
    onBackground = Color(0xFF5C6A72),
    surface = Color(0xFFF4F0D9),
    onSurface = Color(0xFF5C6A72),
    surfaceVariant = Color(0xFFEFEBD4),
    onSurfaceVariant = Color(0xFF7C8B94),
    surfaceContainerLowest = Color(0xFFF8F1DC),
    surfaceContainerLow = Color(0xFFF4ECD6),
    surfaceContainer = Color(0xFFEFEBD4),
    surfaceContainerHigh = Color(0xFFE6E2CC),
    surfaceContainerHighest = Color(0xFFDFDBC5),
    outline = Color(0xFFDFDBC5),
    outlineVariant = Color(0xFFEFEBD4),
)
