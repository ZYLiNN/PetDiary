package com.zylinn.petdiary.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.record.NamedItem
import com.zylinn.petdiary.ui.pets.UNDO_WINDOW_MS
import kotlinx.coroutines.withTimeoutOrNull

/** Wording of one pick-list screen. */
data class NameListTexts(val title: String, val explain: String, val addLabel: String, val empty: String)

val SupplementTexts = NameListTexts(
    title = "保健食品清單",
    explain = "這些會出現在「餵食」紀錄的保健食品選項裡。刪除後，舊紀錄仍然會顯示當時的名稱。",
    addLabel = "新增保健食品",
    empty = "還沒有保健食品。在上面輸入名稱新增，或在餵食紀錄裡直接新增也可以。",
)

val ToyTexts = NameListTexts(
    title = "玩具清單",
    explain = "這些會出現在「玩耍」紀錄的玩具選項裡。刪除後，舊紀錄仍然會顯示當時的名稱。",
    addLabel = "新增玩具",
    empty = "還沒有玩具。在上面輸入名稱新增（例如：逗貓棒），或在玩耍紀錄裡直接新增也可以。",
)

val CategoryTexts = NameListTexts(
    title = "購物分類",
    explain = "飼料、零食、玩具、保健食品、用品、其他是內建分類；這裡可以再加上自己的分類。刪除後，舊紀錄仍然會顯示當時的名稱。",
    addLabel = "新增分類",
    empty = "還沒有自訂分類。在上面輸入名稱新增（例如：貓砂），或在購物紀錄裡直接新增也可以。",
)

/** P6-02 / D77: add, rename and delete the supplements (feeding) or toys (play) offered in the record forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementsScreen(vm: SupplementsViewModel, onBack: () -> Unit, texts: NameListTexts = SupplementTexts) {
    val items by vm.items.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val justDeleted by vm.justDeleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var newName by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<NamedItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)

    LaunchedEffect(justDeleted) {
        val deleted = justDeleted ?: return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbar.showSnackbar("已刪除「${deleted.name}」", actionLabel = "復原", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) vm.undo(deleted) else vm.deleteAcknowledged()
    }

    renaming?.let { item ->
        AlertDialog(
            onDismissRequest = { renaming = null; vm.clearMessage() },
            title = { Text("修改名稱") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { if (it.length <= 50) renameText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("supp_rename_field"),
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("supp_message")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(item.id, renameText) { renaming = null } }, modifier = Modifier.testTag("supp_rename_confirm")) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null; vm.clearMessage() }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                title = { Text(texts.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("snackbar")) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("supp_list")) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            texts.explain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { if (it.length <= 50) { newName = it; vm.clearMessage() } },
                                label = { Text(texts.addLabel) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { vm.add(newName) { newName = "" } }),
                                modifier = Modifier.weight(1f).testTag("supp_add_field"),
                            )
                            Button(onClick = { vm.add(newName) { newName = "" } }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("supp_add_button")) {
                                Icon(Icons.Filled.Add, contentDescription = "新增")
                            }
                        }
                        if (renaming == null) message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("supp_message"))
                        }
                    }
                }
                if (items.isEmpty()) {
                    item {
                        Text(
                            texts.empty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("supp_empty"),
                        )
                    }
                }
                items(items, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = s; renameText = s.name; vm.clearMessage() }, modifier = Modifier.testTag("supp_edit_${s.id}")) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "修改名稱")
                                }
                                IconButton(onClick = { vm.delete(s) }, modifier = Modifier.testTag("supp_delete_${s.id}")) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "刪除")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.testTag("supp_item_${s.id}"),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
