package com.zylinn.petdiary.ui.album

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.ui.common.SettingsAction
import com.zylinn.petdiary.ui.records.AttachmentExport
import com.zylinn.petdiary.ui.records.formatSize
import com.zylinn.petdiary.ui.theme.PetPalette
import java.time.LocalDate

/**
 * Album tab (P13, D80): every photo (grid) and PDF (list) attached to any record, grouped by month,
 * for all pets or one. Tap a photo to see it large with 下載 / 分享 / 看紀錄.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    vm: AlbumViewModel,
    onOpenRecordDay: (petId: String, day: LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val names = ui.pets.associate { it.id to it.name }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("相簿") },
                actions = { SettingsAction(onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val tabs = listOf(AlbumTab.PHOTOS to "照片 ${ui.photoCount}", AlbumTab.FILES to "檔案 ${ui.fileCount}")
                tabs.forEachIndexed { i, (t, text) ->
                    SegmentedButton(
                        selected = ui.tab == t,
                        onClick = { vm.showTab(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                        modifier = Modifier.testTag("album_tab_${t.name}"),
                    ) { Text(text) }
                }
            }
            if (ui.pets.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    item {
                        FilterChip(selected = ui.petId == null, onClick = { vm.showPet(null) }, label = { Text("全部") }, modifier = Modifier.testTag("album_pet_all"))
                    }
                    items(ui.pets, key = { it.id }) { p ->
                        val sw = PetPalette.swatch(p.color, dark)
                        FilterChip(
                            selected = ui.petId == p.id,
                            onClick = { vm.showPet(p.id) },
                            label = { Text(p.name) },
                            leadingIcon = { Box(Modifier.size(12.dp).background(sw.fill, CircleShape).border(1.dp, sw.line, CircleShape)) },
                            modifier = Modifier.testTag("album_pet_${p.id}"),
                        )
                    }
                }
            }
            if (!ui.loading && ui.months.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(
                        if (ui.tab == AlbumTab.PHOTOS) "還沒有照片。\n在任何一筆紀錄裡按「拍照」或「相簿」就會出現在這裡。"
                        else "還沒有檔案。\n在紀錄裡加入 PDF（例如檢驗報告）就會出現在這裡。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp).testTag("album_empty"),
                    )
                }
                return@Column
            }
            val photos = ui.tab == AlbumTab.PHOTOS
            LazyVerticalGrid(
                columns = if (photos) GridCells.Adaptive(104.dp) else GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize().testTag("album_grid"),
            ) {
                ui.months.forEach { (month, list) ->
                    item(key = "m-$month", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${month.year} 年 ${month.monthValue} 月",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.entry.id }) { item ->
                        if (photos) PhotoTile(item, names[item.entry.petId].orEmpty()) { viewing = item.entry.id }
                        else FileRow(item, names[item.entry.petId].orEmpty()) {
                            if (!item.ready) Toast.makeText(context, "檔案還在從雲端下載，請稍後再試", Toast.LENGTH_SHORT).show()
                            else if (!AttachmentExport.open(context, item.file, item.entry.mimeType)) {
                                Toast.makeText(context, "找不到可以開啟 PDF 的 App", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    ui.months.flatMap { it.second }.firstOrNull { it.entry.id == viewing }?.let { item ->
        PhotoViewer(
            item = item,
            petName = names[item.entry.petId].orEmpty(),
            onDismiss = { viewing = null },
            onOpenRecord = { viewing = null; onOpenRecordDay(item.entry.petId, item.day) },
        )
    }
}

@Composable
private fun PhotoTile(item: AlbumItem, petName: String, onClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$petName ${item.entry.type.label} ${item.day.monthValue}月${item.day.dayOfMonth}日的照片" }
            .testTag("album_photo_${item.entry.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (item.ready) {
            AsyncImage(model = item.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // A family member's photo that hasn't finished downloading yet.
            Icon(Icons.Outlined.CloudDownload, contentDescription = "下載中", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(item: AlbumItem, petName: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(10.dp).testTag("album_file_${item.entry.id}"),
    ) {
        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f)) {
            Text(item.entry.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$petName・${item.entry.type.label}・${item.day.monthValue}/${item.day.dayOfMonth}・${formatSize(item.entry.sizeBytes)}" +
                    if (item.ready) "" else "・下載中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoViewer(item: AlbumItem, petName: String, onDismiss: () -> Unit, onOpenRecord: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).testTag("album_viewer")) {
            Box(Modifier.fillMaxWidth().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                if (item.ready) AsyncImage(model = item.file, contentDescription = item.entry.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                else Text("照片還在從雲端下載", color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(48.dp))
            }
            val ink = MaterialTheme.colorScheme.inverseOnSurface
            Text(
                "$petName・${item.entry.type.label}・${item.day.year}/${item.day.monthValue}/${item.day.dayOfMonth} ${RecordFormat.time(item.entry.minuteOfDay)}",
                color = ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenRecord, modifier = Modifier.testTag("viewer_open_record")) { Text("看紀錄", color = ink) }
                if (item.ready && AttachmentExport.canSave) {
                    TextButton(onClick = {
                        val where = AttachmentExport.save(context, item.file, item.entry.mimeType, item.entry.displayName)
                        Toast.makeText(context, where?.let { "已存到$it" } ?: "儲存失敗，請改用「分享」", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.testTag("viewer_save")) { Text("下載", color = ink) }
                }
                if (item.ready) {
                    TextButton(onClick = { AttachmentExport.share(context, item.file, item.entry.mimeType) }, modifier = Modifier.testTag("viewer_share")) { Text("分享", color = ink) }
                }
                TextButton(onClick = onDismiss) { Text("關閉", color = ink) }
            }
        }
    }
}
