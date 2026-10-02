package com.example.toolbox.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import com.example.toolbox.ledger.NotificationLedgerCandidate
import com.example.toolbox.ledger.NotificationLedgerStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NotificationSourceApp(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotificationLedgerDialog(
    strings: ToolboxStrings,
    onSelectAmount: (NotificationLedgerCandidate, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { SimpleDateFormat("MM-dd HH:mm", locale) }
    val store = remember(context) { NotificationLedgerStore(context) }
    var enabled by remember { mutableStateOf(store.enabled()) }
    var allowedPackages by remember { mutableStateOf(store.allowedPackages()) }
    var candidates by remember { mutableStateOf(store.candidates()) }
    var apps by remember { mutableStateOf<List<NotificationSourceApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var accessGranted by remember {
        mutableStateOf(
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
        )
    }
    var selectedTab by remember {
        mutableIntStateOf(if (accessGranted && allowedPackages.isNotEmpty()) 0 else 1)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        accessGranted = context.packageName in
            NotificationManagerCompat.getEnabledListenerPackages(context)
        candidates = store.candidates()
    }

    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) { installedNotificationApps(context) }
        appsLoaded = true
    }

    val appLabels = remember(apps) { apps.associate { it.packageName to it.label } }
    val displayedApps = remember(apps, allowedPackages, search, selectedOnly) {
        val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
        val missing = (allowedPackages - installedPackages).map {
            NotificationSourceApp(it, it)
        }
        (apps + missing).filter { app ->
            (!selectedOnly || app.packageName in allowedPackages) &&
                (search.isBlank() || app.label.contains(search, ignoreCase = true) ||
                    app.packageName.contains(search, ignoreCase = true))
        }.sortedWith(compareBy<NotificationSourceApp> { it.label.lowercase() }
            .thenBy { it.packageName })
    }
    val status = when {
        !enabled -> strings.notificationLedgerStatusOff
        !accessGranted -> strings.notificationLedgerStatusNeedsAccess
        allowedPackages.isEmpty() -> strings.notificationLedgerStatusNeedsSources
        else -> strings.notificationLedgerStatusReady(allowedPackages.size)
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (intent.resolveActivity(context.packageManager) != null) {
            settingsLauncher.launch(intent)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().imePadding()) {
            Scaffold(
                topBar = {
                    ToolboxTopBar(
                        title = strings.notificationLedgerTitle,
                        onBack = onDismiss,
                        backLabel = strings.back,
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.notificationLedgerEnable,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                store.setEnabled(it)
                                if (!it) candidates = emptyList()
                            },
                        )
                    }
                    if (enabled && !accessGranted) {
                        Button(
                            onClick = ::openSystemSettings,
                            modifier = Modifier.padding(start = 18.dp, bottom = 8.dp),
                        ) { Text(strings.notificationLedgerGrantAccess) }
                    }
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                candidates = store.candidates()
                            },
                            text = {
                                Text("${strings.notificationLedgerCandidatesTab} (${candidates.size})")
                            },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text("${strings.notificationLedgerSourcesTab} (${allowedPackages.size})")
                            },
                        )
                    }
                    if (selectedTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.notificationLedgerCandidateHint,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { candidates = store.candidates() }) {
                                Text(strings.refresh)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 20.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (candidates.isEmpty()) {
                                item {
                                    InfoCard(strings.notificationLedgerEmpty) {
                                        Text(strings.notificationLedgerEmptyHint)
                                        if (allowedPackages.isEmpty()) {
                                            TextButton(onClick = { selectedTab = 1 }) {
                                                Text(strings.notificationLedgerSources)
                                            }
                                        }
                                    }
                                }
                            }
                            items(candidates, key = { it.id }) { candidate ->
                                InfoCard(
                                    title = appLabels[candidate.packageName]
                                        ?: candidate.packageName,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            timeFormatter.format(Date(candidate.occurredAtMillis)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = {
                                            store.removeCandidate(candidate.id)
                                            candidates = store.candidates()
                                        }) { Text(strings.notificationLedgerDiscard) }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        candidate.amountsCents.forEach { amount ->
                                            OutlinedButton(
                                                onClick = { onSelectAmount(candidate, amount) },
                                            ) {
                                                Text(String.format(Locale.US, "%.2f", amount / 100.0))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        ) {
                            Text(
                                strings.notificationLedgerHint,
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = ::openSystemSettings) {
                                Text(
                                    if (accessGranted) strings.notificationLedgerManageAccess
                                    else strings.notificationLedgerGrantAccess,
                                )
                            }
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text(strings.notificationLedgerAppSearch) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FilterChip(
                                selected = selectedOnly,
                                onClick = { selectedOnly = !selectedOnly },
                                label = { Text(strings.notificationLedgerSelectedOnly) },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            HorizontalDivider()
                            if (!appsLoaded) {
                                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            } else if (displayedApps.isEmpty()) {
                                Text(
                                    strings.notificationLedgerNoApps,
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(displayedApps, key = { it.packageName }) { app ->
                                        val checked = app.packageName in allowedPackages
                                        Row(
                                            modifier = Modifier.fillMaxWidth().toggleable(
                                                value = checked,
                                                role = Role.Checkbox,
                                                onValueChange = { selected ->
                                                    allowedPackages = if (selected) {
                                                        allowedPackages + app.packageName
                                                    } else {
                                                        allowedPackages - app.packageName
                                                    }
                                                    store.setAllowedPackages(allowedPackages)
                                                    candidates = store.candidates()
                                                },
                                            ).padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(checked = checked, onCheckedChange = null)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    app.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (app.label != app.packageName) {
                                                    Text(
                                                        app.packageName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun installedNotificationApps(context: Context): List<NotificationSourceApp> {
    val manager = context.packageManager
    val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getInstalledApplications(0)
    }
    return installed.asSequence()
        .filter { it.packageName != context.packageName }
        .map {
            NotificationSourceApp(
                packageName = it.packageName,
                label = it.loadLabel(manager).toString(),
            )
        }
        .sortedWith(compareBy<NotificationSourceApp> { it.label.lowercase() }
            .thenBy { it.packageName })
        .toList()
}
