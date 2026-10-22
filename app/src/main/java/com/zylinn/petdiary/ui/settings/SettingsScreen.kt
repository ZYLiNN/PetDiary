package com.zylinn.petdiary.ui.settings

import androidx.compose.material.icons.outlined.ShoppingCart
import android.content.Intent
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.settings.ThemeMode
import com.zylinn.petdiary.sync.AccountState
import com.zylinn.petdiary.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (enabled) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).testTag(tag),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun CloudSyncRow(status: SyncStatus, onToggle: (Boolean) -> Unit) {
    val on = status != SyncStatus.Off && status != SyncStatus.Unavailable
    val subtitle = when (status) {
        SyncStatus.Off -> "未開啟：資料只存在這支手機"
        SyncStatus.Unavailable -> "這個版本尚未設定雲端"
        SyncStatus.Connecting -> "連線中…"
        is SyncStatus.Active -> status.lastSyncedAt?.let { "已開啟・上次同步 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "已開啟・同步中"
        is SyncStatus.Error -> "同步遇到問題，會自動重試（${status.message}）"
    }
    ListItem(
        headlineContent = { Text("雲端備份與同步") },
        supportingContent = { Text(subtitle, modifier = Modifier.testTag("cloud_status")) },
        leadingContent = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
        trailingContent = {
            Switch(checked = on, onCheckedChange = onToggle, enabled = status != SyncStatus.Unavailable, modifier = Modifier.testTag("cloud_switch"))
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.testTag("settings_cloud"),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun GoogleAccountRow(cloud: SyncStatus, account: AccountState?, busy: Boolean, onLink: () -> Unit) {
    val ready = cloud is SyncStatus.Active
    val linked = account?.isGoogle == true
    ListItem(
        headlineContent = { Text("Google 帳號") },
        supportingContent = {
            Text(
                when {
                    linked -> "已綁定：${account?.googleEmail}"
                    !ready -> "先開啟雲端備份與同步，才能綁定"
                    else -> "綁定後，換手機或重新安裝也能找回雲端資料"
                },
                modifier = Modifier.testTag("google_status"),
            )
        },
        leadingContent = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
        trailingContent = if (ready && !linked) {
            { TextButton(onClick = onLink, enabled = !busy, modifier = Modifier.testTag("link_google")) { Text("綁定") } }
        } else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.testTag("settings_google"),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** Settings (P6): appearance, custom items, data export, trash, about; cloud account is a "coming soon" block (S9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    versionName: String,
    onOpenTrash: () -> Unit,
    onOpenSupplements: () -> Unit,
    onOpenToys: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onOpenFamily: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val cloud by vm.cloudStatus.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()
    val accountMessage by vm.accountMessage.collectAsStateWithLifecycle()
    val switchOffer by vm.switchOffer.collectAsStateWithLifecycle()
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val host = androidx.activity.compose.LocalActivity.current ?: LocalContext.current
    var askConsent by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(accountMessage) {
        accountMessage?.let { snackbar.showSnackbar(it); vm.accountMessageShown() }
    }

    switchOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = vm::dismissSwitchOffer,
            title = { Text("這個 Google 帳號已經在使用中") },
            text = {
                Text(
                    (offer.email?.let { "$it 已經綁定過另一個雲端身分（例如你的舊手機）。\n\n" } ?: "") +
                        "要改用它登入嗎？這支手機現有的資料會和該帳號的雲端資料合併，不會被刪除。",
                )
            },
            confirmButton = { TextButton(onClick = { vm.switchToGoogle(offer.idToken) }, modifier = Modifier.testTag("switch_ok")) { Text("改用這個帳號") } },
            dismissButton = { TextButton(onClick = vm::dismissSwitchOffer, modifier = Modifier.testTag("switch_cancel")) { Text("取消") } },
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("刪除雲端資料與帳號？") },
            text = {
                Text(
                    "會刪掉雲端上屬於你的備份：寵物、紀錄、提醒、照片與附件，以及你的雲端帳號。\n\n" +
                        "• 你手機上的資料不會被刪除，但會停止同步\n" +
                        "• 如果你是家庭群組的擁有者，其他成員會失去群組的雲端資料（他們手機上的資料仍保留）\n" +
                        "• 這個動作無法復原",
                )
            },
            confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteCloudAccount(host) }, modifier = Modifier.testTag("delete_cloud_ok")) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { askDelete = false }, modifier = Modifier.testTag("delete_cloud_cancel")) { Text("取消") } },
        )
    }

    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text("開啟雲端備份與同步？") },
            text = {
                Text(
                    "開啟後，寵物、紀錄、提醒，以及照片和 PDF 附件會上傳到 Google Firebase，讓你在其他裝置也能看到同樣的資料。\n\n" +
                        "• 不需要登入、不需要 Email，系統會建立一個匿名身分\n" +
                        "• 目前解除安裝 App 或換手機後，找不回這個匿名身分的雲端資料（綁定 Google 帳號功能之後推出）\n" +
                        "• 只有你邀請加入「家庭群組」的人看得到這些資料\n" +
                        "• 隨時可以關閉；關閉只會停止同步，不會刪除資料",
                )
            },
            confirmButton = {
                TextButton(onClick = { askConsent = false; vm.setCloudSync(true) }, modifier = Modifier.testTag("cloud_consent_ok")) { Text("同意並開啟") }
            },
            dismissButton = { TextButton(onClick = { askConsent = false }, modifier = Modifier.testTag("cloud_consent_cancel")) { Text("先不要") } },
        )
    }

    LaunchedEffect(export) {
        when (val e = export) {
            is ExportState.Ready -> {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", e.file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "PetDiary 紀錄匯出")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(send, "匯出 ${e.count} 筆紀錄").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                vm.exportHandled()
            }
            ExportState.Empty -> {
                snackbar.showSnackbar("還沒有任何紀錄可以匯出")
                vm.exportHandled()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                title = { Text("設定") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("snackbar")) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("list_Settings")) {
                // S9: one block for everything cloud-related
                item {
                    SectionHeader("帳號與家人")
                    CloudSyncRow(cloud, onToggle = { on -> if (on) askConsent = true else vm.setCloudSync(false) })
                    GoogleAccountRow(cloud, account, busy = accountBusy, onLink = { vm.linkGoogle(host) })
                    SettingRow("家庭群組", "用 QR Code 邀請家人一起記錄、查看成員", Icons.Outlined.Groups, "settings_account", onClick = onOpenFamily)
                    if (cloud is SyncStatus.Active) {
                        SettingRow("刪除雲端資料與帳號", "刪掉雲端上的備份與檔案（手機上的資料不受影響）", Icons.Outlined.DeleteForever, "settings_delete_cloud", onClick = { askDelete = true })
                    }
                }
                item {
                    SectionHeader("外觀")
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = theme == mode,
                                    onClick = { vm.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                    modifier = Modifier.testTag("theme_${mode.name}"),
                                ) { Text(mode.label) }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                item {
                    SectionHeader("自訂項目")
                    SettingRow("保健食品清單", "新增、修改、刪除餵食紀錄裡的保健食品選項", Icons.Outlined.Medication, "settings_supplements", onClick = onOpenSupplements)
                    SettingRow("玩具清單", "新增、修改、刪除玩耍紀錄裡的玩具選項", Icons.Outlined.Toys, "settings_toys", onClick = onOpenToys)
                    SettingRow("購物分類", "在內建分類之外，新增自己的購物分類", Icons.Outlined.ShoppingCart, "settings_categories", onClick = onOpenCategories)
                }
                item {
                    SectionHeader("資料")
                    SettingRow(
                        "匯出紀錄（CSV）",
                        if (export == ExportState.Working) "整理中…" else "全部寵物的紀錄，可用 Excel 或 Google 試算表開啟",
                        Icons.Outlined.FileDownload, "settings_export", onClick = vm::exportCsv,
                    )
                    SettingRow("垃圾桶", "刪除的寵物和紀錄保留 7 天，可以還原", Icons.Outlined.DeleteOutline, "settings_trash", onClick = onOpenTrash)
                }
                item {
                    SectionHeader("關於")
                    SettingRow("關於 PetDiary", "版本 $versionName・隱私・授權", Icons.Outlined.Info, "settings_about", onClick = onOpenAbout)
                }
            }
        }
    }
}
