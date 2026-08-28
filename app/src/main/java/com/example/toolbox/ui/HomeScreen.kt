package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.device.DeviceInfo

private data class ModuleCard(
    val module: ToolboxModule,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    strings: ToolboxStrings,
    deviceInfo: DeviceInfo?,
    onOpen: (ToolboxModule) -> Unit,
) {
    val cards = listOf(
        ModuleCard(ToolboxModule.MONITOR),
        ModuleCard(ToolboxModule.ASTRONOMY),
        ModuleCard(ToolboxModule.USAGE),
        ModuleCard(ToolboxModule.DEVICE),
        ModuleCard(ToolboxModule.LOCATION),
        ModuleCard(ToolboxModule.NETWORK),
    )
    val columns = if (isExpandedWidth()) 2 else 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.appName) },
                actions = {
                    TextButton(onClick = { onOpen(ToolboxModule.ABOUT) }) {
                        Text(strings.about)
                    }
                    TextButton(onClick = { onOpen(ToolboxModule.SETTINGS) }) {
                        Text(strings.settings)
                    }
                },
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
            items(cards.chunked(columns)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { card ->
                        ModuleCardView(
                            strings = strings,
                            card = card,
                            deviceInfo = deviceInfo,
                            onClick = { onOpen(card.module) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCardView(
    strings: ToolboxStrings,
    card: ModuleCard,
    deviceInfo: DeviceInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = strings.moduleTitle(card.module),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = strings.moduleSummary(card.module, deviceInfo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
