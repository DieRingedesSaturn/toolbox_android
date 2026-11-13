package com.example.toolbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Measure occupied rows first so fitting never clips a summary or large-font card. */
@Composable
internal fun HomeGridRows(
    blankRows: Set<Int>,
    settledRowCount: Int,
    availableHeight: Int,
    measurement: HomeGridMeasurement,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val rowConstraints = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.mapIndexed { index, measurable ->
            if (index in blankRows) null else measurable.measure(rowConstraints)
        }.toMutableList()
        val naturalHeights = placeables.map { it?.height ?: 64.dp.roundToPx() }
        // Extra drag targets append below the settled layout, without moving its rows.
        val settled = settledRowCount.coerceAtMost(naturalHeights.size)
        val fitted = HomeGridGeometry.fit(
            naturalHeights = naturalHeights.take(settled),
            blankRows = blankRows,
            availableHeight = availableHeight,
            preferredSpacing = 10.dp.roundToPx(),
            minimumBlankHeight = 32.dp.roundToPx(),
            minimumSpacing = 4.dp.roundToPx(),
        )
        val geometry = HomeGridGeometry(fitted.heights + naturalHeights.drop(settled), fitted.spacing)
        for (index in measurables.indices) {
            if (placeables[index] == null) {
                placeables[index] = measurables[index].measure(
                    rowConstraints.copy(minHeight = geometry.heights[index], maxHeight = geometry.heights[index]),
                )
            }
        }
        measurement.geometry = geometry
        layout(constraints.maxWidth, geometry.height) {
            placeables.forEachIndexed { index, placeable -> placeable?.placeRelative(0, geometry.tops[index]) }
        }
    }
}
