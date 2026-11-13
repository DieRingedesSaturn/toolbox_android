package com.example.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridLayoutTest {

    private val order = HomeModuleOrder.DEFAULT

    @Test
    fun defaultLayoutPlacesToolsRowMajor() {
        val layout = HomeGridLayout.default(order.take(5), columns = 2)
        assertEquals(2, layout.columns)
        assertEquals(3, layout.rows)
        assertEquals(HomeGridCell(order[0], 0, 0), layout.cells[0])
        assertEquals(HomeGridCell(order[1], 0, 1), layout.cells[1])
        assertEquals(HomeGridCell(order[2], 1, 0), layout.cells[2])
    }

    @Test
    fun moveToEmptyCellLeavesTheOldCellBlank() {
        val layout = HomeGridLayout.default(order.take(3), columns = 2)
        val moved = layout.moveTo(order[0], 1, 1)

        assertEquals(HomeGridCell(order[0], 1, 1), moved.cellOf(order[0]))
        assertNull(moved.cellAt(0, 0))
    }

    @Test
    fun moveOntoOccupiedCellSwapsBothCards() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2)
        val moved = layout.moveTo(order[0], 1, 1)

        assertEquals(HomeGridCell(order[0], 1, 1), moved.cellOf(order[0]))
        assertEquals(HomeGridCell(order[3], 0, 0), moved.cellOf(order[3]))
    }

    @Test
    fun moveBelowTheGridCreatesANewRow() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2)
        val moved = layout.moveTo(order[0], 3, 1)

        assertEquals(4, moved.rows)
        assertEquals(HomeGridCell(order[0], 3, 1), moved.cellOf(order[0]))
    }

    @Test
    fun reflowIntoFewerColumnsResolvesCollisionsWithoutOverlap() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2)
        val reflowed = layout.reflow(1)

        assertEquals(1, reflowed.columns)
        assertEquals(listOf(0, 1, 2, 3), reflowed.cells.map { it.row })
        assertEquals(reflowed.cells.size, reflowed.cells.map { it.row to it.col }.distinct().size)
    }

    @Test
    fun reflowKeepsGapsWhenTheColumnCountDoesNotChange() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2)
            .moveTo(order[0], 3, 0)

        assertEquals(layout, layout.reflow(2))
    }

    @Test
    fun topBlankRowsShiftCardsAndKeepBottomSpace() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2)
        val shifted = layout.withTopBlankRows(2).withBottomBlankRows(1)

        assertEquals(2, shifted.topBlankRows)
        assertEquals(1, shifted.bottomBlankRows)
        assertEquals(HomeGridCell(order[0], 2, 0), shifted.cellOf(order[0]))
        assertEquals(5, shifted.rows)
    }

    @Test
    fun formatAndParseKeepCoordinatesAndBlankRows() {
        val original = HomeGridLayout.default(order.take(4), columns = 2)
            .withTopBlankRows(1)
            .withBottomBlankRows(2)
        val restored = HomeGridLayout.parse(original.format(), order.take(4), columns = 2)

        assertEquals(original, restored)
        assertTrue(restored.format().startsWith("v1|cols=2|rows="))
    }

    @Test
    fun missingToolsAreAppendedBelowExistingContent() {
        val raw = "v1|cols=2|rows=3|${order[0].name}@2,0"
        val layout = HomeGridLayout.parse(raw, order, columns = 2)

        assertTrue(layout.cells.all { it.row >= 3 || it.module == order[0] })
        assertEquals(HomeGridCell(order[0], 2, 0), layout.cellOf(order[0]))
        // Every known tool is present exactly once.
        assertEquals(order.toSet(), layout.cells.map { it.module }.toSet())
        assertEquals(layout.cells.size, layout.cells.map { it.module }.distinct().size)
    }

    @Test
    fun duplicateCellsArePushedToTheNextFreeCell() {
        val raw = "v1|cols=2|rows=1|${order[0].name}@0,0|${order[1].name}@0,0"
        val layout = HomeGridLayout.parse(raw, order, columns = 2)

        assertEquals(HomeGridCell(order[0], 0, 0), layout.cellOf(order[0]))
        assertNotEquals(HomeGridCell(order[0], 0, 0), layout.cellOf(order[1]))
    }

    @Test
    fun malformedInputFallsBackToTheDefaultArrangement() {
        val layout = HomeGridLayout.parse("not-a-layout", order, columns = 3)
        val expected = HomeGridLayout.default(order, columns = 3)

        assertEquals(expected.cells, layout.cells)
        assertEquals(3, layout.columns)
    }

    @Test
    fun summaryRowPushesToolsDownAndParsesBack() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2, summaryExpected = true)

        assertEquals(0, layout.summaryRow)
        assertEquals(1, layout.cellOf(order[0])?.row)
        assertEquals(3, layout.rows)
        val restored = HomeGridLayout.parse(
            layout.format(),
            order.take(4),
            columns = 2,
            summaryExpected = true,
        )
        assertEquals(layout, restored)
    }

    @Test
    fun movingSummaryDownKeepsToolCellsOffItsRow() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2, summaryExpected = true)
        val moved = layout.withSummaryRow(2)

        assertEquals(2, moved.summaryRow)
        assertTrue(moved.cells.none { it.row == 2 })
        assertEquals(moved.cells.size, moved.cells.map { it.row to it.col }.distinct().size)
    }

    @Test
    fun disablingSummaryPullsRowsUpAndEnablingInsertsThemAgain() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2, summaryExpected = true)
        val without = layout.withoutSummary()

        assertNull(without.summaryRow)
        assertEquals(0, without.cellOf(order[0])?.row)
        assertEquals(layout, without.withSummaryRow(0))
    }

    @Test
    fun toolCannotBePlacedOnTheSummaryRow() {
        val layout = HomeGridLayout.default(order.take(4), columns = 2, summaryExpected = true)
        val moved = layout.moveTo(order[0], 0, 0)

        assertEquals(layout, moved)
    }

    @Test
    fun parsingWithoutSummaryCompactsTheStoredSummaryRow() {
        val withSummary = HomeGridLayout.default(order.take(4), 2, summaryExpected = true)
        val disabled = HomeGridLayout.parse(
            withSummary.format(),
            order.take(4),
            columns = 2,
            summaryExpected = false,
        )

        assertNull(disabled.summaryRow)
        assertEquals(0, disabled.cellOf(order[0])?.row)
        assertEquals(2, disabled.rows)
    }

    @Test
    fun parsingWithSummaryAddsTheRowWhenTheFileHasNone() {
        val without = HomeGridLayout.default(order.take(4), 2, summaryExpected = false)
        val enabled = HomeGridLayout.parse(
            without.format(),
            order.take(4),
            columns = 2,
            summaryExpected = true,
        )

        assertEquals(0, enabled.summaryRow)
        assertEquals(1, enabled.cellOf(order[0])?.row)
    }
}
