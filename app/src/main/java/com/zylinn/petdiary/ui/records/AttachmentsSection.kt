package com.zylinn.petdiary.ui.records

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.zylinn.petdiary.data.record.MAX_ATTACHMENTS
import java.io.File

/** Photos (camera / gallery) and PDFs on any record (P2-15 / P2-16; every type since D78), at most [MAX_ATTACHMENTS]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentsSection(
    items: List<AttachmentItem>,
    importing: Boolean,
    error: String?,
    onPicked: (List<Uri>) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) { onPicked(it) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onPicked(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingCamera?.let { if (ok) onPicked(listOf(Uri.parse(it))) }
        pendingCamera = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("attachments_section")) {
        Text("照片 / 附件（${items.size}/$MAX_ATTACHMENTS，每個最多 10MB）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("attach_count"))
        if (items.size < MAX_ATTACHMENTS) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "att-${System.currentTimeMillis()}.jpg"))
                    pendingCamera = uri.toString()
                    takePhoto.launch(uri)
                },
                label = { Text("拍照") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("attach_camera"),
            )
            AssistChip(
                onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("相簿") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("attach_gallery"),
            )
            AssistChip(
                onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                label = { Text("PDF") },
                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("attach_pdf"),
            )
        }
        if (importing) CircularProgressIndicator(Modifier.size(24.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("attach_error")) }
        items.forEach { item ->
            AttachmentRow(
                item = item,
                onOpen = { if (item.isImage) preview = item.key else openWithViewer(context, item) },
                onRemove = { onRemove(item.key) },
                onSave = { saveToPhone(context, item) },
                onShare = { shareItem(context, item) },
            )
        }
    }

    // Photos only: PDFs open in another app, so they never join the swipe (P14-02).
    val photos = items.filter { it.isImage }
    if (preview != null && photos.any { it.key == preview }) {
        PhotoPager(
            items = photos,
            startIndex = photos.indexOfFirst { it.key == preview },
            key = { it.key },
            onDismiss = { preview = null },
            onShow = { preview = it.key },
            tag = "attach_preview",
            page = { item -> AsyncImage(model = item.file, contentDescription = item.displayName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) },
            caption = { it.displayName },
        ) { item, ink ->
            if (AttachmentExport.canSave) {
                TextButton(onClick = { saveToPhone(context, item) }, modifier = Modifier.testTag("preview_save")) { Text("下載", color = ink) }
            }
            TextButton(onClick = { shareItem(context, item) }, modifier = Modifier.testTag("preview_share")) { Text("分享", color = ink) }
        }
    }
}

@Composable
private fun AttachmentRow(item: AttachmentItem, onOpen: () -> Unit, onRemove: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(8.dp)
            .testTag("attachment_${item.displayName}"),
    ) {
        if (item.isImage) {
            AsyncImage(
                model = item.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("more_${item.displayName}")) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多：${item.displayName}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (AttachmentExport.canSave) {
                    DropdownMenuItem(text = { Text("下載到手機") }, onClick = { menu = false; onSave() }, modifier = Modifier.testTag("save_${item.displayName}"))
                }
                DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; onShare() }, modifier = Modifier.testTag("share_${item.displayName}"))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.testTag("remove_${item.displayName}")) {
            Icon(Icons.Filled.Close, contentDescription = "移除 ${item.displayName}")
        }
    }
}

private fun saveToPhone(context: Context, item: AttachmentItem) {
    val where = AttachmentExport.save(context, item.file, item.mimeType, item.displayName)
    Toast.makeText(context, where?.let { "已存到$it" } ?: "儲存失敗，請改用「分享」", Toast.LENGTH_SHORT).show()
}

private fun shareItem(context: Context, item: AttachmentItem) {
    if (!AttachmentExport.share(context, item.file, item.mimeType)) Toast.makeText(context, "找不到可以分享的 App", Toast.LENGTH_SHORT).show()
}

/** PDFs open in whatever viewer the phone has; access is granted read-only for this one file. */
private fun openWithViewer(context: Context, item: AttachmentItem) {
    if (!AttachmentExport.open(context, item.file, item.mimeType)) Toast.makeText(context, "找不到可以開啟 PDF 的 App", Toast.LENGTH_SHORT).show()
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

