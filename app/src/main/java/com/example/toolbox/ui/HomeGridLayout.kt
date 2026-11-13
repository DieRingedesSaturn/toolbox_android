package com.example.toolbox.ui

/** One tool placed on an explicit home-grid cell. */
internal data class HomeGridCell(
    val module: ToolboxModule,
    val row: Int,
    val col: Int,
)

/**
 * Launcher-like home layout: every tool and the summary card keep an explicit
 * cell/row so moving a card leaves a real gap instead of compacting the
 * remaining cards upwards.
 *
 * Coordinates are absolute grid rows; [minRows] keeps trailing blank rows so
 * users can reserve space below the grid for background alignment.
 */
internal data class HomeGridLayout(
    val columns: Int,
    val minRows: Int,
    val cells: List<HomeGridCell>,
    /** Full-width summary card row, or null when the summary is disabled. */
    val summaryRow: Int? = null,
) {
    val rows: Int
        get() = maxOf(
            minRows,
            (cells.maxOfOrNull { it.row } ?: -1) + 1,
            (summaryRow ?: -1) + 1,
        ).coerceAtLeast(1)

    val topBlankRows: Int
        get() = listOfNotNull(cells.minOfOrNull { it.row }, summaryRow)
            .minOrNull() ?: 0

    private val lastContentRow: Int
        get() = maxOf(cells.maxOfOrNull { it.row } ?: -1, summaryRow ?: -1)

    val bottomBlankRows: Int
        get() = (rows - 1 - lastContentRow).coerceAtLeast(0)

    fun cellAt(row: Int, col: Int): HomeGridCell? =
        cells.firstOrNull { it.row == row && it.col == col }

    fun cellOf(module: ToolboxModule): HomeGridCell? =
        cells.firstOrNull { it.module == module }

    fun readingOrder(): List<ToolboxModule> =
        cells.sortedWith(compareBy({ it.row }, { it.col })).map { it.module }

    /** Places the module at the target cell, swapping with any card already there. */
    fun moveTo(module: ToolboxModule, targetRow: Int, targetCol: Int): HomeGridLayout {
        val source = cellOf(module) ?: return this
        val row = targetRow.coerceAtLeast(0)
        val col = targetCol.coerceIn(0, columns - 1)
        if (row == summaryRow) return this
        if (source.row == row && source.col == col) return this

        val occupant = cellAt(row, col)
        val moved = cells.map { cell ->
            when {
                cell.module == module -> cell.copy(row = row, col = col)
                occupant != null && cell.module == occupant.module ->
                    cell.copy(row = source.row, col = source.col)
                else -> cell
            }
        }
        return copy(cells = moved)
    }

    /** Adds or removes blank rows above the first occupied row. */
    fun withTopBlankRows(count: Int): HomeGridLayout {
        val desired = count.coerceIn(0, MAX_BLANK_ROWS)
        val delta = desired - topBlankRows
        if (delta == 0) return this
        val trailing = bottomBlankRows
        val shifted = cells.map { it.copy(row = (it.row + delta).coerceAtLeast(0)) }
        val shiftedSummary = summaryRow?.let { (it + delta).coerceAtLeast(0) }
        val contentRows = maxOf(
            (shifted.maxOfOrNull { it.row } ?: -1) + 1,
            (shiftedSummary ?: -1) + 1,
        )
        return copy(
            minRows = (contentRows + trailing).coerceAtLeast(1),
            cells = shifted,
            summaryRow = shiftedSummary,
        )
    }

    /** Keeps the current card coordinates and only changes the space below them. */
    fun withBottomBlankRows(count: Int): HomeGridLayout {
        val desired = count.coerceIn(0, MAX_BLANK_ROWS)
        val contentRows = lastContentRow + 1
        return copy(minRows = (contentRows + desired).coerceAtLeast(1))
    }

    /**
     * Moves the full-width summary card to [targetRow], inserting a row there
     * and removing the old one so tool cells never overlap it.
     */
    fun withSummaryRow(targetRow: Int): HomeGridLayout {
        val old = summaryRow
        val target = targetRow.coerceIn(0, rows)
        if (old == target) return this
        if (old == null) {
            val shifted = cells.map {
                if (it.row >= target) it.copy(row = it.row + 1) else it
            }
            return copy(
                minRows = (minRows + 1).coerceAtLeast(1),
                cells = shifted,
                summaryRow = target,
            )
        }
        // Remove the old row first, then insert at the same visual index in the
        // shortened grid; this keeps the summary at the requested row.
        val removed = cells.map {
            if (it.row > old) it.copy(row = it.row - 1) else it
        }
        val adjusted = target.coerceIn(0, (rows - 1).coerceAtLeast(0))
        val inserted = removed.map {
            if (it.row >= adjusted) it.copy(row = it.row + 1) else it
        }
        return copy(
            cells = inserted,
            summaryRow = adjusted,
        )
    }

    /** Removes the summary row and pulls the rows below it upward. */
    fun withoutSummary(): HomeGridLayout {
        val old = summaryRow ?: return this
        val shifted = cells.map {
            if (it.row > old) it.copy(row = it.row - 1) else it
        }
        return copy(
            minRows = (minRows - 1).coerceAtLeast(1),
            cells = shifted,
            summaryRow = null,
        )
    }

    /**
     * Adapts the stored layout to a different column count without losing
     * positions: columns are clamped and collisions move down to the next free
     * cell. Only the displayed layout changes; the stored layout is untouched.
     */
    fun reflow(columns: Int): HomeGridLayout {
        val targetColumns = columns.coerceIn(1, MAX_COLUMNS)
        if (targetColumns == this.columns) return this
        val trailing = bottomBlankRows
        val occupied = HashSet<Pair<Int, Int>>()
        val placed = cells.sortedWith(compareBy({ it.row }, { it.col })).map { cell ->
            var row = cell.row.coerceAtLeast(0)
            var col = cell.col.coerceIn(0, targetColumns - 1)
            while (
                !occupied.add(row to col) ||
                (summaryRow != null && row == summaryRow)
            ) {
                col++
                if (col >= targetColumns) {
                    col = 0
                    row++
                }
            }
            cell.copy(row = row, col = col)
        }
        val contentRows = maxOf(
            (placed.maxOfOrNull { it.row } ?: -1) + 1,
            (summaryRow ?: -1) + 1,
        )
        return HomeGridLayout(
            columns = targetColumns,
            minRows = (contentRows + trailing).coerceAtLeast(1),
            cells = placed,
            summaryRow = summaryRow,
        )
    }

    fun format(): String = buildString {
        append(VERSION)
        append("|cols=").append(columns)
        append("|rows=").append(minRows)
        append("|summary=").append(summaryRow ?: -1)
        cells.sortedWith(compareBy({ it.row }, { it.col })).forEach { cell ->
            append('|')
            append(cell.module.name)
            append('@').append(cell.row).append(',').append(cell.col)
        }
    }

    companion object {
        const val MAX_COLUMNS = 6
        const val MAX_BLANK_ROWS = 8
        private const val VERSION = "v1"

        fun default(
            order: List<ToolboxModule>,
            columns: Int,
            summaryExpected: Boolean = false,
        ): HomeGridLayout {
            val cols = columns.coerceIn(1, MAX_COLUMNS)
            val modules = order
                .ifEmpty { HomeModuleOrder.DEFAULT }
                .filter { it in HomeModuleOrder.DEFAULT }
                .distinct()
            val summaryRow = if (summaryExpected) 0 else null
            val startRow = if (summaryExpected) 1 else 0
            val cells = modules.mapIndexed { index, module ->
                HomeGridCell(module, startRow + index / cols, index % cols)
            }
            return HomeGridLayout(
                columns = cols,
                minRows = (cells.maxOfOrNull { it.row } ?: -1).coerceAtLeast(summaryRow ?: 0) + 1,
                cells = cells,
                summaryRow = summaryRow,
            )
        }

        /**
         * Parses `v1|cols=2|rows=5|summary=0|MONITOR@1,0|...`. Missing tools are
         * appended below the existing content so intentional gaps above or
         * between cards are not filled in unexpectedly.
         */
        fun parse(
            raw: String?,
            order: List<ToolboxModule>,
            columns: Int,
            summaryExpected: Boolean = false,
        ): HomeGridLayout {
            val fallbackColumns = columns.coerceIn(1, MAX_COLUMNS)
            if (raw.isNullOrBlank()) return default(order, fallbackColumns, summaryExpected)
            val parts = raw.split('|')
            if (parts.firstOrNull() != VERSION) return default(order, fallbackColumns, summaryExpected)

            var storedColumns = fallbackColumns
            var storedRows = 1
            var summaryRow: Int? = null
            val parsed = mutableListOf<HomeGridCell>()
            val seen = mutableSetOf<ToolboxModule>()
            for (part in parts.drop(1)) {
                when {
                    part.startsWith("cols=") -> {
                        storedColumns = part.removePrefix("cols=").toIntOrNull()
                            ?.coerceIn(1, MAX_COLUMNS) ?: storedColumns
                    }
                    part.startsWith("rows=") -> {
                        storedRows = part.removePrefix("rows=").toIntOrNull()
                            ?.coerceIn(1, MAX_BLANK_ROWS + 24) ?: storedRows
                    }
                    part.startsWith("summary=") -> {
                        summaryRow = part.removePrefix("summary=").toIntOrNull()
                            ?.takeIf { it >= 0 }
                    }
                    else -> {
                        val at = part.indexOf('@')
                        if (at <= 0) continue
                        val module = ToolboxModule.entries
                            .firstOrNull { it.name == part.substring(0, at) }
                            ?.takeIf { it in HomeModuleOrder.DEFAULT }
                            ?: continue
                        if (!seen.add(module)) continue
                        val coords = part.substring(at + 1).split(',')
                        val row = coords.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: continue
                        val col = coords.getOrNull(1)?.toIntOrNull()
                            ?.coerceIn(0, storedColumns - 1) ?: continue
                        parsed += HomeGridCell(module, row, col)
                    }
                }
            }
            if (parsed.isEmpty()) return default(order, fallbackColumns, summaryExpected)

            val storedSummary = summaryRow
            if (!summaryExpected) {
                summaryRow = null
            } else if (summaryRow == null) {
                summaryRow = 0
            }

            // Removing a stored summary row must pull tools up so disabling the
            // summary does not leave a blank row behind.
            val summaryAdjusted = if (!summaryExpected && storedSummary != null) {
                parsed.map {
                    if (it.row > storedSummary) it.copy(row = it.row - 1) else it
                }
            } else {
                parsed
            }
            val effectiveRows = if (!summaryExpected && storedSummary != null) {
                (storedRows - 1).coerceAtLeast(1)
            } else {
                storedRows
            }

            // Keep tool cells out of the summary row.
            val base = summaryAdjusted.map {
                if (summaryRow != null && it.row == summaryRow) {
                    it.copy(row = it.row + 1)
                } else {
                    it
                }
            }

            // Resolve duplicate cells deterministically, keeping file order.
            val occupied = HashSet<Pair<Int, Int>>()
            val normalized = base.map { cell ->
                var row = cell.row
                var col = cell.col
                while (!occupied.add(row to col) || row == summaryRow) {
                    col++
                    if (col >= storedColumns) {
                        col = 0
                        row++
                    }
                }
                cell.copy(row = row, col = col)
            }.toMutableList()

            val known = normalized.mapTo(HashSet()) { it.module }
            val missing = order
                .ifEmpty { HomeModuleOrder.DEFAULT }
                .filter { it in HomeModuleOrder.DEFAULT && it !in known }
                .distinct()
            var appendRow = (normalized.maxOfOrNull { it.row } ?: -1) + 1
            for (module in missing) {
                val free = firstFreeCell(occupied, storedColumns, appendRow, summaryRow)
                occupied += free
                appendRow = free.first
                normalized += HomeGridCell(module, free.first, free.second)
            }

            val contentRows = maxOf(
                (normalized.maxOfOrNull { it.row } ?: -1) + 1,
                (summaryRow ?: -1) + 1,
            )
            return HomeGridLayout(
                columns = storedColumns,
                minRows = maxOf(effectiveRows, contentRows).coerceAtLeast(1),
                cells = normalized,
                summaryRow = summaryRow,
            )
        }

        private fun firstFreeCell(
            occupied: Set<Pair<Int, Int>>,
            columns: Int,
            startRow: Int,
            summaryRow: Int?,
        ): Pair<Int, Int> {
            var row = startRow.coerceAtLeast(0)
            while (true) {
                if (row != summaryRow) {
                    for (col in 0 until columns) {
                        if ((row to col) !in occupied) return row to col
                    }
                }
                row++
            }
        }
    }
}
