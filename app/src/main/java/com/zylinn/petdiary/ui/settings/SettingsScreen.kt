package com.zylinn.petdiary.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Settings items not built yet (P6–P9); listed so the user can see what's coming. */
private val comingSoon = listOf(
    "外觀：淺色 / 深色 / 跟隨系統", "保健食品清單", "匯出 CSV", "帳號與家人",
    "家庭群組 QR Code", "綁定 Google 帳號", "雲端同步", "刪除帳號", "關於",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenTrash: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("list_Settings")) {
                item {
                    ListItem(
                        headlineContent = { Text("垃圾桶") },
                        supportingContent = { Text("刪除的寵物保留 7 天，可以還原") },
                        leadingContent = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable(onClick = onOpenTrash).testTag("settings_trash"),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                itemsIndexed(comingSoon) { i, label ->
                    ListItem(
                        headlineContent = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        supportingContent = { Text("即將推出", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.testTag("item_Settings_$i"),
                    )
                }
            }
        }
    }
}
