package com.zylinn.petdiary.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val licenses = listOf(
    "Lucide 圖示（寵物剪影）" to "ISC 授權",
    "Nunito 字型" to "SIL Open Font License 1.1",
    "Jetpack Compose、Material 3、Navigation 3、Room、WorkManager、Lifecycle" to "Apache License 2.0（Google）",
    "Coil（圖片載入）" to "Apache License 2.0",
    "Kotlin、kotlinx.coroutines、kotlinx.serialization" to "Apache License 2.0（JetBrains）",
)

/** P6-04: version, what data the app keeps (privacy) and open-source credits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                title = { Text("關於 PetDiary") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("about_scroll"),
            ) {
                Text("PetDiary 寵物日記", style = MaterialTheme.typography.headlineSmall)
                Text("版本 $versionName", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("about_version"))
                Text("記錄毛孩的餵食、散步、醫療與日常，並在該吃藥、打疫苗、驅蟲時提醒你。", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text("隱私", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• 目前所有資料（寵物、紀錄、照片、PDF、提醒）只存在這支手機裡，沒有上傳到任何伺服器。\n" +
                        "• App 不要求你登入、不收集 Email 或位置，也沒有廣告和追蹤程式。\n" +
                        "• 需要的權限：通知（提醒）、相機（拍照，選用）。相簿和檔案由你自己選取，App 不會自行讀取。\n" +
                        "• 「匯出紀錄」會產生一個檔案交給你選擇的 App（例如 Email、雲端硬碟），由你決定要不要分享。\n" +
                        "• 刪除的寵物和紀錄會在垃圾桶保留 7 天，之後永久刪除，無法復原。\n" +
                        "• 之後若加入雲端同步，會先徵求你的同意，並在這裡更新說明。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("about_privacy"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text("開放原始碼與授權", style = MaterialTheme.typography.titleMedium)
                licenses.forEach { (name, license) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
