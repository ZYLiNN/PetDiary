package com.zylinn.petdiary.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-width photo viewer that swipes left / right through [items] (P14-02), used by the record editor and the album.
 * [onShow] reports the photo now on screen, so callers can keep it across rotation / folding; [actions] act on it.
 * The photo takes only the height left over by the buttons (unfolded screens), zooms with a pinch or double tap,
 * and closes with 關閉 / back only, not by tapping it (v0.14.1).
 */
@Composable
fun <T> PhotoPager(
    items: List<T>,
    startIndex: Int,
    key: (T) -> Any,
    onDismiss: () -> Unit,
    onShow: (T) -> Unit,
    tag: String,
    page: @Composable (T) -> Unit,
    caption: ((T) -> String)? = null,
    actions: @Composable RowScope.(item: T, ink: Color) -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage }.collect { i -> items.getOrNull(i)?.let(onShow) }
    }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .testTag(tag)) {
            HorizontalPager(
                state = pager,
                key = { key(items[it]) },
                userScrollEnabled = !zoomed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("viewer_pager"),
            ) { i ->
                ZoomableBox(onZoomChange = { zoomed = it }) { page(items[i]) }
            }
            Row(Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    caption?.invoke(current).orEmpty(),
                    color = ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                if (items.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${items.size}",
                        color = ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("viewer_counter"),
                    )
                }
            }
            Row(Modifier
                .fillMaxWidth()
                .padding(8.dp), horizontalArrangement = Arrangement.End) {
                actions(current, ink)
                TextButton(onClick = onDismiss) { Text("關閉", color = ink) }
            }
        }
    }
}

/** Pinch or double tap to zoom, drag while zoomed; at 1x a one-finger swipe is left to the pager. */
@Composable
private fun ZoomableBox(onZoomChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else scale = 2.5f
                    onZoomChange(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // While zoomed, one finger only drags once it really moves: a still tap must reach the double-tap above.
                    var dragging = false
                    var travel = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val twoFingers = event.changes.count { it.pressed } >= 2
                        if (!dragging && scale > 1f) {
                            travel += event.calculatePan()
                            dragging = travel.getDistance() > viewConfiguration.touchSlop
                        }
                        if (twoFingers || dragging) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            onZoomChange(scale > 1f)
                            event.changes.forEach { it.consume() } // 吃掉，pager 就不會同時換頁
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
