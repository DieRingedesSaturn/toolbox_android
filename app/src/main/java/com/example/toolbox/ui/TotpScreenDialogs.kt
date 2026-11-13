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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.BackupTool
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TotpQrScannerDialog(
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
                FilledTonalButton(
                                shape = MaterialTheme.shapes.small,
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
internal fun TotpTokenCard(
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

    ToolboxCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onToggleReveal() },
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
                        color = Color(TotpGenerator.badgeForegroundRgb(badge.second)),
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
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = strings.moreOptions,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    ToolboxDropdownMenu(
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
internal fun TotpEntryEditor(
    strings: ToolboxStrings,
    initial: TotpEntry?,
    onDismiss: () -> Unit,
    onSave: (TotpEntry) -> Unit,
) {
    val context = LocalContext.current
    // A secret or otpauth URI must never enter the saved-instance Bundle.
    // An existing token is re-read from the encrypted store when the editor
    // is restored; a new, unsaved secret is simply dropped.
    var uriInput by remember { mutableStateOf("") }
    var issuer by rememberSaveable { mutableStateOf(initial?.issuer.orEmpty()) }
    var accountName by rememberSaveable { mutableStateOf(initial?.accountName.orEmpty()) }
    var secret by remember { mutableStateOf(initial?.secret.orEmpty()) }
    var showSecret by remember { mutableStateOf(false) }
    var showUri by remember { mutableStateOf(false) }
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
        val opaqueBg = MaterialTheme.colorScheme.background.copy(alpha = 1f)
        CompositionLocalProvider(LocalAcrylicBackground provides AcrylicBackgroundState()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = opaqueBg,
            ) {
                Scaffold(
                    containerColor = opaqueBg,
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
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = readableHorizontalPadding(),
                            vertical = 12.dp,
                        ),
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
                                        contentDescription = if (showUri) {
                                            strings.totpHideUri
                                        } else {
                                            strings.totpShowUri
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(
                                shape = MaterialTheme.shapes.small,
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
}

@Composable
internal fun TotpPassphraseDialog(
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

internal fun copySensitiveCode(
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
