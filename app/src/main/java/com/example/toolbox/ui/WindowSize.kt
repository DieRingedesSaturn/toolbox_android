package com.example.toolbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
fun isExpandedWidth(): Boolean {
    val minimumWidth = with(LocalDensity.current) { 600.dp.roundToPx() }
    return LocalWindowInfo.current.containerSize.width >= minimumWidth
}
