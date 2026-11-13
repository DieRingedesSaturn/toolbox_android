package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavHistory
import com.example.toolbox.ledger.WebDavServiceProfile
import com.example.toolbox.totp.TotpVaultCrypto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BackupVersion(
    val name: String,
    val time: LocalDateTime?,
    val read: () -> String,
    val legacy: Boolean = false,
    val local: Boolean = false,
)

@Composable
internal fun BackupPassphraseDialog(
    strings: ToolboxStrings,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pass by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.backupPasswordTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.backupPasswordHint)
                OutlinedTextField(value = pass, onValueChange = { pass = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(), label = { Text(strings.backupPasswordLabel) })
            }
        },
        confirmButton = {
            TextButton(enabled = TotpVaultCrypto.validatePassphrase(pass), onClick = { onConfirm(pass) }) {
                Text(strings.saveAction)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelAction) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupHistoryDialog(
    strings: ToolboxStrings,
    tool: BackupTool,
    targets: List<WebDavServiceProfile>,
    initialPassphrase: String,
    preview: (String, String) -> Int,
    restore: (String, String) -> Unit,
    onRestored: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recovery = remember(context, tool) { BackupRecoveryStore(context, tool) }
    var selected by remember { mutableStateOf(targets.firstOrNull()?.id) }
    var pass by remember { mutableStateOf(initialPassphrase) }
    var versions by remember { mutableStateOf<List<BackupVersion>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun readVersion(read: () -> String) {
        busy = true
        error = false
        scope.launch {
            try {
                pending = withContext(Dispatchers.IO) {
                    val body = read()
                    body to preview(body, pass)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(strings.backupHistoryTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.backupPasswordHint)
                OutlinedTextField(value = pass, onValueChange = { pass = it }, enabled = !busy, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(), label = { Text(strings.backupPasswordLabel) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { service ->
                        FilterChip(selected = selected == service.id, enabled = !busy, onClick = {
                            selected = service.id
                            versions = emptyList()
                            loaded = false
                        }, label = { Text(service.name) })
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = false
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val found = mutableListOf<BackupVersion>()
                                var failed = false
                                if (recovery.exists()) found += BackupVersion(strings.backupBeforeRestore, null, { recovery.read() }, local = true)
                                targets.firstOrNull { it.id == selected }?.let { service ->
                                    val clients = listOf(service.syncClient(tool)) + service.legacyClients(tool)
                                    clients.forEachIndexed { index, client ->
                                        try {
                                            val history = WebDavHistory(client, tool)
                                            history.list().forEach { name ->
                                                found += BackupVersion(name, WebDavHistory.snapshotTime(tool, name),
                                                    { history.read(name) }, legacy = index != 0)
                                            }
                                        } catch (failure: CancellationException) {
                                            throw failure
                                        } catch (_: Exception) { failed = true }
                                    }
                                }
                                found.sortedWith(compareByDescending<BackupVersion> { it.local }
                                    .thenByDescending { it.time }.thenByDescending { it.name }) to failed
                            }
                            versions = result.first
                            error = result.second
                            loaded = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }) { Text(strings.refresh) }
                if (busy) CircularProgressIndicator()
                if (error) Text(strings.backupHistoryFailed)
                if (loaded && versions.isEmpty()) Text(strings.backupHistoryEmpty)
                versions.groupBy { version ->
                    when {
                        version.local -> strings.backupBeforeRestore
                        version.time != null -> version.time.toLocalDate().toString()
                        else -> strings.backupTimeUnknown
                    }
                }.forEach { (date, items) ->
                    Text(date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    items.forEach { version ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && TotpVaultCrypto.validatePassphrase(pass),
                            onClick = { readVersion(version.read) },
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(version.time?.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) ?: version.name)
                                if (!version.local) {
                                    Text(version.name, style = MaterialTheme.typography.bodySmall)
                                    if (version.legacy) Text(strings.backupLegacy, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(strings.cancelAction) } },
    )
    pending?.let { (body, count) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(strings.backupHistoryTitle) },
            text = { Text(if (tool == BackupTool.SETTINGS) strings.settingsRestoreWarning(count) else strings.backupRestoreWarning(count)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { restore(body, pass) }
                            pending = null
                            onRestored()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { error = true; pending = null }
                        finally { busy = false }
                    }
                }) { Text(strings.backupRestoreAction) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(strings.cancelAction) } },
        )
    }
}
