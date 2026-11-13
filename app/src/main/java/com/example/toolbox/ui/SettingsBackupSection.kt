package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavServiceProfile
import com.example.toolbox.totp.TotpStore
import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpVaultException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsBackupSection(
    strings: ToolboxStrings,
    service: WebDavServiceProfile?,
    onRestored: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Loading a saved passphrase touches SharedPreferences and the Keystore,
    // so it must not run during composition.
    var passphrase by remember(context) { mutableStateOf("") }
    var passphraseState by remember(context) { mutableStateOf(SavedPassphraseState.LOADING) }
    var passphraseLoadAttempt by remember { mutableStateOf(0) }
    var askingPassword by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember(service?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(context, passphraseLoadAttempt) {
        passphraseState = SavedPassphraseState.LOADING
        val outcome = withContext(Dispatchers.IO) {
            TotpStore(context).readSavedDefaultBackupPassphrase()
        }
        outcome.onSuccess { saved ->
            if (saved.isNullOrEmpty()) {
                passphraseState = SavedPassphraseState.ABSENT
            } else {
                // Never overwrite text the user typed while the read was in flight.
                if (passphrase.isEmpty()) passphrase = saved
                passphraseState = SavedPassphraseState.READY
            }
        }.onFailure {
            passphraseState = SavedPassphraseState.LOAD_FAILED
        }
    }

    fun backup(pass: String) {
        val target = service?.takeIf { it.isComplete } ?: return
        if (busy) return
        passphrase = pass
        busy = true
        status = null
        scope.launch {
            try {
                val (rememberFailed, result) = withContext(Dispatchers.IO) {
                    // Remembering the passphrase is a convenience; a Keystore
                    // failure must not block this backup or lose the entry.
                    val failed = runCatching {
                        TotpStore(context).saveDefaultBackupPassphrase(pass)
                    }.isFailure
                    failed to AppSettingsBackup.backup(target.syncClient(BackupTool.SETTINGS),
                        AppPreferences(context).backupSettings(), pass)
                }
                passphraseState = if (rememberFailed) {
                    SavedPassphraseState.WRITE_FAILED
                } else {
                    SavedPassphraseState.READY
                }
                status = strings.settingsBackupDone + result.snapshotFailure?.let {
                    "\n" + strings.webDavSnapshotFailed(strings.webDavError(it))
                }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = when (error) {
                    is TotpVaultException -> strings.backupDecryptFailed
                    is WebDavException -> strings.webDavError(error.failure, error.httpCode)
                    else -> strings.backupHistoryFailed
                }
            } finally {
                busy = false
            }
        }
    }

    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.settingsBackupTitle, style = MaterialTheme.typography.titleSmall)
        Text(strings.settingsBackupHint, style = MaterialTheme.typography.bodySmall)
        service?.let { Text(strings.settingsBackupTarget(it.name), style = MaterialTheme.typography.bodySmall) }
        FilledTonalButton(
            enabled = !busy && service?.isComplete == true &&
                passphraseState != SavedPassphraseState.LOADING,
            onClick = {
                if (TotpVaultCrypto.validatePassphrase(passphrase)) {
                    backup(passphrase)
                } else {
                    askingPassword = true
                }
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(strings.settingsBackupNow)
        }
        when (passphraseState) {
            SavedPassphraseState.LOADING -> Text(
                text = strings.backupPassphraseLoading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SavedPassphraseState.LOAD_FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.backupPassphraseLoadFailed,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { passphraseLoadAttempt++ }) {
                    Text(strings.retryAction)
                }
            }
            SavedPassphraseState.WRITE_FAILED -> Text(
                text = strings.backupPassphraseSaveFailed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            SavedPassphraseState.ABSENT, SavedPassphraseState.READY -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { history = true }) {
                Text(strings.backupHistoryTitle)
            }
            TextButton(enabled = !busy, onClick = { askingPassword = true }) {
                Text(strings.backupPasswordTitle)
            }
        }
        if (busy) CircularProgressIndicator()
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (askingPassword) {
        BackupPassphraseDialog(strings, passphrase,
            onDismiss = { askingPassword = false },
            onConfirm = { askingPassword = false; backup(it) })
    }
    if (history) {
        BackupHistoryDialog(strings, BackupTool.SETTINGS, listOfNotNull(service?.takeIf { it.isComplete }), passphrase,
            preview = { body, pass -> AppSettingsBackup.decode(body, pass).length() },
            restore = { body, pass -> AppSettingsBackup.restore(context, body, pass) },
            onRestored = { history = false; onRestored() },
            onDismiss = { history = false })
    }
}

/** Whether the Keystore-backed backup passphrase has been read for this screen. */
private enum class SavedPassphraseState { LOADING, ABSENT, READY, LOAD_FAILED, WRITE_FAILED }
