package com.example.toolbox.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.privacy.PrivacyShortcutRow
import com.example.toolbox.privacy.PrivacyShortcutUnavailable
import com.example.toolbox.privacy.launchPrivacyShortcut
import com.example.toolbox.privacy.loadPrivacyRows
import com.example.toolbox.privacy.privacyAdbCommand
import com.example.toolbox.privacy.requestPrivacyPinShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val chinese = strings.language == AppLanguage.CHINESE
    var rows by remember { mutableStateOf<List<PrivacyShortcutRow>>(emptyList()) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            rows = withContext(Dispatchers.IO) { loadPrivacyRows(context) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToolboxTopBar(
                title = strings.moduleTitle(ToolboxModule.PRIVACY),
                onBack = onBack,
                backLabel = strings.back,
                scrollBehavior = scrollBehavior,
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
                Text(
                    text = strings.privacyIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rows, key = { it.shortcut.id }) { row ->
                PrivacyShortcutCard(
                    strings = strings,
                    chinese = chinese,
                    row = row,
                    onOpen = {
                        val opened = launchPrivacyShortcut(context, row)
                        if (opened.isFailure) {
                            Toast.makeText(context, strings.privacyLaunchFailed, Toast.LENGTH_LONG).show()
                        }
                    },
                    onUnavailable = {
                        val message = when (row.selection.unavailable) {
                            PrivacyShortcutUnavailable.NOT_EXPORTED -> strings.privacyNotExported
                            else -> strings.privacyNotFound
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    },
                    onCopyAdb = {
                        val candidate = row.selection.candidate ?: row.shortcut.candidates.first()
                        copyToClipboard(
                            context = context,
                            label = row.shortcut.title(chinese),
                            value = privacyAdbCommand(candidate, row.selection.activity),
                            copiedMessage = strings.privacyCopiedAdb,
                        )
                    },
                    onPin = {
                        val requested = requestPrivacyPinShortcut(
                            context = context,
                            row = row,
                            label = row.shortcut.title(chinese),
                        )
                        Toast.makeText(
                            context,
                            if (requested) strings.privacyPinRequested else strings.privacyPinUnsupported,
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivacyShortcutCard(
    strings: ToolboxStrings,
    chinese: Boolean,
    row: PrivacyShortcutRow,
    onOpen: () -> Unit,
    onUnavailable: () -> Unit,
    onCopyAdb: () -> Unit,
    onPin: () -> Unit,
) {
    val available = row.selection.launchable
    val reason = when (row.selection.unavailable) {
        PrivacyShortcutUnavailable.NOT_FOUND -> strings.privacyNotFound
        PrivacyShortcutUnavailable.NOT_EXPORTED -> strings.privacyNotExported
        null -> null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (available) 1f else 0.55f)
            .combinedClickable(
                onClick = {
                    if (available) onOpen() else onUnavailable()
                },
                onLongClick = onCopyAdb,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.shortcut.title(chinese),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.shortcut.description(chinese),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val component = row.selection.activity
            Text(
                text = if (component != null) {
                    "${component.packageName}/${component.className}"
                } else {
                    reason.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (component != null) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCopyAdb) {
                    Text(strings.privacyCopyAdb)
                }
                if (available) {
                    TextButton(onClick = onPin) {
                        Text(strings.privacyPinShortcut)
                    }
                }
            }
        }
    }
}
