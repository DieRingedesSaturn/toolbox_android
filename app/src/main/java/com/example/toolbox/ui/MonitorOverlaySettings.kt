package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.toolbox.monitor.MonitorOverlayTheme
import com.example.toolbox.monitor.MonitorOverlayThemeHelper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayAppearanceCard(
    strings: ToolboxStrings,
    accentColor: AccentColor,
    customAccentRgb: Int,
    selectedTheme: MonitorOverlayTheme,
    customColor: Int,
    opacity: Float,
    onThemeChange: (MonitorOverlayTheme) -> Unit,
    onCustomColorChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    val colors = remember(selectedTheme, customColor, opacity) {
        MonitorOverlayThemeHelper.resolveColors(selectedTheme, customColor, opacity)
    }

    InfoCard(strings.overlayAppearanceTitle) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonitorOverlayTheme.entries.forEach { theme ->
                ToolboxFilterChip(
                    selected = theme == selectedTheme,
                    onClick = { onThemeChange(theme) },
                    label = {
                        Text(
                            when (theme) {
                                MonitorOverlayTheme.DARK -> strings.overlayThemeDark
                                MonitorOverlayTheme.LIGHT -> strings.overlayThemeLight
                                MonitorOverlayTheme.EVERFOREST -> strings.overlayThemeEverforest
                                MonitorOverlayTheme.SAGE -> strings.overlayThemeSage
                                MonitorOverlayTheme.BLACK -> strings.overlayThemeBlack
                                MonitorOverlayTheme.CUSTOM -> strings.overlayThemeCustom
                            },
                        )
                    },
                    leadingIcon = {
                        val previewColor = when (theme) {
                            MonitorOverlayTheme.DARK -> Color(0xFF1E1E1E)
                            MonitorOverlayTheme.LIGHT -> Color(0xFFFFFFFF)
                            MonitorOverlayTheme.EVERFOREST -> Color(0xFF2D353B)
                            MonitorOverlayTheme.SAGE -> Color(0xFF232C27)
                            MonitorOverlayTheme.BLACK -> Color(0xFF000000)
                            MonitorOverlayTheme.CUSTOM -> Color(customColor or (0xFF shl 24))
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(previewColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                        )
                    },
                )
            }
        }

        if (selectedTheme == MonitorOverlayTheme.CUSTOM) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.overlayPresetColorsTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MonitorOverlayThemeHelper.PRESET_CUSTOM_COLORS.forEach { colorInt ->
                        val isSelected = (customColor and 0x00FFFFFF) == (colorInt and 0x00FFFFFF)
                        val color = Color(colorInt or (0xFF shl 24))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { onCustomColorChange(colorInt) },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.overlayOpacityTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${(opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.0f..1.0f,
                steps = 19,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings.overlayPreviewTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(colors.backgroundColor))
                    .border(1.dp, Color(colors.gridColor), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Toolbox",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "×",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CPU",
                            color = Color(colors.primaryTextColor),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "32%",
                            color = Color(colors.secondaryTextColor),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                        )
                    }

                    MonitorSparkline(
                        values = PREVIEW_SPARK,
                        color = accentColor.swatch(customAccentRgb),
                        trackColor = Color(colors.gridColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )
                }
            }
        }
    }
}

private val PREVIEW_SPARK = listOf(
    42f, 38f, 55f, 48f, 62f, 44f, 58f, 51f, 47f, 53f, 49f, 56f,
)
