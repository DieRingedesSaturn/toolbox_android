package com.example.toolbox.ui

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pixel geometry shared by row measurement and drag hit-testing. */
internal data class HomeGridGeometry(val heights: List<Int>, val spacing: Int) {
    val tops: List<Int> = heights.runningFold(0) { top, height -> top + height + spacing }.dropLast(1)
    val height: Int = heights.sum() + spacing * (heights.size - 1).coerceAtLeast(0)

    fun targetRow(from: Int, offsetY: Float): Int {
        if (heights.isEmpty()) return 0
        val source = from.coerceIn(heights.indices)
        val y = tops[source] + heights[source] / 2f + offsetY
        val last = heights.lastIndex
        val lastCenter = tops[last] + heights[last] / 2f
        if (y > lastCenter) {
            return last + ((y - lastCenter) / (heights.last() + spacing).coerceAtLeast(1)).roundToInt()
        }
        return heights.indices.minBy { kotlin.math.abs(tops[it] + heights[it] / 2f - y) }
    }

    companion object {
        /** Compress gaps and blank rows only; real cards retain their measured height. */
        fun fit(
            naturalHeights: List<Int>,
            blankRows: Set<Int>,
            availableHeight: Int,
            preferredSpacing: Int,
            minimumBlankHeight: Int,
            minimumSpacing: Int,
        ): HomeGridGeometry {
            val heights = naturalHeights.toMutableList()
            val gaps = (heights.size - 1).coerceAtLeast(0)
            var deficit = (heights.sum() + preferredSpacing * gaps - availableHeight).coerceAtLeast(0)
            val blanks = heights.indices.filter { it in blankRows }
            // Share the reduction across blank rows rather than erasing the first gap.
            for ((index, row) in blanks.withIndex()) {
                val share = ceil(deficit.toDouble() / (blanks.size - index)).toInt()
                val reduction = share.coerceAtMost((heights[row] - minimumBlankHeight).coerceAtLeast(0))
                heights[row] -= reduction
                deficit -= reduction
            }
            val spacing = if (gaps > 0) {
                (preferredSpacing - ceil(deficit.toDouble() / gaps).toInt())
                    .coerceIn(minimumSpacing.coerceAtMost(preferredSpacing), preferredSpacing)
            } else {
                preferredSpacing
            }
            return HomeGridGeometry(heights, spacing)
        }
    }
}

/** Layout-pass output, read only by drag handlers; it never schedules another measure pass. */
internal class HomeGridMeasurement {
    var geometry: HomeGridGeometry? = null
}
