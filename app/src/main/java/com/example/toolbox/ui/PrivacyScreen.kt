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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.privacy.PrivacyPinResult
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
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadAttempt by remember { mutableStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(lifecycleOwner, reloadAttempt) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            // Keep an already loaded list visible while it refreshes.
            loading = rows.isEmpty()
            loadFailed = false
            val outcome = withContext(Dispatchers.IO) {
                runCatching { loadPrivacyRows(context) }
            }
            outcome.onSuccess {
                rows = it
                loadFailed = false
            }.onFailure {
                loadFailed = true
            }
            loading = false
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
            contentPadding = readableContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.privacyIntro,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = strings.privacyLoading,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (loadFailed) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.privacyLoadFailed,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { reloadAttempt++ }) {
                            Text(strings.retryAction)
                        }
                    }
                }
            }
            if (!loading && !loadFailed && rows.isEmpty()) {
                item {
                    Text(
                        text = strings.privacyEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        val message = when (
                            requestPrivacyPinShortcut(
                                context = context,
                                row = row,
                                label = row.shortcut.title(chinese),
                            )
                        ) {
                            PrivacyPinResult.REQUESTED -> strings.privacyPinRequested
                            PrivacyPinResult.UNSUPPORTED -> strings.privacyPinUnsupported
                            PrivacyPinResult.PERMISSION_DENIED -> strings.privacyPinPermissionDenied
                            PrivacyPinResult.FAILED -> strings.privacyPinFailed
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
    ToolboxCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = {
                    if (available) onOpen() else onUnavailable()
                },
                onLongClick = onCopyAdb,
            ),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.shortcut.title(chinese),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!available && reason != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = row.shortcut.description(chinese),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCopyAdb,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(strings.privacyCopyAdb)
                }
                if (available) {
                    TextButton(
                        onClick = onPin,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(strings.privacyPinShortcut)
                    }
                }
            }
        }
    }
}
