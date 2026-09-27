package com.example.toolbox.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerSyncStatus
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.LedgerTags
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Swatches offered when creating or editing a tag. */
internal val TAG_COLOR_PALETTE = listOf(
    0xFFEFEAD2.toInt(), 0xFFDBBC7F.toInt(), 0xFFE69875.toInt(),
    0xFFE67C73.toInt(), 0xFFA7C080.toInt(), 0xFF83C092.toInt(),
    0xFF7FBBB3.toInt(), 0xFF67B0E8.toInt(), 0xFFD699B6.toInt(),
    0xFF9DA9A0.toInt(), 0xFFCBBEB3.toInt(), 0xFF859289.toInt(),
)

internal fun newLedgerTag(
    name: String,
    colorArgb: Int,
    emoji: String,
    sortOrder: Int,
): LedgerTag = LedgerTag(
    uuid = UUID.randomUUID().toString(),
    name = name.trim(),
    colorArgb = colorArgb,
    emoji = lastGrapheme(emoji),
    sortOrder = sortOrder,
    createdAtMillis = System.currentTimeMillis(),
    updatedAtMillis = System.currentTimeMillis(),
    syncStatus = LedgerSyncStatus.PENDING_PUSH,
)

/** Create/edit dialog for a single tag: name + color + optional emoji. */
@Composable
internal fun TagEditDialog(
    strings: ToolboxStrings,
    existing: List<LedgerTag>,
    editing: LedgerTag?,
    onDismiss: () -> Unit,
    onSave: (LedgerTag) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var emoji by remember { mutableStateOf(editing?.emoji ?: "") }
    var presetName by remember { mutableStateOf<String?>(null) }
    var colorIdx by remember {
        mutableIntStateOf(
            TAG_COLOR_PALETTE.indexOf(editing?.colorArgb)
                .takeIf { it >= 0 } ?: 1,
        )
    }
    val trimmed = name.trim()
    val duplicate = existing.any {
        it.deletedAtMillis == null &&
            it.uuid != editing?.uuid &&
            it.name.equals(trimmed, ignoreCase = true)
    }
    val valid = trimmed.isNotEmpty() && !duplicate
    val liveNames = remember(existing) {
        existing.filter { it.deletedAtMillis == null }
            .mapTo(HashSet()) { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editing == null) {
                    strings.newTagChip.removePrefix("＋ ").removePrefix("+ ")
                } else {
                    strings.editAction
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.tagNameLabel) },
                    singleLine = true,
                    isError = name.isNotEmpty() && (trimmed.isEmpty() || duplicate),
                    supportingText = if (name.isNotEmpty() && duplicate) {
                        { Text(strings.tagNameDuplicate) }
                    } else if (name.isNotEmpty() && trimmed.isEmpty()) {
                        { Text(strings.tagNameRequired) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = lastGrapheme(it) },
                    label = { Text(strings.tagEmojiLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Preset chips grouped by section; tapping sets the emoji and
                // fills the name while it's blank or matches the last preset.
                Text(
                    text = strings.recommendedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for ((group, presets) in strings.tagPresets) {
                    val visible = presets.filter { it.second !in liveNames }
                    if (visible.isEmpty()) continue
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        visible.forEach { (glyph, label) ->
                            FilterChip(
                                selected = emoji == glyph,
                                onClick = {
                                    emoji = glyph
                                    if (name.isBlank() || name.trim() == presetName) {
                                        name = label
                                    }
                                    presetName = label
                                },
                                label = { Text("$glyph $label") },
                            )
                        }
                    }
                }

                Text(
                    text = strings.tagColorLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_COLOR_PALETTE.take(6).forEachIndexed { index, argb ->
                        ColorSwatch(argb, index == colorIdx) { colorIdx = index }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_COLOR_PALETTE.drop(6).forEachIndexed { index, argb ->
                        ColorSwatch(argb, index + 6 == colorIdx) { colorIdx = index + 6 }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tag = editing?.copy(
                        name = trimmed,
                        emoji = emoji.trim(),
                        colorArgb = TAG_COLOR_PALETTE[colorIdx],
                    ) ?: newLedgerTag(
                        name = trimmed,
                        colorArgb = TAG_COLOR_PALETTE[colorIdx],
                        emoji = emoji,
                        sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                    )
                    onSave(tag)
                },
                enabled = valid,
            ) {
                Text(if (editing == null) strings.addTagAction else strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}

@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

/** Editor-facing small create dialog (name + color). */
@Composable
internal fun NewTagDialog(
    strings: ToolboxStrings,
    existing: List<LedgerTag>,
    onDismiss: () -> Unit,
    onCreate: (LedgerTag) -> Unit,
) {
    TagEditDialog(
        strings = strings,
        existing = existing,
        editing = null,
        onDismiss = onDismiss,
        onSave = onCreate,
    )
}

/**
 * Full-screen tag management: live tags in sort order with ▲/▼ reorder,
 * per-tag counts and totals, edit/merge/delete overflow, and a drill-in
 * detail view with stats and the tag's entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerTagsDialog(
    strings: ToolboxStrings,
    tags: List<LedgerTag>,
    entries: List<LedgerEntry>,
    accounts: List<LedgerAccount> = emptyList(),
    onUpsert: (LedgerTag) -> Unit,
    onDelete: (LedgerTag) -> Unit,
    onReorder: (List<String>) -> Unit = {},
    onMerge: (String, String) -> Unit = { _, _ -> },
    onOpenEntry: ((LedgerEntry) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var editingTag by remember { mutableStateOf<LedgerTag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LedgerTag?>(null) }
    var mergeSource by remember { mutableStateOf<LedgerTag?>(null) }
    var mergeTarget by remember { mutableStateOf<LedgerTag?>(null) }
    var detailTag by remember { mutableStateOf<LedgerTag?>(null) }

    val zone = ZoneId.systemDefault()
    val month = remember { YearMonth.now(zone) }
    val liveTags = tags
        .filter { it.deletedAtMillis == null }
        .sortedWith(compareBy({ it.sortOrder }, { it.name }))
    val statsByTag = remember(entries, liveTags) {
        liveTags.associate { it.uuid to LedgerTags.stats(entries, it.uuid, month, zone) }
    }

    BackHandler {
        if (detailTag != null) {
            detailTag = null
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (detailTag != null) detailTag = null else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                detailTag?.let {
                                    "${it.emoji.ifBlank { "" }} ${it.name}".trim()
                                } ?: strings.manageTagsMenu,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (detailTag != null) {
                                        detailTag = null
                                    } else {
                                        onDismiss()
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (detailTag != null) {
                                            R.drawable.ic_arrow_back
                                        } else {
                                            R.drawable.ic_close
                                        },
                                    ),
                                    contentDescription = strings.back,
                                )
                            }
                        },
                        actions = {
                            if (detailTag == null) {
                                TextButton(onClick = { creating = true }) {
                                    Text(strings.addTagAction)
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                val detail = detailTag
                if (detail != null) {
                    TagDetailContent(
                        strings = strings,
                        tag = detail,
                        entries = entries,
                        tags = tags,
                        accounts = accounts,
                        stats = statsByTag[detail.uuid],
                        onOpenEntry = onOpenEntry,
                        onCopyEntry = {},
                        modifier = Modifier.padding(padding),
                    )
                } else if (liveTags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.tagsEmptyHint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            liveTags,
                            key = { it.uuid },
                        ) { tag ->
                            val index = liveTags.indexOfFirst { it.uuid == tag.uuid }
                            TagManagementRow(
                                strings = strings,
                                tag = tag,
                                stats = statsByTag[tag.uuid],
                                canMoveUp = index > 0,
                                canMoveDown = index < liveTags.lastIndex,
                                onMoveUp = {
                                    val next = liveTags.toMutableList()
                                    next.add(index - 1, next.removeAt(index))
                                    onReorder(next.map { it.uuid })
                                },
                                onMoveDown = {
                                    val next = liveTags.toMutableList()
                                    next.add(index + 1, next.removeAt(index))
                                    onReorder(next.map { it.uuid })
                                },
                                onEdit = { editingTag = tag },
                                onMerge = { mergeSource = tag },
                                onDelete = { confirmDelete = tag },
                                onOpen = { detailTag = tag },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TagEditDialog(
            strings = strings,
            existing = tags,
            editing = null,
            onDismiss = { creating = false },
            onSave = {
                onUpsert(it)
                creating = false
            },
        )
    }
    editingTag?.let { tag ->
        TagEditDialog(
            strings = strings,
            existing = tags,
            editing = tag,
            onDismiss = { editingTag = null },
            onSave = {
                onUpsert(it)
                editingTag = null
            },
        )
    }
    mergeSource?.let { source ->
        val others = liveTags.filter { it.uuid != source.uuid }
        AlertDialog(
            onDismissRequest = { mergeSource = null },
            title = { Text(strings.mergeIntoAction) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = strings.mergeTargetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    others.forEach { candidate ->
                        Text(
                            text = "${candidate.emoji.ifBlank { "🏷" }} ${candidate.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    mergeTarget = candidate
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mergeSource = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
        mergeTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { mergeTarget = null },
                title = { Text(strings.mergeIntoAction) },
                text = {
                    Text(
                        strings.mergeTagConfirm(
                            source.name,
                            target.name,
                            statsByTag[source.uuid]?.entryCount ?: 0,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onMerge(source.uuid, target.uuid)
                            mergeSource = null
                            mergeTarget = null
                        },
                    ) {
                        Text(strings.confirmAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mergeTarget = null }) {
                        Text(strings.cancelAction)
                    }
                },
            )
        }
    }
    confirmDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(strings.deleteAction) },
            text = {
                Text(
                    strings.deleteTagConfirm(
                        statsByTag[tag.uuid]?.entryCount ?: 0,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(tag)
                        confirmDelete = null
                    },
                ) {
                    Text(strings.deleteAction, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

@Composable
private fun TagManagementRow(
    strings: ToolboxStrings,
    tag: LedgerTag,
    stats: LedgerTags.TagStats?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(tag.colorArgb)),
            contentAlignment = Alignment.Center,
        ) {
            if (tag.emoji.isNotBlank()) {
                Text(tag.emoji, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = strings.tagRowCaption(
                    stats?.entryCount ?: 0,
                    LedgerCalculator.formatCurrency(
                        (stats?.totalExpenseCents ?: 0L) / 100.0,
                    ),
                    stats?.totalIncomeCents?.takeIf { it > 0 }?.let {
                        LedgerCalculator.formatCurrency(it / 100.0)
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = "↑",
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_downward),
                contentDescription = "↓",
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = strings.moreOptions,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.editAction) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(strings.mergeIntoAction) },
                    onClick = { menuOpen = false; onMerge() },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            strings.deleteAction,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** Tag detail: header stats + every entry carrying the tag, newest first. */
@Composable
private fun TagDetailContent(
    strings: ToolboxStrings,
    tag: LedgerTag,
    entries: List<LedgerEntry>,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    stats: LedgerTags.TagStats?,
    onOpenEntry: ((LedgerEntry) -> Unit)?,
    onCopyEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val tagEntries = remember(entries, tag.uuid) {
        entries.filter {
            it.deletedAtMillis == null && tag.uuid in it.tagUuids
        }.sortedByDescending { it.occurredAtMillis }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(tag.colorArgb).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tag.emoji.ifBlank { "🏷" })
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    stats?.let {
                        Text(
                            text = buildString {
                                append(
                                    strings.tagRowCaption(
                                        it.entryCount,
                                        LedgerCalculator.formatCurrency(
                                            it.totalExpenseCents / 100.0,
                                        ),
                                        it.totalIncomeCents.takeIf { v -> v > 0 }
                                            ?.let { v ->
                                                LedgerCalculator.formatCurrency(v / 100.0)
                                            },
                                    ),
                                )
                                append(
                                    "\n${strings.thisMonthLabel}: " +
                                        LedgerCalculator.formatCurrency(
                                            it.monthExpenseCents / 100.0,
                                        ),
                                )
                                if (it.monthIncomeCents > 0) {
                                    append(
                                        " · " + LedgerCalculator.formatCurrency(
                                            it.monthIncomeCents / 100.0,
                                        ),
                                    )
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        var lastMonth: YearMonth? = null
        tagEntries.forEach { entry ->
            val entryMonth = YearMonth.from(
                Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone),
            )
            if (entryMonth != lastMonth) {
                lastMonth = entryMonth
                item(key = "month-${entry.uuid}") {
                    Text(
                        text = strings.formatLedgerMonth(entryMonth),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item(key = entry.uuid) {
                LedgerEntryRow(
                    strings = strings,
                    entry = entry,
                    tags = tags,
                    accounts = accounts,
                    onOpen = { onOpenEntry?.invoke(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
