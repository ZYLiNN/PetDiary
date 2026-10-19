package com.zylinn.petdiary.ui.settings

import android.content.Intent
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.settings.ThemeMode

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

/** Settings (P6): appearance, custom items, data export, trash, about; cloud account is a "coming soon" block (S9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    versionName: String,
    onOpenTrash: () -> Unit,
    onOpenSupplements: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

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
                    SettingRow("帳號與家人", "Google 帳號、家庭群組（QR Code）・即將推出", Icons.Outlined.CloudOff, "settings_account", enabled = false)
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
