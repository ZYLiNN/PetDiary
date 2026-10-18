package com.zylinn.petdiary.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.ui.pets.PetAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: TrashViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    confirmId?.let { id ->
        val name = items?.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("永久刪除「$name」？") },
            text = { Text("永久刪除後就無法復原，牠的照片也會一起刪除。") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deletePermanently(id); confirmId = null },
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
        val list = items ?: return@Scaffold
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
                if (list.isEmpty()) {
                    item {
                        Text(
                            text = "垃圾桶是空的",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp).fillMaxWidth().testTag("trash_empty"),
                        )
                    }
                }
                items(list, key = { it.id }) { item ->
                    TrashRow(item = item, onRestore = { vm.restore(item.id) }, onDelete = { confirmId = item.id })
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
