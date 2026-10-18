package com.zylinn.petdiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zylinn.petdiary.R
import com.zylinn.petdiary.ui.navigation.TopLevelDestination

/** Temporary content per tab until each phase implements the real screen. */
private val roadmap: Map<TopLevelDestination, Pair<String, List<String>>> = mapOf(
    TopLevelDestination.Pets to ("P1–P3" to listOf(
        "寵物方格主頁", "新增寵物（必填 + 更多資料）", "寵物剪影圖示", "垃圾桶（保留 7 天）",
        "寵物詳細頁與一週月曆", "上次紀錄摘要列", "新增紀錄面板", "一鍵記錄・同上次",
        "多隻寵物一起記錄", "附件（照片 / PDF）", "搜尋",
    )),
    TopLevelDestination.Reminders to ("P4" to listOf(
        "提醒列表", "重複方式設定", "本機通知", "通知上的「完成」按鈕",
        "今天待辦（主頁）", "從紀錄的下次日期建立提醒", "通知權限引導", "電池最佳化白名單引導",
        "重新開機後恢復提醒", "過期提醒標示",
    )),
    TopLevelDestination.Stats to ("P5" to listOf(
        "寵物複選", "時間區間：週 / 月 / 年 / 自訂", "餵食折線圖", "體重折線圖",
        "費用堆疊長條圖", "醫療時間軸", "美容次數", "異常狀況次數", "空資料提示", "每隻寵物一種顏色",
    )),
    TopLevelDestination.Settings to ("P6–P9" to listOf(
        "外觀：淺色 / 深色 / 跟隨系統", "保健食品清單", "垃圾桶", "匯出 CSV",
        "帳號與家人", "家庭群組 QR Code", "綁定 Google 帳號", "雲端同步", "刪除帳號", "關於",
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(destination: TopLevelDestination) {
    val (phase, items) = roadmap.getValue(destination)
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(destination.title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("list_${destination.name}"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = stringResource(R.string.placeholder_coming, phase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
            itemsIndexed(items) { index, label ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .testTag("item_${destination.name}_$index"),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp),
                    )
                }
            }
        }
    }
}
