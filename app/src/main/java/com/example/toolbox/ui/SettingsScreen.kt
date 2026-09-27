package com.example.toolbox.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    strings: ToolboxStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
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
                                AccentColor.BLUE -> strings.blue
                                AccentColor.GREEN -> strings.green
                                AccentColor.ORANGE -> strings.orange
                                AccentColor.PURPLE -> strings.purple
                            }
                        },
                        leading = { color ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(color.swatch()),
                            )
                        },
                        onSelected = onAccentColorChange,
                    )
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
