package com.example.toolbox.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.LedgerWebDavSync
import com.example.toolbox.ledger.WebDavConfigStore
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavPaths
import com.example.toolbox.ledger.WebDavServiceProfile
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    strings: ToolboxStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    customAccentRgb: Int,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onCustomAccentChange: (Int) -> Unit,
    backgroundImage: ImageBitmap?,
    backgroundStrength: Float,
    onBackgroundStrengthChange: (Float) -> Unit,
    backgroundBlurEnabled: Boolean,
    onBackgroundBlurEnabledChange: (Boolean) -> Unit,
    backgroundBlurStrength: Float,
    onBackgroundBlurStrengthChange: (Float) -> Unit,
    backgroundScale: Float,
    backgroundOffsetX: Float,
    backgroundOffsetY: Float,
    onBackgroundTransformChange: (Float, Float, Float) -> Unit,
    onBackgroundChanged: () -> Unit,
    onSettingsRestored: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var summaryEnabled by remember { mutableStateOf(preferences.homeSummaryEnabled()) }
    var summaryItems by remember { mutableStateOf(preferences.homeSummaryItems()) }
    var summaryCustomText by remember { mutableStateOf(preferences.homeSummaryCustomText()) }
    var moduleOrder by remember { mutableStateOf(preferences.homeModuleOrder()) }
    var showSummaryConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showModuleOrderDialog by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ToolboxTopBar(
                title = strings.settings,
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
                SettingsCard(title = strings.languageTitle) {
                    ChoiceStrip(
                        options = AppLanguage.entries.toList(),
                        selected = language,
                        label = { it.displayName },
                        onSelected = onLanguageChange,
                    )
                }
            }
            item {
                SettingsCard(title = strings.themeTitle) {
                    ChoiceStrip(
                        options = ThemeMode.entries.toList(),
                        selected = themeMode,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> strings.system
                                ThemeMode.LIGHT -> strings.light
                                ThemeMode.DARK -> strings.dark
                            }
                        },
                        onSelected = onThemeModeChange,
                    )
                }
            }
            item {
                SettingsCard(title = strings.accentTitle) {
                    ChoiceStrip(
                        options = AccentColor.entries.toList(),
                        selected = accentColor,
                        label = {
                            when (it) {
                                AccentColor.DYNAMIC -> strings.dynamic
                                AccentColor.EVERFOREST -> strings.everforest
                                AccentColor.SAGE -> strings.sage
                                AccentColor.CUSTOM -> strings.custom
                                AccentColor.BACKGROUND -> strings.accentBackground
                            }
                        },
                        leading = { color ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(color.swatch(customAccentRgb, preferences.backgroundSeedRgb()))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline
                                            .copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    ),
                            )
                        },
                        onSelected = onAccentColorChange,
                    )
                    when (accentColor) {
                        AccentColor.DYNAMIC -> Text(
                            text = strings.accentDynamicHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccentColor.CUSTOM -> CustomAccentPicker(
                            strings = strings,
                            customAccentRgb = customAccentRgb,
                            onCustomAccentChange = onCustomAccentChange,
                        )
                        AccentColor.BACKGROUND -> Text(
                            text = strings.backgroundAccentHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccentColor.EVERFOREST,
                        AccentColor.SAGE,
                        -> Unit
                    }
                }
            }
            item {
                SettingsCard(title = strings.backgroundTitle) {
                    BackgroundSettingsSection(
                        strings = strings,
                        backgroundImage = backgroundImage,
                        backgroundStrength = backgroundStrength,
                        onBackgroundStrengthChange = onBackgroundStrengthChange,
                        backgroundBlurEnabled = backgroundBlurEnabled,
                        onBackgroundBlurEnabledChange = onBackgroundBlurEnabledChange,
                        backgroundBlurStrength = backgroundBlurStrength,
                        onBackgroundBlurStrengthChange = onBackgroundBlurStrengthChange,
                        backgroundScale = backgroundScale,
                        backgroundOffsetX = backgroundOffsetX,
                        backgroundOffsetY = backgroundOffsetY,
                        onBackgroundTransformChange = onBackgroundTransformChange,
                        onAccentColorChange = onAccentColorChange,
                        onBackgroundChanged = onBackgroundChanged,
                    )
                }
            }
            item {
                SettingsCard(title = strings.homeSettingsTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { showSummaryConfigDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(strings.homeSummaryConfigTitle)
                        }
                        FilledTonalButton(
                            onClick = { showModuleOrderDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(strings.homeModulesOrderTitle)
                        }
                    }
                }
            }
            item {
                SettingsCard(title = strings.webDavSettingsCardTitle) {
                    WebDavBackupSettingsSection(
                        strings = strings,
                        onSettingsRestored = {
                            summaryEnabled = preferences.homeSummaryEnabled()
                            summaryItems = preferences.homeSummaryItems()
                            summaryCustomText = preferences.homeSummaryCustomText()
                            moduleOrder = preferences.homeModuleOrder()
                            onSettingsRestored()
                        },
                    )
                }
            }
            item {
                SettingsCard(title = strings.mockLocationTitle) {
                    Text(
                        text = strings.mockLocationSettingsHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showSummaryConfigDialog) {
        HomeSummaryConfigDialog(
            strings = strings,
            enabled = summaryEnabled,
            items = summaryItems,
            customText = summaryCustomText,
            onEnabledChange = {
                summaryEnabled = it
                preferences.saveHomeSummaryEnabled(it)
            },
            onItemsChange = {
                summaryItems = it
                preferences.saveHomeSummaryItems(it)
            },
            onCustomTextChange = {
                val trimmed = it.take(200)
                summaryCustomText = trimmed
                preferences.saveHomeSummaryCustomText(trimmed)
            },
            onDismiss = { showSummaryConfigDialog = false },
        )
    }

    if (showModuleOrderDialog) {
        HomeModuleOrderDialog(
            strings = strings,
            order = moduleOrder,
            onMove = { index, offset ->
                val target = (index + offset).coerceIn(0, moduleOrder.lastIndex)
                if (target != index) {
                    val mutable = moduleOrder.toMutableList()
                    val moved = mutable.removeAt(index)
                    mutable.add(target, moved)
                    moduleOrder = mutable
                    preferences.saveHomeModuleOrder(mutable)
                }
            },
            onReset = {
                moduleOrder = HomeModuleOrder.DEFAULT
                preferences.saveHomeModuleOrder(HomeModuleOrder.DEFAULT)
            },
            onDismiss = { showModuleOrderDialog = false },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun BackgroundSettingsSection(
    strings: ToolboxStrings,
    backgroundImage: ImageBitmap?,
    backgroundStrength: Float,
    onBackgroundStrengthChange: (Float) -> Unit,
    backgroundBlurEnabled: Boolean,
    onBackgroundBlurEnabledChange: (Boolean) -> Unit,
    backgroundBlurStrength: Float,
    onBackgroundBlurStrengthChange: (Float) -> Unit,
    backgroundScale: Float,
    backgroundOffsetX: Float,
    backgroundOffsetY: Float,
    onBackgroundTransformChange: (Float, Float, Float) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onBackgroundChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { AppPreferences(context) }
    var version by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        preview = withContext(Dispatchers.IO) {
            if (AppBackground.exists(context)) {
                AppBackground.loadPreview(context)?.asImageBitmap()
            } else {
                null
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val seed = withContext(Dispatchers.IO) { AppBackground.import(context, uri) }
            if (seed == null) {
                Toast.makeText(context, strings.backgroundPickFailed, Toast.LENGTH_LONG).show()
            } else {
                preferences.saveBackgroundSeedRgb(seed)
                preferences.saveAccentColor(AccentColor.BACKGROUND)
                preferences.saveBackgroundTransform(1f, 0f, 0f)
                onAccentColorChange(AccentColor.BACKGROUND)
                onBackgroundChanged()
                version++
            }
        }
    }

    // A delegated var cannot be smart-cast, so read it once.
    val shown = preview
    if (shown == null) {
        Text(
            text = strings.backgroundHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(strings.backgroundPick)
        }
    } else {
        var editingPosition by rememberSaveable { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .clipToBounds()
                .clickable(onClickLabel = strings.backgroundAdjustPosition) { editingPosition = true },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bounds = backgroundImageBounds(
                    rootW = size.width,
                    rootH = size.height,
                    imgW = shown.width.toFloat(),
                    imgH = shown.height.toFloat(),
                    bgScale = backgroundScale,
                    bgOffsetX = backgroundOffsetX,
                    bgOffsetY = backgroundOffsetY,
                )
                drawImage(
                    image = backgroundImage ?: shown,
                    dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(
                        bounds.width.roundToInt().coerceAtLeast(1),
                        bounds.height.roundToInt().coerceAtLeast(1),
                    ),
                )
            }
        }
        Text(
            text = strings.backgroundPositionHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { picker.launch("image/*") },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(strings.backgroundChange)
            }
            TextButton(
                onClick = {
                    editingPosition = true
                },
            ) {
                Text(strings.backgroundAdjustPosition)
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    AppBackground.clear(context)
                    preferences.saveBackgroundSeedRgb(0)
                    preferences.saveBackgroundTransform(1f, 0f, 0f)
                    onBackgroundChanged()
                    version++
                },
            ) {
                Text(strings.backgroundRemove)
            }
        }
        if (editingPosition) {
            BackgroundImageEditor(
                strings = strings,
                image = backgroundImage ?: shown,
                initial = BackgroundImageTransform(backgroundScale, backgroundOffsetX, backgroundOffsetY),
                onDismiss = { editingPosition = false },
                onSave = { transform ->
                    onBackgroundTransformChange(transform.scale, transform.offsetX, transform.offsetY)
                    editingPosition = false
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.backgroundStrengthLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(backgroundStrength * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = backgroundStrength,
            onValueChange = onBackgroundStrengthChange,
            valueRange = 0f..1f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.backgroundBlurLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = backgroundBlurEnabled,
                onCheckedChange = onBackgroundBlurEnabledChange,
            )
        }
        if (backgroundBlurEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.backgroundBlurStrengthLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(backgroundBlurStrength * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = backgroundBlurStrength,
                onValueChange = onBackgroundBlurStrengthChange,
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun <T> ChoiceStrip(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
                leadingIcon = leading?.let { content -> { content(option) } },
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                ),
            )
        }
        Spacer(modifier = Modifier.size(1.dp))
    }
}

@Composable
private fun CustomAccentPicker(
    strings: ToolboxStrings,
    customAccentRgb: Int,
    onCustomAccentChange: (Int) -> Unit,
) {
    var hexInput by remember(customAccentRgb) {
        mutableStateOf("%06X".format(Locale.US, customAccentRgb and 0xFFFFFF))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ACCENT_PRESET_COLORS.forEach { colorInt ->
                val isSelected =
                    (customAccentRgb and 0x00FFFFFF) == (colorInt and 0x00FFFFFF)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt or (0xFF shl 24)))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { onCustomAccentChange(colorInt) },
                )
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input
                parseHexColor(input)?.let(onCustomAccentChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.accentHexLabel) },
            prefix = { Text("#") },
            singleLine = true,
            isError = parseHexColor(hexInput) == null,
            supportingText = {
                if (parseHexColor(hexInput) == null) {
                    Text(strings.accentHexError)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebDavBackupSettingsSection(
    strings: ToolboxStrings,
    onSettingsRestored: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { WebDavConfigStore(context) }
    var version by remember { mutableIntStateOf(0) }
    var services by remember { mutableStateOf<List<WebDavServiceProfile>>(emptyList()) }
    var ledgerSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totpSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingProfile by remember { mutableStateOf<WebDavServiceProfile?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var deletingProfile by remember { mutableStateOf<WebDavServiceProfile?>(null) }

    LaunchedEffect(version) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                store.loadServices(),
                store.selectedServiceIds(BackupTool.LEDGER),
                store.selectedServiceIds(BackupTool.TOTP),
            )
        }
        services = loaded.first
        ledgerSelected = loaded.second
        totpSelected = loaded.third
    }

    Text(
        text = strings.webDavSettingsCardHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (services.isEmpty()) {
        Text(
            text = strings.webDavNoServiceHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            services.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                        .clickable {
                            editingProfile = profile
                            isCreating = false
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${profile.username} · ${profile.folderUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    editingProfile = profile
                                    isCreating = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(strings.editAction)
                            }
                            IconButton(
                                onClick = { deletingProfile = profile },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = strings.deleteAction,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    FilledTonalButton(
        onClick = {
            editingProfile = WebDavServiceProfile(
                id = UUID.randomUUID().toString(),
                name = "",
                folderUrl = "",
                username = "",
                password = "",
            )
            isCreating = true
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(strings.webDavAddServiceAction)
    }

    if (services.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = strings.webDavTargetLedgerLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            services.forEach { service ->
                val selected = service.id in ledgerSelected
                FilterChip(
                    selected = selected,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.setServiceSelected(BackupTool.LEDGER, service.id, !selected)
                            }
                            version++
                        }
                    },
                    label = { Text(service.name) },
                )
            }
        }

        Text(
            text = strings.webDavTargetTotpLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            services.forEach { service ->
                val selected = service.id in totpSelected
                FilterChip(
                    selected = selected,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.setServiceSelected(BackupTool.TOTP, service.id, !selected)
                            }
                            version++
                        }
                    },
                    label = { Text(service.name) },
                )
            }
        }
    }

    SettingsBackupSection(strings, services.firstOrNull(), onSettingsRestored)

    editingProfile?.let { draft ->
        WebDavServiceEditDialog(
            strings = strings,
            initial = draft,
            isNew = isCreating,
            onSave = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val defaults = if (isCreating && services.isEmpty()) {
                            setOf(BackupTool.LEDGER, BackupTool.TOTP)
                        } else if (isCreating) {
                            setOf(BackupTool.LEDGER, BackupTool.TOTP)
                        } else {
                            emptySet()
                        }
                        store.upsertService(updated, defaultTools = defaults)
                    }
                    editingProfile = null
                    version++
                    Toast.makeText(context, strings.webDavConfigSaved, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editingProfile = null },
        )
    }

    deletingProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text(strings.deleteAction) },
            text = { Text(strings.webDavDeleteServiceConfirm(target.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                store.deleteService(target.id)
                            }
                            deletingProfile = null
                            version++
                        }
                    },
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavServiceEditDialog(
    strings: ToolboxStrings,
    initial: WebDavServiceProfile,
    isNew: Boolean,
    onSave: (WebDavServiceProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nameText by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var urlText by rememberSaveable(initial.id) { mutableStateOf(initial.folderUrl) }
    var usernameText by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var passwordText by remember(initial.id) { mutableStateOf(initial.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var urlError by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var pendingTestProfile by remember { mutableStateOf<WebDavServiceProfile?>(null) }

    fun runConnectionTest(profile: WebDavServiceProfile) {
        testing = true
        testResult = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    LedgerWebDavSync(
                        client = profile.connectionClient(),
                        loadLocal = { emptyList() },
                        loadLocalTags = { emptyList() },
                        loadLocalAccounts = { emptyList() },
                        applyMerged = {},
                    ).testConnection()
                }
            }
            testing = false
            testResult = outcome.fold(
                onSuccess = { strings.webDavTestOk },
                onFailure = { err ->
                    val webErr = err as? WebDavException
                    strings.webDavError(
                        webErr?.failure ?: WebDavFailure.NETWORK,
                        webErr?.httpCode,
                    )
                },
            )
        }
    }

    fun buildValidProfile(): WebDavServiceProfile? {
        val normalized = WebDavPaths.normalizeFolderUrl(urlText)
        urlError = normalized == null
        if (normalized == null || usernameText.isBlank() || passwordText.isEmpty()) return null
        return WebDavServiceProfile(
            id = initial.id,
            name = nameText.trim().ifEmpty { WebDavPaths.defaultServiceName(normalized) },
            folderUrl = normalized,
            username = usernameText.trim(),
            password = passwordText,
        )
    }

    fun saveProfile() {
        if (testing) return
        buildValidProfile()?.let(onSave)
    }

    Dialog(
        onDismissRequest = { if (!testing) onDismiss() },
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
                        title = if (isNew) {
                            strings.webDavNewServiceTitle
                        } else {
                            strings.webDavEditServiceTitle
                        },
                        onBack = { if (!testing) onDismiss() },
                        backLabel = strings.cancelAction,
                        actions = {
                            TextButton(
                                onClick = ::saveProfile,
                                enabled = !testing,
                            ) {
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
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(strings.webDavServiceNameLabel) },
                    placeholder = { Text(strings.webDavServiceNameHint) },
                    singleLine = true,
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        urlError = false
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
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = usernameText,
                    onValueChange = { usernameText = it },
                    label = { Text(strings.webDavUsernameLabel) },
                    singleLine = true,
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    label = { Text(strings.webDavPasswordLabel) },
                    singleLine = true,
                    enabled = !testing,
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
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(
                    onClick = {
                        val profile = buildValidProfile() ?: return@FilledTonalButton
                        pendingTestProfile = profile
                    },
                    enabled = !testing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(strings.webDavTestConnection)
                    }
                }
                testResult?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg == strings.webDavTestOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                }
                }
            }
        }
    }

    pendingTestProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingTestProfile = null },
            title = { Text(strings.webDavConfirmTestTitle) },
            text = { Text(strings.webDavConfirmTestMessage(profile.folderUrl)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingTestProfile = null
                        runConnectionTest(profile)
                    },
                ) {
                    Text(strings.webDavTestConnection)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTestProfile = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}
