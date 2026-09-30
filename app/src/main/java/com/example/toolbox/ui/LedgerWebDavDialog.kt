package com.example.toolbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
import com.example.toolbox.ledger.WebDavConfig
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavPaths

internal enum class WebDavBusyAction {
    TEST,
    SYNC,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerWebDavDialog(
    strings: ToolboxStrings,
    initialConfig: WebDavConfig?,
    passwordUnavailable: Boolean,
    lastSyncMillis: Long?,
    pendingCount: Int,
    busyAction: WebDavBusyAction?,
    lastResult: String?,
    onSaveConfig: (WebDavConfig) -> Unit,
    onTestConnection: (WebDavConfig) -> Unit,
    onSyncNow: (WebDavConfig) -> Unit,
    onClearConfig: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var urlText by rememberSaveable { mutableStateOf(initialConfig?.folderUrl ?: "") }
    var usernameText by rememberSaveable { mutableStateOf(initialConfig?.username ?: "") }
    var passwordText by remember { mutableStateOf(initialConfig?.password ?: "") }
    var userEdited by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var urlError by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    // Seed the fields when the asynchronously loaded config arrives, but never
    // clobber text the user has already typed.
    LaunchedEffect(initialConfig) {
        val config = initialConfig
        if (config != null && !userEdited) {
            urlText = config.folderUrl
            usernameText = config.username
            passwordText = config.password
        }
    }

    fun currentConfig(): WebDavConfig? {
        val normalized = WebDavPaths.normalizeFolderUrl(urlText)
        urlError = normalized == null
        return normalized?.let {
            WebDavConfig(
                folderUrl = it,
                username = usernameText.trim(),
                password = passwordText,
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                modifier = Modifier.imePadding(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = strings.webDavBackupMenu,
                                maxLines = 1,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = strings.cancelAction,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = strings.webDavHelpText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            urlError = false
                            userEdited = true
                        },
                        label = { Text(strings.webDavFolderUrlLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = urlError,
                        supportingText = if (urlError) {
                            { Text(strings.webDavError(WebDavFailure.INVALID_URL)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        enabled = busyAction == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = usernameText,
                        onValueChange = {
                            usernameText = it
                            userEdited = true
                        },
                        label = { Text(strings.webDavUsernameLabel) },
                        singleLine = true,
                        enabled = busyAction == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            userEdited = true
                        },
                        label = { Text(strings.webDavPasswordLabel) },
                        singleLine = true,
                        enabled = busyAction == null,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter = painterResource(
                                        if (showPassword) {
                                            R.drawable.ic_visibility_off
                                        } else {
                                            R.drawable.ic_visibility
                                        },
                                    ),
                                    contentDescription = if (showPassword) {
                                        strings.webDavHidePassword
                                    } else {
                                        strings.webDavShowPassword
                                    },
                                )
                            }
                        },
                        isError = passwordUnavailable,
                        supportingText = if (passwordUnavailable) {
                            { Text(strings.webDavError(WebDavFailure.PASSWORD_UNAVAILABLE)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                currentConfig()?.let(onSaveConfig)
                            },
                            enabled = busyAction == null,
                        ) {
                            Text(strings.saveAction)
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                currentConfig()?.let(onTestConnection)
                            },
                            enabled = busyAction == null,
                        ) {
                            if (busyAction == WebDavBusyAction.TEST) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current,
                                )
                            } else {
                                Text(strings.webDavTestConnection)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                currentConfig()?.let(onSyncNow)
                            },
                            enabled = busyAction == null,
                        ) {
                            if (busyAction == WebDavBusyAction.SYNC) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current,
                                )
                            } else {
                                Text(strings.webDavSyncNow)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    InfoCard(title = strings.webDavStatusTitle) {
                        InfoRow(
                            label = strings.webDavLastSyncLabel,
                            value = lastSyncMillis?.let {
                                strings.formatLedgerDateTime(it)
                            } ?: strings.notAvailable,
                            onCopy = {
                                copyToClipboard(
                                    context = context,
                                    label = strings.webDavLastSyncLabel,
                                    value = it,
                                )
                            },
                        )
                        InfoRow(
                            label = strings.pendingSyncRecords,
                            value = pendingCount.toString(),
                            onCopy = {
                                copyToClipboard(
                                    context = context,
                                    label = strings.pendingSyncRecords,
                                    value = it,
                                )
                            },
                        )
                        if (lastResult != null) {
                            InfoRow(
                                label = strings.webDavLastResultLabel,
                                value = lastResult,
                                onCopy = {
                                    copyToClipboard(
                                        context = context,
                                        label = strings.webDavLastResultLabel,
                                        value = it,
                                    )
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = busyAction == null,
                    ) {
                        Text(
                            text = strings.webDavClearButton,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(strings.webDavClearButton) },
            text = { Text(strings.webDavClearConfirmText) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearConfig()
                    },
                ) {
                    Text(strings.webDavClearAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}
