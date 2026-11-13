package com.example.toolbox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageEditorTest {
    private val viewport = IntSize(200, 200)

    @Test
    fun successiveDragEventsAccumulateInsteadOfReturningToStartingPosition() {
        var transform = BackgroundImageTransform(scale = 2f)
        repeat(10) {
            transform = transform.transformed(viewport, viewport, Offset(100f, 100f), Offset(5f, -2f), 1f)
        }
        assertEquals(0.5f, transform.offsetX, 0.0001f)
        assertEquals(-0.2f, transform.offsetY, 0.0001f)
    }

    @Test
    fun pinchKeepsTheImagePointUnderTheFingers() {
        val transform = BackgroundImageTransform().transformed(viewport, viewport,
            Offset(50f, 75f), Offset.Zero, 2f)
        assertEquals(2f, transform.scale, 0f)
        assertEquals(0.5f, transform.offsetX, 0.0001f)
        assertEquals(0.25f, transform.offsetY, 0.0001f)
        val bounds = backgroundImageBounds(200f, 200f, 200f, 200f,
            transform.scale, transform.offsetX, transform.offsetY)
        assertEquals(50f, (50f - bounds.left) / bounds.scale, 0.0001f)
        assertEquals(75f, (75f - bounds.top) / bounds.scale, 0.0001f)
    }

    @Test
    fun reachingZoomLimitUsesTheActualScaleRatio() {
        val transform = BackgroundImageTransform(scale = 2f).transformed(viewport, viewport,
            Offset(40f, 100f), Offset.Zero, 10f)
        assertEquals(3f, transform.scale, 0f)
        assertEquals(0.15f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun anAxisWithoutOverflowCannotAccumulateHiddenPositionChanges() {
        val transform = BackgroundImageTransform().transformed(IntSize(100, 200), IntSize(100, 400),
            Offset(50f, 100f), Offset(1000f, 30f), 1f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0.3f, transform.offsetY, 0.0001f)
    }

    @Test
    fun reversingAtAnEdgeMovesImmediatelyWithoutOverscrollBounce() {
        val edge = BackgroundImageTransform(scale = 2f).transformed(viewport, viewport,
            Offset(100f, 100f), Offset(10000f, -10000f), 1f)
        assertEquals(1f, edge.offsetX, 0f)
        assertEquals(-1f, edge.offsetY, 0f)
        val reversed = edge.transformed(viewport, viewport, Offset(100f, 100f), Offset(-6f, 6f), 1f)
        assertEquals(0.94f, reversed.offsetX, 0.0001f)
        assertEquals(-0.94f, reversed.offsetY, 0.0001f)
    }

    @Test
    fun zoomingOutToCoverRecentersAxesAndNeverLeavesGaps() {
        val transform = BackgroundImageTransform(2f, 0.7f, -0.5f).transformed(viewport, viewport,
            Offset(100f, 100f), Offset.Zero, 0.1f)
        assertEquals(BackgroundImageTransform(), transform)
        assertTrue(transform.offsetX.isFinite() && transform.offsetY.isFinite())
    }

    @Test
    fun emptyLayoutDoesNotModifyTheDraft() {
        val transform = BackgroundImageTransform(2f, 0.7f, -0.5f)
        assertEquals(transform, transform.transformed(IntSize.Zero, viewport,
            Offset.Zero, Offset(5f, 5f), 2f))
    }
}
