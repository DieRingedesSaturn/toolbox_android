package com.example.toolbox.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.toolbox.R
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavConfigStore
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavServiceProfile
import com.example.toolbox.totp.TotpAlgorithm
import com.example.toolbox.totp.TotpCameraScannerView
import com.example.toolbox.totp.TotpEntry
import com.example.toolbox.totp.TotpGenerator
import com.example.toolbox.totp.TotpLocalVaultException
import com.example.toolbox.totp.TotpQrDecoder
import com.example.toolbox.totp.TotpStore
import com.example.toolbox.totp.TotpVaultCrypto
import com.example.toolbox.totp.TotpVaultException
import com.example.toolbox.totp.TotpVaultFailure
import com.example.toolbox.totp.TotpWebDavSync
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
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
    var syncBusy by remember { mutableStateOf(false) }

    var passphrasePurpose by remember { mutableStateOf<PassphrasePurpose?>(null) }
    var savedPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
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
                for (service in targets) {
                    val res = runCatching {
                        TotpWebDavSync(
                            client = WebDavClient(
                                folderUrl = service.folderUrl,
                                username = service.username,
                                password = service.password,
                            ),
                            passphrase = passphrase,
                            loadLocal = { store.queryAllForSync() },
                            applyMerged = { merged -> store.replaceAll(merged) },
                        ).sync(syncTime)
                    }
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
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
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
                            OutlinedButton(
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
                    OutlinedButton(
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

    if (showEditor) {
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
                    OutlinedButton(
                        onClick = {
                            val incoming = imported
                            pendingImportEntries = null
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    runCatching {
                                        store.replaceAll(incoming, overwriteUnreadable = true)
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

@Composable
private fun TotpQrScannerDialog(
    strings: ToolboxStrings,
    onDismiss: () -> Unit,
    onQrDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasCameraPerm = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching { TotpQrDecoder.decodeImageUri(context, uri) }.getOrNull()
                }
                if (decoded.isNullOrBlank()) {
                    Toast.makeText(context, strings.totpQrNoCodeFound, Toast.LENGTH_LONG).show()
                } else {
                    onQrDecoded(decoded)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.totpScanDialogTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = strings.totpScanDialogHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasCameraPerm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        TotpCameraScannerView(
                            modifier = Modifier.fillMaxSize(),
                            onQrCodeScanned = onQrDecoded,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.72f)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(16.dp),
                                ),
                        )
                    }
                } else {
                    Text(
                        text = strings.totpCameraPermissionDenied,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.totpPickQrImageAction)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
private fun TotpTokenCard(
    strings: ToolboxStrings,
    entry: TotpEntry,
    nowMillis: Long,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyCode: (String) -> Unit,
    onCopyUri: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val badge = remember(entry.issuer, entry.accountName) {
        TotpGenerator.serviceBadge(entry.issuer, entry.accountName)
    }
    val rawCode = remember(entry, nowMillis / 1000L) {
        runCatching { TotpGenerator.generateCode(entry, nowMillis) }.getOrDefault("------")
    }
    val formattedCode = remember(rawCode, isRevealed, entry.digits) {
        if (isRevealed) {
            TotpGenerator.formatDisplayCode(rawCode)
        } else if (entry.digits == 8) {
            "•••• ••••"
        } else {
            "••• •••"
        }
    }
    val remainingSec = TotpGenerator.remainingSeconds(entry.period, nowMillis)
    val progress = TotpGenerator.progress(entry.period, nowMillis)
    val expiringSoon = remainingSec <= 5
    var cardMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggleReveal() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(badge.second)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.first,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.displaySubtitle.isNotEmpty()) {
                        Text(
                            text = entry.displaySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = formattedCode,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isRevealed) 20.sp else 17.sp,
                            letterSpacing = 1.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = when {
                            !isRevealed -> MaterialTheme.colorScheme.onSurfaceVariant
                            expiringSoon -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                    )
                    if (isRevealed) {
                        Text(
                            text = "${remainingSec}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (expiringSoon) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                IconButton(
                    onClick = { onCopyCode(rawCode) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = strings.copy,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(
                        onClick = { cardMenuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = strings.moreOptions,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = cardMenuExpanded,
                        onDismissRequest = { cardMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.totpEditTitle) },
                            onClick = {
                                cardMenuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.totpCopyUriAction) },
                            onClick = {
                                cardMenuExpanded = false
                                onCopyUri()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = strings.deleteAction,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                cardMenuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            if (isRevealed) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (expiringSoon) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TotpEntryEditor(
    strings: ToolboxStrings,
    initial: TotpEntry?,
    onDismiss: () -> Unit,
    onSave: (TotpEntry) -> Unit,
) {
    val context = LocalContext.current
    var uriInput by rememberSaveable { mutableStateOf("") }
    var issuer by rememberSaveable { mutableStateOf(initial?.issuer.orEmpty()) }
    var accountName by rememberSaveable { mutableStateOf(initial?.accountName.orEmpty()) }
    var secret by rememberSaveable { mutableStateOf(initial?.secret.orEmpty()) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var showUri by rememberSaveable { mutableStateOf(false) }
    var algorithmName by rememberSaveable {
        mutableStateOf((initial?.algorithm ?: TotpAlgorithm.SHA1).name)
    }
    var digits by rememberSaveable { mutableIntStateOf(initial?.digits ?: 6) }
    var period by rememberSaveable { mutableIntStateOf(initial?.period ?: 30) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val algorithm = TotpAlgorithm.fromString(algorithmName) ?: TotpAlgorithm.SHA1

    fun applyParsedUri(rawUri: String) {
        val parsed = runCatching { TotpGenerator.parseOtpauthUri(rawUri) }.getOrNull()
        if (parsed == null) {
            errorText = strings.totpUriInvalidError
        } else {
            issuer = parsed.issuer
            accountName = parsed.accountName
            secret = parsed.secret
            algorithmName = parsed.algorithm.name
            digits = parsed.digits
            period = parsed.period
            errorText = null
            Toast.makeText(context, strings.totpUriParsedToast, Toast.LENGTH_SHORT).show()
        }
    }

    fun saveEntry() {
        val normalizedSecret = runCatching {
            TotpGenerator.base32Decode(secret)
            TotpGenerator.normalizeBase32(secret)
        }.getOrNull()
        if (normalizedSecret == null) {
            errorText = strings.totpSecretInvalidError
            return
        }
        val trimmedIssuer = issuer.trim()
        val trimmedAccount = accountName.trim().ifEmpty {
            trimmedIssuer.ifEmpty { "TOTP" }
        }
        val now = System.currentTimeMillis()
        onSave(
            TotpEntry(
                uuid = initial?.uuid ?: UUID.randomUUID().toString(),
                issuer = trimmedIssuer,
                accountName = trimmedAccount,
                secret = normalizedSecret,
                algorithm = algorithm,
                digits = digits,
                period = period,
                createdAtMillis = initial?.createdAtMillis ?: now,
                updatedAtMillis = now,
                deletedAtMillis = null,
            ),
        )
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
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ToolboxTopBar(
                        title = if (initial == null) {
                            strings.totpNewTitle
                        } else {
                            strings.totpEditTitle
                        },
                        onBack = onDismiss,
                        backLabel = strings.cancelAction,
                        actions = {
                            TextButton(onClick = ::saveEntry) {
                                Text(strings.saveAction)
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                if (initial == null) {
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = {
                            uriInput = it
                            if (it.trim().startsWith("otpauth://", ignoreCase = true)) {
                                applyParsedUri(it)
                            }
                        },
                        label = { Text(strings.totpUriFieldLabel) },
                        singleLine = true,
                        visualTransformation = if (showUri) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            if (uriInput.isNotEmpty()) {
                                IconButton(onClick = { showUri = !showUri }) {
                                    Icon(
                                        painter = painterResource(
                                            if (showUri) {
                                                R.drawable.ic_visibility_off
                                            } else {
                                                R.drawable.ic_visibility
                                            },
                                        ),
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? ClipboardManager
                            val pasted = clip?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                            val candidate = uriInput.trim().ifEmpty { pasted }
                            if (candidate.isNotEmpty()) {
                                uriInput = candidate
                                applyParsedUri(candidate)
                            } else {
                                errorText = strings.totpUriInvalidError
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.totpParseUriButton)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }

                OutlinedTextField(
                    value = issuer,
                    onValueChange = {
                        issuer = it
                        errorText = null
                    },
                    label = { Text(strings.totpIssuerLabel) },
                    placeholder = { Text(strings.totpIssuerHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = accountName,
                    onValueChange = {
                        accountName = it
                        errorText = null
                    },
                    label = { Text(strings.totpAccountLabel) },
                    placeholder = { Text(strings.totpAccountHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        errorText = null
                    },
                    label = { Text(strings.totpSecretLabel) },
                    singleLine = true,
                    visualTransformation = if (showSecret) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                painter = painterResource(
                                    if (showSecret) {
                                        R.drawable.ic_visibility_off
                                    } else {
                                        R.drawable.ic_visibility
                                    },
                                ),
                                contentDescription = if (showSecret) {
                                    strings.webDavHidePassword
                                } else {
                                    strings.webDavShowPassword
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (initial == null) {
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "${strings.totpAdvancedOptionsLabel} (${algorithm.name} · $digits · ${period}s)",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = strings.totpAlgorithmLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TotpAlgorithm.entries.forEach { alg ->
                                    FilterChip(
                                        selected = algorithm == alg,
                                        onClick = { algorithmName = alg.name },
                                        label = { Text(alg.name) },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.totpDigitsLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(6, 8).forEach { d ->
                                            FilterChip(
                                                selected = digits == d,
                                                onClick = { digits = d },
                                                label = { Text("$d") },
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.totpPeriodLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(30, 60).forEach { p ->
                                            FilterChip(
                                                selected = period == p,
                                                onClick = { period = p },
                                                label = { Text("${p}s") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun TotpPassphraseDialog(
    strings: ToolboxStrings,
    initialPassphrase: String,
    initialRemember: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String, rememberInKeystore: Boolean) -> Unit,
) {
    var passphrase by remember { mutableStateOf(initialPassphrase) }
    var rememberInKeystore by rememberSaveable { mutableStateOf(initialRemember) }
    var showPassphrase by rememberSaveable { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.totpVaultPassphraseTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.totpVaultPassphraseHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorText = null
                    },
                    label = { Text(strings.totpVaultPassphraseLabel) },
                    singleLine = true,
                    visualTransformation = if (showPassphrase) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                painter = painterResource(
                                    if (showPassphrase) {
                                        R.drawable.ic_visibility_off
                                    } else {
                                        R.drawable.ic_visibility
                                    },
                                ),
                                contentDescription = if (showPassphrase) {
                                    strings.webDavHidePassword
                                } else {
                                    strings.webDavShowPassword
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberInKeystore = !rememberInKeystore },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberInKeystore,
                        onCheckedChange = { rememberInKeystore = it },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.totpVaultPassphraseRemember,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!TotpVaultCrypto.validatePassphrase(passphrase)) {
                        errorText = strings.totpVaultPassphraseTooShort
                        return@Button
                    }
                    onConfirm(passphrase, rememberInKeystore)
                },
            ) {
                Text(strings.confirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

private fun copySensitiveCode(
    context: Context,
    label: String,
    code: String,
    toastMessage: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    val clip = ClipData.newPlainText(label, code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
