package com.example.toolbox.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.R
import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavConfigStore
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavServiceProfile
import com.example.toolbox.totp.TotpEntry
import com.example.toolbox.totp.TotpGenerator
import com.example.toolbox.totp.TotpLocalVaultException
import com.example.toolbox.totp.TotpStore
import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpVaultException
import com.example.toolbox.totp.TotpVaultFailure
import com.example.toolbox.totp.TotpWebDavSync
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PassphrasePurpose {
    SYNC_WEBDAV,
    EXPORT_JSON,
    IMPORT_JSON,
    CONFIGURE_ONLY,
}

private data class TotpScreenLoadedState(
    val entries: List<TotpEntry>,
    val targets: List<WebDavServiceProfile>,
    val passphrase: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TotpScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { TotpStore(context) }
    val webDavStore = remember(context) { WebDavConfigStore(context) }

    // Block screenshots and recent-apps previews while TOTP screen is active.
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val hadSecure = window?.attributes?.flags?.let {
            (it and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false
        if (!hadSecure) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            if (!hadSecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    var entries by remember { mutableStateOf<List<TotpEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddMethodDialog by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showDirectScanner by rememberSaveable { mutableStateOf(false) }
    var editingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteUuid by rememberSaveable { mutableStateOf<String?>(null) }

    // Codes are hidden by default to keep cards compact and prevent shoulder-surfing.
    var revealAllCodes by rememberSaveable { mutableStateOf(false) }
    var revealedUuid by rememberSaveable { mutableStateOf<String?>(null) }

    var webDavVersion by remember { mutableIntStateOf(0) }
    var webDavTargets by remember { mutableStateOf<List<WebDavServiceProfile>>(emptyList()) }
    var showBackupHistory by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }

    var passphrasePurpose by remember { mutableStateOf<PassphrasePurpose?>(null) }
    var savedPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportPassphrase by remember { mutableStateOf("") }
    var pendingImportEntries by remember { mutableStateOf<List<TotpEntry>?>(null) }

    fun reload() {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    TotpScreenLoadedState(
                        entries = store.queryVisible(),
                        targets = webDavStore.selectedServices(BackupTool.TOTP),
                        passphrase = store.savedPassphrase(),
                    )
                }
            }
            outcome.onSuccess { loadedData ->
                entries = loadedData.entries
                webDavTargets = loadedData.targets
                savedPassphrase = loadedData.passphrase
                loadError = null
                loaded = true
            }.onFailure { error ->
                loadError = error
                loaded = true
            }
        }
    }

    LaunchedEffect(store, webDavVersion) {
        reload()
    }

    // Update TOTP codes and countdown progress once per second while visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                val sleepMs = (1000L - (nowMillis % 1000L)).coerceIn(150L, 1000L)
                delay(sleepMs)
            }
        }
    }

    fun vaultErrorMessage(error: Throwable): String = when (error) {
        is TotpVaultException -> when (error.failure) {
            TotpVaultFailure.PASSPHRASE_TOO_SHORT -> strings.totpVaultPassphraseTooShort
            TotpVaultFailure.WRONG_PASSPHRASE_OR_CORRUPTED,
            TotpVaultFailure.INVALID_FORMAT,
            TotpVaultFailure.UNSUPPORTED_VERSION,
            TotpVaultFailure.CRYPTO_ERROR,
            -> strings.totpVaultWrongPassphrase
        }
        is WebDavException -> strings.webDavError(error.failure, error.httpCode)
        is TotpLocalVaultException -> strings.totpLocalVaultUnreadable
        else -> strings.webDavError(WebDavFailure.NETWORK, null)
    }

    fun handleDirectScannedUri(rawUri: String) {
        val parsed = runCatching { TotpGenerator.parseOtpauthUri(rawUri) }.getOrNull()
        if (parsed == null) {
            Toast.makeText(context, strings.totpUriInvalidError, Toast.LENGTH_LONG).show()
            return
        }
        showDirectScanner = false
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { store.upsert(parsed) }.exceptionOrNull()
            }
            if (failure != null) {
                Toast.makeText(context, vaultErrorMessage(failure), Toast.LENGTH_LONG).show()
                reload()
                return@launch
            }
            reload()
            val label = if (parsed.displaySubtitle.isNotEmpty()) {
                "${parsed.displayTitle} (${parsed.displaySubtitle})"
            } else {
                parsed.displayTitle
            }
            Toast.makeText(
                context,
                strings.totpQrAddedToast(label),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val directCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, strings.totpCameraPermissionDenied, Toast.LENGTH_LONG).show()
        }
        showDirectScanner = true
    }

    fun requestDirectQrScan() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            showDirectScanner = true
        } else {
            directCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun runWebDavSyncWithPassphrase(passphrase: String) {
        val targets = webDavTargets.filter { it.isComplete }
        if (targets.isEmpty()) {
            Toast.makeText(context, strings.webDavNoTargetSelectedToast, Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }
        if (syncBusy) return
        syncBusy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                var okCount = 0
                val messages = mutableListOf<String>()
                val syncTime = System.currentTimeMillis()
                val results = TotpWebDavSync.syncTargets(
                    services = targets,
                    passphrase = passphrase,
                    loadLocal = { store.queryAllForSync() },
                    applyMerged = { store.mergeAndSave(it) },
                    nowMillis = syncTime,
                )
                for ((service, res) in targets.zip(results)) {
                    res.onSuccess { r ->
                        okCount++
                        val summary = strings.totpSyncSuccess(
                            total = r.totalTokens,
                            pulled = r.pulledCount,
                            pushed = r.pushedCount,
                        )
                        messages += "${service.name}: $summary" +
                            r.snapshotFailure?.let { failure ->
                                "\n" + strings.webDavSnapshotFailed(
                                    strings.webDavError(failure),
                                )
                            }.orEmpty()
                    }.onFailure { err ->
                        messages += "${service.name}: ${vaultErrorMessage(err)}"
                    }
                }
                if (okCount > 0) {
                    webDavStore.saveLastSync(syncTime, BackupTool.TOTP)
                }
                Triple(okCount, targets.size, messages.joinToString("\n"))
            }
            syncBusy = false
            webDavVersion++
            reload()
            val text = if (outcome.second == 1) {
                outcome.third
            } else {
                strings.webDavMultiSyncSummary(outcome.first, outcome.second, outcome.third)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    fun exportVaultToUri(uri: Uri, passphrase: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val all = store.queryAllForSync()
                    val json = TotpVaultCrypto.encryptVault(all, passphrase)
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("openOutputStream returned null")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    all.count { it.deletedAtMillis == null }
                }
            }
            Toast.makeText(
                context,
                outcome.fold(
                    onSuccess = { strings.backupExported(it) },
                    onFailure = { vaultErrorMessage(it) },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun decryptImportUri(uri: Uri, passphrase: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val r = stream.read(buf)
                            if (r < 0) break
                            total += r
                            if (total > TotpVaultCrypto.MAX_BYTES) throw IOException("File too large")
                            out.write(buf, 0, r)
                        }
                        String(out.toByteArray(), Charsets.UTF_8)
                    } ?: throw IOException("openInputStream returned null")
                    TotpVaultCrypto.decryptVault(text, passphrase)
                }
            }
            outcome.onSuccess { decrypted ->
                pendingImportPassphrase = passphrase
                pendingImportEntries = decrypted
            }.onFailure { err ->
                Toast.makeText(context, vaultErrorMessage(err), Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val pass = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && !pass.isNullOrEmpty()) {
            exportVaultToUri(uri, pass)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            passphrasePurpose = PassphrasePurpose.IMPORT_JSON
        }
    }

    val searching = searchQuery != null
    BackHandler(enabled = searching) {
        searchQuery = null
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    val filteredEntries = remember(entries, searchQuery) {
        val q = searchQuery?.trim().orEmpty()
        if (q.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.issuer.contains(q, ignoreCase = true) ||
                    entry.accountName.contains(q, ignoreCase = true)
            }
        }
    }

    val editingEntry = remember(entries, editingUuid) {
        editingUuid?.let { uuid -> entries.firstOrNull { it.uuid == uuid } }
    }
    val pendingDeleteEntry = remember(entries, pendingDeleteUuid) {
        pendingDeleteUuid?.let { uuid -> entries.firstOrNull { it.uuid == uuid } }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery.orEmpty(),
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = strings.totpSearchHint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (!searchQuery.isNullOrEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = strings.cancelAction,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchQuery = null }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = strings.back,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            } else {
                ToolboxTopBar(
                    title = strings.moduleTitle(ToolboxModule.TOTP),
                    onBack = onBack,
                    backLabel = strings.back,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (syncBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        if (entries.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    revealAllCodes = !revealAllCodes
                                    if (!revealAllCodes) {
                                        revealedUuid = null
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (revealAllCodes) {
                                            R.drawable.ic_visibility_off
                                        } else {
                                            R.drawable.ic_visibility
                                        },
                                    ),
                                    contentDescription = if (revealAllCodes) {
                                        strings.totpHideAllCodes
                                    } else {
                                        strings.totpShowAllCodes
                                    },
                                )
                            }
                        }
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = strings.searchAction,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = strings.moreOptions,
                                )
                            }
                            ToolboxDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.backupHistoryTitle) },
                                    onClick = { menuExpanded = false; showBackupHistory = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.webDavSyncNow) },
                                    onClick = {
                                        menuExpanded = false
                                        val pass = savedPassphrase
                                        if (!pass.isNullOrEmpty()) {
                                            runWebDavSyncWithPassphrase(pass)
                                        } else {
                                            passphrasePurpose = PassphrasePurpose.SYNC_WEBDAV
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.webDavOpenSettingsAction) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSettings()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.totpVaultPassphraseTitle) },
                                    onClick = {
                                        menuExpanded = false
                                        passphrasePurpose = PassphrasePurpose.CONFIGURE_ONLY
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.totpExportEncryptedMenu) },
                                    onClick = {
                                        menuExpanded = false
                                        val pass = savedPassphrase
                                        if (!pass.isNullOrEmpty()) {
                                            pendingExportPassphrase = pass
                                            val name = "toolbox-totp-" +
                                                LocalDateTime.now().format(
                                                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
                                                ) + ".json"
                                            exportLauncher.launch(name)
                                        } else {
                                            passphrasePurpose = PassphrasePurpose.EXPORT_JSON
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.totpImportEncryptedMenu) },
                                    onClick = {
                                        menuExpanded = false
                                        importLauncher.launch(arrayOf("application/json", "*/*"))
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!searching && loadError == null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddMethodDialog = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                        )
                    },
                    text = { Text(strings.totpAddShort) },
                )
            }
        },
    ) { padding ->
        if (loaded && filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                InfoCard(title = strings.moduleTitle(ToolboxModule.TOTP)) {
                    Text(
                        text = loadError?.let { vaultErrorMessage(it) } ?: strings.totpEmptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadError == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = ::requestDirectQrScan,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qr_scan),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.totpScanQrAction, maxLines = 1)
                            }
                            FilledTonalButton(
                                shape = MaterialTheme.shapes.small,
                                onClick = {
                                    editingUuid = null
                                    showEditor = true
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.totpManualAddAction, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { it.uuid },
                ) { entry ->
                    val isRevealed = revealAllCodes || revealedUuid == entry.uuid
                    TotpTokenCard(
                        strings = strings,
                        entry = entry,
                        nowMillis = nowMillis,
                        isRevealed = isRevealed,
                        onToggleReveal = {
                            if (!revealAllCodes) {
                                revealedUuid = if (revealedUuid == entry.uuid) null else entry.uuid
                            }
                        },
                        onCopyCode = { rawCode ->
                            copySensitiveCode(
                                context = context,
                                label = entry.displayTitle,
                                code = rawCode,
                                toastMessage = strings.totpCopyCodeLabel,
                            )
                        },
                        onCopyUri = {
                            copySensitiveCode(
                                context = context,
                                label = entry.displayTitle,
                                code = TotpGenerator.buildOtpauthUri(entry),
                                toastMessage = strings.totpCopyUriAction,
                            )
                        },
                        onEdit = {
                            editingUuid = entry.uuid
                            showEditor = true
                        },
                        onDelete = {
                            pendingDeleteUuid = entry.uuid
                        },
                    )
                }
            }
        }
    }

    if (showAddMethodDialog) {
        AlertDialog(
            onDismissRequest = { showAddMethodDialog = false },
            title = { Text(strings.totpNewTitle) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            showAddMethodDialog = false
                            requestDirectQrScan()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_qr_scan),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.totpScanQrAction)
                    }
                    FilledTonalButton(
                                shape = MaterialTheme.shapes.small,
                        onClick = {
                            showAddMethodDialog = false
                            editingUuid = null
                            showEditor = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.totpManualAddAction)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMethodDialog = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    if (showDirectScanner) {
        TotpQrScannerDialog(
            strings = strings,
            onDismiss = { showDirectScanner = false },
            onQrDecoded = ::handleDirectScannedUri,
        )
    }

    // After process restoration the editor state is saved but the token list
    // reloads from the encrypted store; wait for the entry before editing it.
    LaunchedEffect(loaded, showEditor, editingUuid, editingEntry) {
        if (loaded && showEditor && editingUuid != null && editingEntry == null) {
            showEditor = false
            editingUuid = null
        }
    }

    if (showEditor && loaded && (editingUuid == null || editingEntry != null)) {
        TotpEntryEditor(
            strings = strings,
            initial = editingEntry,
            onDismiss = {
                showEditor = false
                editingUuid = null
            },
            onSave = { updated ->
                showEditor = false
                editingUuid = null
                scope.launch {
                    val failure = withContext(Dispatchers.IO) {
                        runCatching { store.upsert(updated) }.exceptionOrNull()
                    }
                    if (failure != null) {
                        Toast.makeText(context, vaultErrorMessage(failure), Toast.LENGTH_LONG).show()
                    }
                    reload()
                }
            },
        )
    }

    pendingDeleteEntry?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteUuid = null },
            title = { Text(target.displayTitle) },
            text = { Text(strings.totpDeleteConfirm(target.displayTitle)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteUuid = null
                        scope.launch {
                            val failure = withContext(Dispatchers.IO) {
                                runCatching { store.softDelete(target.uuid) }.exceptionOrNull()
                            }
                            if (failure != null) {
                                Toast.makeText(context, vaultErrorMessage(failure), Toast.LENGTH_LONG).show()
                            }
                            reload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteUuid = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    if (showBackupHistory) {
        BackupHistoryDialog(
            strings, BackupTool.TOTP, webDavTargets, savedPassphrase.orEmpty(),
            preview = { body, pass -> TotpVaultCrypto.decryptVault(body, pass).count { it.deletedAtMillis == null } },
            restore = { body, pass ->
                val entries = TotpVaultCrypto.decryptVault(body, pass)
                BackupRecoveryStore(context, BackupTool.TOTP).save(
                    TotpVaultCrypto.encryptVault(store.queryAllForSync(), pass))
                store.restore(entries)
            },
            onRestored = { showBackupHistory = false; reload() },
            onDismiss = { showBackupHistory = false },
        )
    }

    passphrasePurpose?.let { purpose ->
        TotpPassphraseDialog(
            strings = strings,
            initialPassphrase = savedPassphrase.orEmpty(),
            initialRemember = !savedPassphrase.isNullOrEmpty(),
            onDismiss = {
                passphrasePurpose = null
                pendingImportUri = null
            },
            onConfirm = { pass, rememberInKeystore ->
                passphrasePurpose = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.savePassphrase(if (rememberInKeystore) pass else null)
                    }
                    savedPassphrase = if (rememberInKeystore) pass else null
                    when (purpose) {
                        PassphrasePurpose.SYNC_WEBDAV -> runWebDavSyncWithPassphrase(pass)
                        PassphrasePurpose.EXPORT_JSON -> {
                            pendingExportPassphrase = pass
                            val name = "toolbox-totp-" +
                                LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
                                ) + ".json"
                            exportLauncher.launch(name)
                        }
                        PassphrasePurpose.IMPORT_JSON -> {
                            val uri = pendingImportUri
                            pendingImportUri = null
                            if (uri != null) {
                                decryptImportUri(uri, pass)
                            }
                        }
                        PassphrasePurpose.CONFIGURE_ONLY -> Unit
                    }
                }
            },
        )
    }

    pendingImportEntries?.let { imported ->
        val activeCount = imported.count { it.deletedAtMillis == null }
        AlertDialog(
            onDismissRequest = { pendingImportEntries = null },
            title = { Text(strings.totpImportEncryptedMenu) },
            text = { Text("${strings.backupFileEntriesLabel}: $activeCount") },
            confirmButton = {
                Button(
                    onClick = {
                        val incoming = imported
                        pendingImportEntries = null
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching { store.mergeAndSave(incoming) }
                            }
                            reload()
                            Toast.makeText(
                                context,
                                outcome.fold(
                                    onSuccess = { merged ->
                                        strings.totpSyncSuccess(
                                            total = merged.entries.count { it.deletedAtMillis == null },
                                            pulled = merged.pulledCount,
                                            pushed = merged.pushedCount,
                                        )
                                    },
                                    onFailure = { vaultErrorMessage(it) },
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) {
                    Text(strings.backupMergeAction)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingImportEntries = null }) {
                        Text(strings.cancelAction)
                    }
                    FilledTonalButton(
                                shape = MaterialTheme.shapes.small,
                        onClick = {
                            val incoming = imported
                            pendingImportEntries = null
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val current = try {
                                            store.queryAllForSync()
                                        } catch (_: TotpLocalVaultException) {
                                            null // Explicit Replace can recover an unreadable local vault.
                                        }
                                        if (current != null) {
                                            BackupRecoveryStore(context, BackupTool.TOTP).save(
                                                TotpVaultCrypto.encryptVault(current, pendingImportPassphrase),
                                            )
                                        }
                                        store.restore(incoming, overwriteUnreadable = true)
                                        incoming.count { it.deletedAtMillis == null }
                                    }
                                }
                                reload()
                                Toast.makeText(
                                    context,
                                    outcome.fold(
                                        onSuccess = { active -> strings.totpSyncSuccess(active, active, 0) },
                                        onFailure = { vaultErrorMessage(it) },
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    ) {
                        Text(strings.backupReplaceAction)
                    }
                }
            },
        )
    }
}
