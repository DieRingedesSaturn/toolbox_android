package com.example.toolbox.ui

import android.icu.text.BreakIterator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.toolbox.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val acrylic = LocalAcrylicBackground.current
    TopAppBar(
        modifier = if (acrylic.enabled) {
            modifier.acrylicSurface(
                shape = RectangleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                showBorder = false,
            )
        } else {
            modifier
        },
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = backLabel,
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (acrylic.enabled) Color.Transparent else MaterialTheme.colorScheme.background,
            scrolledContainerColor = if (acrylic.enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/**
 * Draws an acrylic frosted-glass backdrop aligned with the active custom background image,
 * or falls back to a solid [containerColor] background when no custom background is active.
 */
fun Modifier.acrylicSurface(
    shape: Shape,
    containerColor: Color,
    showBorder: Boolean = false,
): Modifier = composed {
    val acrylic = LocalAcrylicBackground.current
    if (!acrylic.enabled) {
        return@composed background(containerColor, shape)
    }
    val bmp = acrylic.blurredBitmap
    if (!acrylic.blurEnabled || bmp == null || acrylic.rootSize.width <= 0 || acrylic.rootSize.height <= 0) {
        return@composed background(
            containerColor.copy(alpha = min(containerColor.alpha, 0.86f)),
            shape,
        )
    }

    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    var compSize by remember { mutableStateOf(IntSize.Zero) }
    val borderColor = if (acrylic.darkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val tintColor = containerColor.copy(alpha = min(containerColor.alpha, 0.72f))

    this
        .onGloballyPositioned { coords ->
            posInRoot = coords.positionInRoot()
            compSize = coords.size
        }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            clipPath(clip) {
                val rootW = acrylic.rootSize.width.toFloat()
                val rootH = acrylic.rootSize.height.toFloat()
                val imgW = bmp.width.toFloat()
                val imgH = bmp.height.toFloat()
                val bounds = backgroundImageBounds(
                    rootW = rootW,
                    rootH = rootH,
                    imgW = imgW,
                    imgH = imgH,
                    bgScale = acrylic.bgScale,
                    bgOffsetX = acrylic.bgOffsetX,
                    bgOffsetY = acrylic.bgOffsetY,
                )
                val scale = bounds.scale
                val offsetX = bounds.left
                val offsetY = bounds.top

                val cardX = posInRoot.x
                val cardY = posInRoot.y
                val cardW = compSize.width.toFloat().takeIf { it > 0f } ?: size.width
                val cardH = compSize.height.toFloat().takeIf { it > 0f } ?: size.height

                val visLeft = max(0f, cardX)
                val visTop = max(0f, cardY)
                val visRight = min(rootW, cardX + cardW)
                val visBottom = min(rootH, cardY + cardH)

                if (visRight > visLeft && visBottom > visTop) {
                    val srcLeft = ((visLeft - offsetX) / scale).coerceIn(0f, imgW - 1f)
                    val srcTop = ((visTop - offsetY) / scale).coerceIn(0f, imgH - 1f)
                    val srcRight = ((visRight - offsetX) / scale).coerceIn(srcLeft + 1f, imgW)
                    val srcBottom = ((visBottom - offsetY) / scale).coerceIn(srcTop + 1f, imgH)

                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset(srcLeft.roundToInt(), srcTop.roundToInt()),
                        srcSize = IntSize(
                            (srcRight - srcLeft).roundToInt().coerceAtLeast(1),
                            (srcBottom - srcTop).roundToInt().coerceAtLeast(1),
                        ),
                        dstOffset = IntOffset(
                            (visLeft - cardX).roundToInt(),
                            (visTop - cardY).roundToInt(),
                        ),
                        dstSize = IntSize(
                            (visRight - visLeft).roundToInt().coerceAtLeast(1),
                            (visBottom - visTop).roundToInt().coerceAtLeast(1),
                        ),
                    )
                }
                drawRect(tintColor)
            }
            if (showBorder) {
                drawOutline(
                    outline = outline,
                    color = borderColor,
                    style = Stroke(width = 0.85.dp.toPx()),
                )
            }
        }
}

@Composable
fun ToolboxCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = contentColorFor(containerColor),
    showBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.acrylicSurface(
            shape = shape,
            containerColor = containerColor,
            showBorder = showBorder,
        ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
        content = content,
    )
}

@Composable
fun ToolboxFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    border: BorderStroke? = null,
) {
    val acrylic = LocalAcrylicBackground.current
    val chipShape = MaterialTheme.shapes.small
    // Keep touch-target margins outside the glass, including callers that use compact chips.
    val minimumSize = LocalMinimumInteractiveComponentSize.current
    val verticalInset = if (minimumSize.value.isFinite()) {
        ((minimumSize - 32.dp) / 2).coerceAtLeast(0.dp)
    } else {
        0.dp
    }
    val bodyModifier = modifier.padding(vertical = verticalInset).then(
        if (acrylic.enabled && !selected) {
            Modifier.clip(chipShape).acrylicSurface(
                shape = chipShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            Modifier
        },
    )
    val effectiveColors = if (acrylic.enabled && !selected) colors.copy(containerColor = Color.Transparent) else colors
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = bodyModifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            shape = chipShape,
            border = border,
            colors = effectiveColors,
        )
    }
}

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ToolboxCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    copyAction: String? = null,
    onCopy: (String) -> Unit,
) {
    val tabularBody = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = copyAction) { onCopy(value) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.6f),
            style = tabularBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun IssueActionRow(
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * Keeps long-form lists readable on tablets by growing the side padding
 * instead of stretching cards and text across the whole window.
 */
@Composable
fun readableContentPadding(
    maxWidth: Dp = 840.dp,
    vertical: Dp = 16.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val horizontal = readableHorizontalPadding(maxWidth)
    return PaddingValues(
        start = horizontal,
        top = vertical,
        end = horizontal,
        bottom = vertical + extraBottom,
    )
}

/** The same idea for full-screen editors that apply their own edge padding. */
@Composable
fun readableHorizontalPadding(maxWidth: Dp = 720.dp): Dp {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    return ((width - maxWidth) / 2).coerceAtLeast(16.dp)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
}

/**
 * The last grapheme cluster of [input] (trimmed), e.g. a single emoji with
 * its variation selector. Used to keep single-emoji fields correct for
 * multi-codepoint glyphs like 🖥️ — typing a new one replaces the old.
 */
fun lastGrapheme(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return ""
    val it = BreakIterator.getCharacterInstance().apply { setText(text) }
    var last = ""
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        last = text.substring(start, end)
        start = end
        end = it.next()
    }
    return last
}

/** Opaque dropdown menu matching app card corners even when a custom background image is active. */
@Composable
fun ToolboxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
        content = content,
    )
}
