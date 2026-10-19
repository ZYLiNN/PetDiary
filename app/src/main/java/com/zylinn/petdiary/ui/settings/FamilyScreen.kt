package com.zylinn.petdiary.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.zylinn.petdiary.sync.FamilyService
import com.zylinn.petdiary.sync.Member
import com.zylinn.petdiary.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Family group (P8): who is in it, invite someone (QR), join with a scanned / pasted invitation, remove or leave. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(vm: FamilyViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val status by vm.status.collectAsStateWithLifecycle()
    val family by vm.family.collectAsStateWithLifecycle()
    val invite by vm.invite.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val myName by vm.myName.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    var pendingInvite by rememberSaveable { mutableStateOf<String?>(null) } // scanned / pasted text awaiting confirmation
    var nickname by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Member?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let { pendingInvite = it; nickname = myName } }
            .addOnFailureListener { vm.reportScanProblem() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                title = { Text("家庭群組") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("snackbar")) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("family_scroll"),
            ) {
                val fam = family
                if (fam == null || status !is SyncStatus.Active) {
                    Text(
                        if (status == SyncStatus.Off) "家庭群組需要先開啟「雲端備份與同步」（設定 → 帳號與家人）。"
                        else "連線中… 連上雲端後就能邀請家人或加入群組。",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("family_need_cloud"),
                    )
                    return@Column
                }

                Text("一起記錄毛孩的日常：家人加入後，寵物與紀錄會同步給大家，每張紀錄卡片會顯示是誰記的。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // members
                Text("成員（${fam.members.size}）", style = MaterialTheme.typography.titleSmall)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    fam.members.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ListItem(
                            headlineContent = { Text(m.name + if (m.isMe) "（我）" else "") },
                            supportingContent = { Text(if (m.isOwner) "擁有者" else "成員") },
                            trailingContent = if (fam.iAmOwner && !m.isOwner) {
                                { IconButton(onClick = { removing = m }, modifier = Modifier.testTag("remove_${m.uid}")) { Icon(Icons.Outlined.PersonRemove, contentDescription = "移除 ${m.name}") } }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.testTag("member_${m.uid}"),
                        )
                    }
                }
                OutlinedButton(onClick = { editName = myName }, modifier = Modifier.fillMaxWidth().testTag("rename_me")) { Text("修改我的顯示名稱") }

                // invite
                if (fam.iAmOwner) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("邀請家人", style = MaterialTheme.typography.titleSmall)
                    val inv = invite
                    if (inv == null) {
                        Button(onClick = vm::createInvite, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("create_invite")) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  產生邀請 QR Code")
                        }
                        Text("對方用 PetDiary 掃描就能加入。邀請 24 小時內有效，而且只能用一次。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            QrCode(inv.link.asText(), 240.dp, Modifier.testTag("invite_qr"))
                            Text(
                                "有效到 " + SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(inv.expiresAt)) + "・只能使用一次",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp).testTag("invite_expiry"),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "一起用 PetDiary 記錄毛孩！在 App 的「家庭群組」貼上這段連結加入（24 小時內有效，只能用一次）：\n${inv.link.asText()}")
                                    runCatching { context.startActivity(Intent.createChooser(send, "分享邀請").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }, modifier = Modifier.testTag("share_invite")) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  分享連結")
                                }
                                TextButton(onClick = vm::hideInvite, modifier = Modifier.testTag("hide_invite")) { Text("關閉") }
                            }
                        }
                    }
                }

                // join (only while alone in my own group)
                if (!fam.isShared) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("加入別人的群組", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = ::scan, enabled = !busy, modifier = Modifier.weight(1f).testTag("scan_invite")) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  掃描 QR Code")
                        }
                        OutlinedButton(onClick = { pasteOpen = true }, enabled = !busy, modifier = Modifier.weight(1f).testTag("paste_invite")) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  貼上連結")
                        }
                    }
                }

                // leave
                if (fam.isShared && !fam.iAmOwner) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    OutlinedButton(onClick = { leaving = true }, modifier = Modifier.fillMaxWidth().testTag("leave_group")) { Text("離開這個群組") }
                }
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("貼上邀請連結") },
            text = {
                OutlinedTextField(pasted, { pasted = it }, label = { Text("petdiary://join?…") }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("paste_field"))
            },
            confirmButton = {
                TextButton(onClick = { pendingInvite = pasted; nickname = myName; pasteOpen = false; pasted = "" }, enabled = pasted.isNotBlank(), modifier = Modifier.testTag("paste_ok")) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("取消") } },
        )
    }

    pendingInvite?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingInvite = null },
            title = { Text("加入家庭群組？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("你手機上現有的寵物與紀錄會一起加入這個群組，群組裡的人都看得到。之後如果離開，手機裡的資料會保留。")
                    OutlinedTextField(nickname, { nickname = it.take(FamilyService.NAME_MAX) }, label = { Text("家人看到的名字") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("join_name"))
                }
            },
            confirmButton = { TextButton(onClick = { vm.join(text, nickname); pendingInvite = null }, modifier = Modifier.testTag("join_ok")) { Text("加入") } },
            dismissButton = { TextButton(onClick = { pendingInvite = null }) { Text("取消") } },
        )
    }

    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("移除「${m.name}」？") },
            text = { Text("對方之後就看不到群組的資料。他手機裡已同步的資料會留在他的手機上。") },
            confirmButton = { TextButton(onClick = { vm.remove(m.uid); removing = null }, modifier = Modifier.testTag("remove_ok")) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("離開這個群組？") },
            text = { Text("你就看不到群組之後的新資料。你手機裡的所有資料都會保留，並改成只屬於你自己的備份。") },
            confirmButton = { TextButton(onClick = { vm.leave(); leaving = false }, modifier = Modifier.testTag("leave_ok")) { Text("離開") } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("取消") } },
        )
    }

    editName?.let { current ->
        AlertDialog(
            onDismissRequest = { editName = null },
            title = { Text("我的顯示名稱") },
            text = { OutlinedTextField(current, { editName = it.take(FamilyService.NAME_MAX) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("name_field")) },
            confirmButton = { TextButton(onClick = { vm.rename(current); editName = null }, enabled = current.isNotBlank(), modifier = Modifier.testTag("name_ok")) { Text("儲存") } },
            dismissButton = { TextButton(onClick = { editName = null }) { Text("取消") } },
        )
    }
}
