package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SettingsScreen(
    strings: ToolboxStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    customAccentRgb: Int,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onCustomAccentChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            ToolboxTopBar(
                title = strings.settings,
                onBack = onBack,
                backLabel = strings.back,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard(title = strings.languageTitle) {
                    ChoiceStrip(
                        options = AppLanguage.entries.toList(),
                        selected = language,
                        label = { it.displayName },
                        onSelected = onLanguageChange,
                    )
                }
            }
            item {
                SettingsCard(title = strings.themeTitle) {
                    ChoiceStrip(
                        options = ThemeMode.entries.toList(),
                        selected = themeMode,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> strings.system
                                ThemeMode.LIGHT -> strings.light
                                ThemeMode.DARK -> strings.dark
                            }
                        },
                        onSelected = onThemeModeChange,
                    )
                }
            }
            item {
                SettingsCard(title = strings.accentTitle) {
                    ChoiceStrip(
                        options = AccentColor.entries.toList(),
                        selected = accentColor,
                        label = {
                            when (it) {
                                AccentColor.DYNAMIC -> strings.dynamic
                                AccentColor.EVERFOREST -> strings.everforest
                                AccentColor.CUSTOM -> strings.custom
                            }
                        },
                        leading = { color ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(color.swatch(customAccentRgb))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline
                                            .copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    ),
                            )
                        },
                        onSelected = onAccentColorChange,
                    )
                    when (accentColor) {
                        AccentColor.DYNAMIC -> Text(
                            text = strings.accentDynamicHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccentColor.CUSTOM -> CustomAccentPicker(
                            strings = strings,
                            customAccentRgb = customAccentRgb,
                            onCustomAccentChange = onCustomAccentChange,
                        )
                        AccentColor.EVERFOREST -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun <T> ChoiceStrip(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
                leadingIcon = leading?.let { content -> { content(option) } },
            )
        }
        Spacer(modifier = Modifier.size(1.dp))
    }
}

@Composable
private fun CustomAccentPicker(
    strings: ToolboxStrings,
    customAccentRgb: Int,
    onCustomAccentChange: (Int) -> Unit,
) {
    var hexInput by remember(customAccentRgb) {
        mutableStateOf("%06X".format(Locale.US, customAccentRgb and 0xFFFFFF))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ACCENT_PRESET_COLORS.forEach { colorInt ->
                val isSelected =
                    (customAccentRgb and 0x00FFFFFF) == (colorInt and 0x00FFFFFF)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt or (0xFF shl 24)))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { onCustomAccentChange(colorInt) },
                )
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input
                parseHexColor(input)?.let(onCustomAccentChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.accentHexLabel) },
            prefix = { Text("#") },
            singleLine = true,
            isError = parseHexColor(hexInput) == null,
            supportingText = {
                if (parseHexColor(hexInput) == null) {
                    Text(strings.accentHexError)
                }
            },
        )
    }
}
