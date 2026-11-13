package com.example.toolbox.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import com.example.toolbox.R
import java.util.Locale
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
    onBackgroundStrengthPreview: (Float) -> Unit,
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
    isActive: Boolean = true,
    onSubmenuChange: (Boolean) -> Unit = {},
    onHomePrefsChange: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }
    var summaryEnabled by remember { mutableStateOf(preferences.homeSummaryEnabled()) }
    var summaryItems by remember { mutableStateOf(preferences.homeSummaryItems()) }
    var summaryCustomText by remember { mutableStateOf(preferences.homeSummaryCustomText()) }
    var gridLayoutRaw by remember { mutableStateOf(preferences.homeGridLayout()) }
    var showSummaryConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showResetGridDialog by rememberSaveable { mutableStateOf(false) }
    val gridLayout = remember(gridLayoutRaw, summaryEnabled) {
        HomeGridLayout.parse(
            gridLayoutRaw,
            preferences.homeModuleOrder(),
            2,
            summaryExpected = summaryEnabled,
        )
    }

    fun saveGridLayout(next: HomeGridLayout) {
        val raw = next.format()
        gridLayoutRaw = raw
        preferences.saveHomeGridLayout(raw)
        preferences.saveHomeModuleOrder(next.readingOrder())
        onHomePrefsChange()
    }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryName?.let { name ->
        SettingsCategory.entries.firstOrNull { it.name == name }
    }
    LaunchedEffect(categoryName) {
        onSubmenuChange(categoryName != null)
    }
    BackHandler(enabled = isActive) {
        if (categoryName != null) categoryName = null else onBack()
    }
    AnimatedContent(
        targetState = category,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
            val enterFade = tween<Float>(180)
            val exitFade = tween<Float>(150)
            val forward = initialState == null && targetState != null
            val backward = initialState != null && targetState == null
            when {
                forward -> (slideInHorizontally(slide) { it } + fadeIn(enterFade))
                    .togetherWith(
                        slideOutHorizontally(slide) { -it / 5 } + fadeOut(exitFade),
                    )
                backward -> (slideInHorizontally(slide) { -it / 5 } + fadeIn(enterFade))
                    .togetherWith(
                        slideOutHorizontally(slide) { it } + fadeOut(exitFade),
                    )
                else -> fadeIn(enterFade).togetherWith(fadeOut(exitFade))
            }.apply {
                targetContentZIndex = if (targetState != null) 1f else 0f
            }
        },
        label = "settingsCategory",
    ) { target ->
        val pageTitle = when (target) {
            null -> strings.settings
            SettingsCategory.APPEARANCE -> strings.settingsAppearanceTitle
            SettingsCategory.HOME -> strings.homeSettingsTitle
            SettingsCategory.BACKUP -> strings.webDavSettingsCardTitle
            SettingsCategory.ABOUT -> strings.about
        }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                ToolboxTopBar(
                    title = pageTitle,
                    onBack = if (target != null) {
                        { categoryName = null }
                    } else {
                        null
                    },
                    backLabel = strings.back,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            when (target) {
            null -> SettingsHub(
                strings = strings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpen = { categoryName = it.name },
            )
            SettingsCategory.APPEARANCE -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = readableContentPadding(
                    extraBottom = HomePagerIndicatorClearance,
                ),
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
                        onBackgroundStrengthPreview = onBackgroundStrengthPreview,
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
            }
            SettingsCategory.HOME -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = readableContentPadding(
                    extraBottom = HomePagerIndicatorClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                SettingsCard(title = strings.homeSettingsTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { showSummaryConfigDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = strings.homeSummaryConfigAction,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton(
                            onClick = { showResetGridDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = strings.homeGridReset,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    BlankRowStepper(
                        label = strings.homeGridTopBlank,
                        value = gridLayout.topBlankRows,
                        onDecrease = {
                            saveGridLayout(gridLayout.withTopBlankRows(gridLayout.topBlankRows - 1))
                        },
                        onIncrease = {
                            saveGridLayout(gridLayout.withTopBlankRows(gridLayout.topBlankRows + 1))
                        },
                    )
                    BlankRowStepper(
                        label = strings.homeGridBottomBlank,
                        value = gridLayout.bottomBlankRows,
                        onDecrease = {
                            saveGridLayout(
                                gridLayout.withBottomBlankRows(gridLayout.bottomBlankRows - 1),
                            )
                        },
                        onIncrease = {
                            saveGridLayout(
                                gridLayout.withBottomBlankRows(gridLayout.bottomBlankRows + 1),
                            )
                        },
                    )
                }
            }
            }
            SettingsCategory.BACKUP -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = readableContentPadding(
                    extraBottom = HomePagerIndicatorClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                SettingsCard(title = strings.webDavSettingsCardTitle) {
                    WebDavBackupSettingsSection(
                        strings = strings,
                        onSettingsRestored = {
                            summaryEnabled = preferences.homeSummaryEnabled()
                            summaryItems = preferences.homeSummaryItems()
                            summaryCustomText = preferences.homeSummaryCustomText()
                            gridLayoutRaw = preferences.homeGridLayout()
                            onHomePrefsChange()
                            onSettingsRestored()
                        },
                    )
                }
            }
            }
            SettingsCategory.ABOUT -> AboutSettingsContent(
                strings = strings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            }
        }
    }

    if (showSummaryConfigDialog) {
        HomeSummaryConfigDialog(
            strings = strings,
            enabled = summaryEnabled,
            items = summaryItems,
            customText = summaryCustomText,
            onEnabledChange = { enabled ->
                summaryEnabled = enabled
                preferences.saveHomeSummaryEnabled(enabled)
                val adjusted = when {
                    enabled && gridLayout.summaryRow == null -> gridLayout.withSummaryRow(0)
                    !enabled && gridLayout.summaryRow != null -> gridLayout.withoutSummary()
                    else -> gridLayout
                }
                saveGridLayout(adjusted)
            },
            onItemsChange = {
                summaryItems = it
                preferences.saveHomeSummaryItems(it)
                onHomePrefsChange()
            },
            onCustomTextChange = {
                val trimmed = it.take(200)
                summaryCustomText = trimmed
                preferences.saveHomeSummaryCustomText(trimmed)
                onHomePrefsChange()
            },
            onDismiss = { showSummaryConfigDialog = false },
        )
    }

    if (showResetGridDialog) {
        AlertDialog(
            onDismissRequest = { showResetGridDialog = false },
            title = { Text(strings.homeGridReset) },
            text = { Text(strings.homeGridResetMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetGridDialog = false
                        preferences.clearHomeGridLayout()
                        preferences.saveHomeModuleOrder(HomeModuleOrder.DEFAULT)
                        gridLayoutRaw = null
                        onHomePrefsChange()
                    },
                ) {
                    Text(strings.homeGridReset)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetGridDialog = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

private enum class SettingsCategory {
    APPEARANCE,
    HOME,
    BACKUP,
    ABOUT,
}

@Composable
private fun SettingsHub(
    strings: ToolboxStrings,
    onOpen: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = readableContentPadding(
            extraBottom = HomePagerIndicatorClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsCategory.entries.forEachIndexed { index, category ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        val (title, summary) = when (category) {
                            SettingsCategory.APPEARANCE ->
                                strings.settingsAppearanceTitle to strings.settingsAppearanceSummary
                            SettingsCategory.HOME ->
                                strings.homeSettingsTitle to strings.settingsHomeSummary
                            SettingsCategory.BACKUP ->
                                strings.webDavSettingsCardTitle to strings.settingsBackupSummary
                            SettingsCategory.ABOUT ->
                                strings.about to strings.settingsAboutSummary
                        }
                        SettingsCategoryRow(
                            title = title,
                            summary = summary,
                            onClick = { onOpen(category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onBackgroundStrengthPreview: (Float) -> Unit,
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
    // Sliders keep a local draft and persist once the finger is lifted.
    var strengthDraft by remember(backgroundStrength) { mutableFloatStateOf(backgroundStrength) }
    var blurStrengthDraft by remember(backgroundBlurStrength) {
        mutableFloatStateOf(backgroundBlurStrength)
    }
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
                text = "${(strengthDraft * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = strengthDraft,
            onValueChange = {
                strengthDraft = it
                onBackgroundStrengthPreview(it)
            },
            onValueChangeFinished = { onBackgroundStrengthChange(strengthDraft) },
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
                    text = "${(blurStrengthDraft * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = blurStrengthDraft,
                onValueChange = { blurStrengthDraft = it },
                onValueChangeFinished = { onBackgroundBlurStrengthChange(blurStrengthDraft) },
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

@Composable
private fun BlankRowStepper(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDecrease, enabled = value > 0) {
            Text("−")
        }
        Text(
            text = value.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(
            onClick = onIncrease,
            enabled = value < HomeGridLayout.MAX_BLANK_ROWS,
        ) {
            Text("+")
        }
    }
}
