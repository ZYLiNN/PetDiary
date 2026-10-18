package com.zylinn.petdiary.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.ui.pets.PetAvatar
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: TrashViewModel, onBack: () -> Unit) {
    val content by vm.content.collectAsStateWithLifecycle()
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    confirmId?.let { id ->
        val pet = content?.pets?.firstOrNull { it.id == id }
        val record = content?.records?.firstOrNull { it.id == id }
        val title = pet?.let { "永久刪除「${it.name}」？" } ?: "永久刪除這筆「${record?.type?.label.orEmpty()}」紀錄？"
        val body = if (pet != null) "永久刪除後就無法復原，牠的紀錄和照片也會一起刪除。" else "永久刪除後就無法復原，附件也會一起刪除。"
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pet != null) vm.deletePermanently(id) else vm.deleteRecordPermanently(id)
                        confirmId = null
                    },
                    modifier = Modifier.testTag("confirm_permanent_delete"),
                ) { Text("永久刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                title = { Text("垃圾桶") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val data = content ?: return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("trash_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "刪除的項目會保留 7 天，之後自動永久刪除。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (data.isEmpty) {
                    item {
                        Text(
                            text = "垃圾桶是空的",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp).fillMaxWidth().testTag("trash_empty"),
                        )
                    }
                }
                if (data.pets.isNotEmpty()) item { SectionTitle("寵物") }
                items(data.pets, key = { it.id }) { item ->
                    TrashRow(item = item, onRestore = { vm.restore(item.id) }, onDelete = { confirmId = item.id })
                }
                if (data.records.isNotEmpty()) item { SectionTitle("紀錄") }
                items(data.records, key = { it.id }) { item ->
                    TrashRecordRow(item = item, onRestore = { vm.restoreRecord(item) }, onDelete = { confirmId = item.id })
                }
            }
        }
    }
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("trash_item_${item.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            PetAvatar(name = item.name, seed = item.id, photo = item.photo, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "剩餘 ${item.remainingDays} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("remaining_${item.id}"),
                )
            }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_${item.id}")) {
                Text("永久刪除", color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.testTag("restore_${item.id}")) { Text("還原") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TrashRecordRow(item: TrashRecordItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("trash_record_${item.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(item.type.color.copy(alpha = 0.14f)),
            ) { Icon(item.type.icon, contentDescription = null, tint = item.type.color) }
            Column(Modifier.weight(1f)) {
                Text(item.type.label, style = MaterialTheme.typography.titleMedium)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (item.petAlsoTrashed) "剩餘 ${item.remainingDays} 天・寵物也在垃圾桶" else "剩餘 ${item.remainingDays} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("remaining_${item.id}"),
                )
            }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_${item.id}")) {
                Text("永久刪除", color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.testTag("restore_${item.id}")) {
                Text(if (item.petAlsoTrashed) "連同寵物還原" else "還原")
            }
        }
    }
}

