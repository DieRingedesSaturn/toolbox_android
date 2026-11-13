package com.example.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.roundToInt

internal data class BackgroundImageTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun transformed(
        viewport: IntSize,
        image: IntSize,
        centroid: Offset,
        pan: Offset,
        zoom: Float,
    ): BackgroundImageTransform {
        if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0 ||
            !zoom.isFinite() || zoom <= 0f
        ) return this
        val nextScale = (scale * zoom).coerceIn(1f, 3f)
        val before = backgroundImageBounds(viewport.width.toFloat(), viewport.height.toFloat(),
            image.width.toFloat(), image.height.toFloat(), scale, offsetX, offsetY)
        val after = backgroundImageBounds(viewport.width.toFloat(), viewport.height.toFloat(),
            image.width.toFloat(), image.height.toFloat(), nextScale, 0f, 0f)
        val ratio = nextScale / scale
        val left = centroid.x + pan.x - (centroid.x - before.left) * ratio
        val top = centroid.y + pan.y - (centroid.y - before.top) * ratio
        val maxShiftX = (after.width - viewport.width) / 2f
        val maxShiftY = (after.height - viewport.height) / 2f
        return BackgroundImageTransform(
            scale = nextScale,
            offsetX = if (maxShiftX > 0f) ((left - after.left) / maxShiftX).coerceIn(-1f, 1f) else 0f,
            offsetY = if (maxShiftY > 0f) ((top - after.top) / maxShiftY).coerceIn(-1f, 1f) else 0f,
        )
    }
}

/** Edits a local draft; only Save publishes and persists a transform. */
@Composable
internal fun BackgroundImageEditor(
    strings: ToolboxStrings,
    image: ImageBitmap,
    initial: BackgroundImageTransform,
    onDismiss: () -> Unit,
    onSave: (BackgroundImageTransform) -> Unit,
) {
    val saver = remember {
        listSaver<BackgroundImageTransform, Float>(
            save = { listOf(it.scale, it.offsetX, it.offsetY) },
            restore = { BackgroundImageTransform(it[0], it[1], it[2]) },
        )
    }
    var transform by rememberSaveable(image, stateSaver = saver) { mutableStateOf(initial) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
                .pointerInput(image, viewport) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Read the current draft on every event; never capture a starting offset.
                        transform = transform.transformed(viewport, IntSize(image.width, image.height), centroid, pan, zoom)
                    }
                }) {
                val bounds = backgroundImageBounds(size.width, size.height, image.width.toFloat(),
                    image.height.toFloat(), transform.scale, transform.offsetX, transform.offsetY)
                drawImage(image, dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)))
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).statusBarsPadding()
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
                Text(strings.backgroundPositionTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onSave(transform) }) { Text(strings.saveAction) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { transform = BackgroundImageTransform() }) { Text(strings.backgroundResetTransform) }
                    Text(String.format(Locale.US, "%.2f×", transform.scale))
                }
                Text(strings.backgroundGestureHint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
