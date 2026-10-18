package com.zylinn.petdiary.testutil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Lays out [content] at exactly [width] × [height] dp, shrinking the density when the device
 * is smaller, so e.g. an unfolded-width layout stays fully on screen even on a folded emulator.
 */
@Composable
fun FitToScreen(width: Dp, height: Dp, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val config = LocalConfiguration.current
    val factor = minOf(1f, config.screenWidthDp / width.value, config.screenHeightDp / height.value)
    CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale)) {
        Box(Modifier.requiredSize(width, height)) { content() }
    }
}
