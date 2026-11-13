package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridGeometryTest {
    private fun fit(heights: List<Int>, blanks: Set<Int>, available: Int) = HomeGridGeometry.fit(
        naturalHeights = heights,
        blankRows = blanks,
        availableHeight = available,
        preferredSpacing = 10,
        minimumBlankHeight = 32,
        minimumSpacing = 4,
    )

    @Test
    fun roomyScreenPreservesBlankRowsAndCardSpacing() {
        val natural = List(8) { 64 }
        val result = fit(natural, setOf(0, 1, 2), 700)
        assertEquals(natural, result.heights)
        assertEquals(10, result.spacing)
        assertEquals(582, result.height)
    }

    @Test
    fun threeTopBlankRowsAbsorbSmallOverflowWithoutResizingCards() {
        val result = fit(List(8) { 64 }, setOf(0, 1, 2), 580)
        assertEquals(580, result.height)
        assertEquals(List(5) { 64 }, result.heights.drop(3))
        assertEquals(10, result.spacing)
        assertTrue(result.heights.take(3).all { it in 32..64 })
        assertEquals(8, result.heights.size)
    }

    @Test
    fun blankRowsShareLargerReductionInsteadOfDisappearing() {
        val result = fit(List(8) { 64 }, setOf(0, 1, 2), 500)
        assertEquals(500, result.height)
        assertTrue(result.heights.take(3).all { it in 32..64 })
        assertTrue(result.heights.take(3).max() - result.heights.take(3).min() <= 1)
    }

    @Test
    fun gapAdjustmentPreventsOnePixelScrollEvenWithoutBlankRows() {
        val result = fit(List(5) { 64 }, emptySet(), 359)
        assertTrue(result.height <= 359)
        assertEquals(List(5) { 64 }, result.heights)
        assertTrue(result.spacing in 4..10)
    }

    @Test
    fun genuinelyLongContentRemainsScrollable() {
        val result = fit(List(8) { 64 }, setOf(0, 1, 2), 400)
        assertEquals(List(3) { 32 }, result.heights.take(3))
        assertEquals(List(5) { 64 }, result.heights.drop(3))
        assertEquals(4, result.spacing)
        assertTrue(result.height > 400)
    }

    @Test
    fun measuredSummaryAndLargeFontCardHeightsAreNeverClipped() {
        val natural = listOf(64, 64, 64, 270, 100, 100, 100)
        val result = fit(natural, setOf(0, 1, 2), 500)
        assertEquals(natural.drop(3), result.heights.drop(3))
        assertTrue(result.height > 500)
    }

    @Test
    fun blankRowsBetweenOrBelowCardsCanAbsorbOverflowToo() {
        val result = fit(List(6) { 64 }, setOf(1, 5), 420)
        assertTrue(result.height <= 420)
        assertEquals(64, result.heights[0])
        assertEquals(64, result.heights[2])
        assertEquals(6, result.heights.size)
    }

    @Test
    fun independentlyRoundedContentInsetsLeaveNoFractionalDensityOverflow() {
        val viewport = 1028
        val padding = 29 // 16dp rounded independently at a fractional density.
        val result = HomeGridGeometry.fit(
            naturalHeights = List(8) { 114 },
            blankRows = setOf(0, 1, 2),
            availableHeight = viewport - 2 * padding,
            preferredSpacing = 18,
            minimumBlankHeight = 57,
            minimumSpacing = 7,
        )
        assertTrue(result.height + 2 * padding <= viewport)
        assertEquals(List(5) { 114 }, result.heights.drop(3))
    }

    @Test
    fun dragTargetsFollowMeasuredRowCentersInsteadOfAssumingEqualHeight() {
        val geometry = HomeGridGeometry(listOf(32, 32, 200, 64, 64), spacing = 10)
        assertEquals(listOf(0, 42, 84, 294, 368), geometry.tops)
        assertEquals(3, geometry.targetRow(3, 0f))
        assertEquals(2, geometry.targetRow(3, -135f))
        assertEquals(1, geometry.targetRow(3, -265f))
        assertEquals(0, geometry.targetRow(3, -1000f))
        assertEquals(5, geometry.targetRow(4, 74f))
    }

    @Test
    fun emptyAndSingleRowLayoutsHaveNoPhantomSpacing() {
        assertEquals(0, fit(emptyList(), emptySet(), 100).height)
        assertEquals(64, fit(listOf(64), emptySet(), 100).height)
        assertEquals(0, HomeGridGeometry(emptyList(), 10).targetRow(0, 20f))
    }
}
