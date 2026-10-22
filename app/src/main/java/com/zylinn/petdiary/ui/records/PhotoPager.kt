package com.zylinn.petdiary.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-width photo viewer that swipes left / right through [items] (P14-02), used by the record editor and the album.
 * [onShow] reports the photo now on screen, so callers can keep it across rotation / folding; [actions] act on it.
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
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage }.collect { i -> items.getOrNull(i)?.let(onShow) }
    }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).testTag(tag)) {
            HorizontalPager(
                state = pager,
                key = { key(items[it]) },
                modifier = Modifier.fillMaxWidth().testTag("viewer_pager"),
            ) { i ->
                // A minimum height keeps a photo that is still loading (or failed to) swipeable.
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { page(items[i]) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                actions(current, ink)
                TextButton(onClick = onDismiss) { Text("關閉", color = ink) }
            }
        }
    }
}
