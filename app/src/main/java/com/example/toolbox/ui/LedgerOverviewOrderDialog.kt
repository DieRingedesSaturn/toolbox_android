package com.example.toolbox.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.toolbox.R
import kotlin.math.roundToInt

private val OverviewOrderRowHeight = 56.dp

@Composable
internal fun LedgerOverviewOrderDialog(
    strings: ToolboxStrings,
    order: List<LedgerOverviewCard>,
    onMove: (index: Int, offset: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { OverviewOrderRowHeight.toPx() }
    var draggingFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val visualOffset = draggingFrom?.let { from ->
        dragOffset.coerceIn(
            -from * rowHeightPx,
            (order.lastIndex - from) * rowHeightPx,
        )
    } ?: 0f
    val targetIndex = draggingFrom?.let { from ->
        (from + (visualOffset / rowHeightPx).roundToInt()).coerceIn(0, order.lastIndex)
    }

    fun finishDrag() {
        val from = draggingFrom
        if (from != null) {
            val clamped = dragOffset.coerceIn(
                -from * rowHeightPx,
                (order.lastIndex - from) * rowHeightPx,
            )
            val to = (from + (clamped / rowHeightPx).roundToInt())
                .coerceIn(0, order.lastIndex)
            if (to != from) onMove(from, to - from)
        }
        draggingFrom = null
        dragOffset = 0f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.ledgerOverviewOrderTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.ledgerOverviewOrderHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    order.forEachIndexed { index, card ->
                        val isDragging = draggingFrom == index
                        val from = draggingFrom
                        val target = targetIndex
                        val shift = when {
                            from == null || target == null -> 0f
                            index == from -> visualOffset
                            target > from && index in (from + 1)..target -> -rowHeightPx
                            target < from && index in target until from -> rowHeightPx
                            else -> 0f
                        }
                        val animatedShift by animateFloatAsState(
                            targetValue = if (isDragging) 0f else shift,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "overviewOrderRowShift",
                        )
                        val rowOffset = if (isDragging) visualOffset else animatedShift
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(OverviewOrderRowHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset { IntOffset(0, rowOffset.roundToInt()) }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .pointerInput(order) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingFrom = index
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = { finishDrag() },
                                        onDragCancel = {
                                            draggingFrom = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                                .semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction(
                                                    strings.ledgerOverviewMoveUp,
                                                ) {
                                                    onMove(index, -1)
                                                    true
                                                },
                                            )
                                        }
                                        if (index < order.lastIndex) {
                                            add(
                                                CustomAccessibilityAction(
                                                    strings.ledgerOverviewMoveDown,
                                                ) {
                                                    onMove(index, 1)
                                                    true
                                                },
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.ledgerOverviewCardName(card),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = strings.ledgerOverviewOrderDrag,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.doneAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(strings.ledgerOverviewOrderReset)
            }
        },
    )
}
