package com.zylinn.petdiary.ui.reminders

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.ui.common.SettingsAction
import com.zylinn.petdiary.ui.pets.UNDO_WINDOW_MS
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    vm: RemindersViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    /** Set when opened as a page from the calendar (P13): shows ← instead of the gear. */
    onBack: (() -> Unit)? = null,
) {
    onBack?.let { androidx.activity.compose.BackHandler(onBack = it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val justCompleted by vm.justCompleted.collectAsStateWithLifecycle()
    val askLate by vm.askLate.collectAsStateWithLifecycle()
    askLate?.let { ask -> LateCompleteDialog(ask, onChoose = { vm.completeLate(ask.id, it) }, onDismiss = vm::dismissLate) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var notificationsAllowed by remember { mutableStateOf(true) }
    var batteryTip by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        batteryTip = ReminderTips.needsBatteryTip(context)
    }

    LaunchedEffect(justCompleted) {
        val c = justCompleted ?: return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbar.showSnackbar("已完成並記錄", actionLabel = "復原", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) vm.undo(c) else vm.completionAcknowledged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onBack != null) "提醒清單" else "提醒") },
                navigationIcon = {
                    onBack?.let { back ->
                        IconButton(onClick = back, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = { if (onBack == null) SettingsAction(onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("fab_add_reminder"),
            ) { Icon(Icons.Filled.Add, contentDescription = "新增提醒") }
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("snackbar")) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("reminder_list"),
            ) {
                if (!notificationsAllowed && !state.isEmpty) {
                    item(key = "tip_permission") {
                        TipCard(
                            title = "尚未允許通知",
                            body = "提醒時間到了不會跳出通知，但還是會顯示在這裡和寵物頁面上方的「今天待辦」。",
                            action = "開啟通知",
                            onAction = { ReminderTips.openNotificationSettings(context) },
                            tag = "tip_permission",
                        )
                    }
                }
                if (batteryTip) {
                    item(key = "tip_battery") {
                        val brand = ReminderTips.brand()
                        TipCard(
                            title = "讓提醒更準時",
                            body = "這支手機的省電機制可能會關掉背景程式，讓提醒晚到或不跳出。建議把 PetDiary 加入白名單：\n${brand.steps}",
                            action = "開啟電池設定",
                            onAction = { ReminderTips.openBatterySettings(context) },
                            secondary = "已設定，不再顯示",
                            onSecondary = { ReminderTips.dismissBatteryTip(context); batteryTip = false },
                            tag = "tip_battery",
                        )
                    }
                }
                if (state.loaded && state.isEmpty) {
                    item(key = "empty") {
                        Text(
                            "還沒有提醒。點右下角「＋」新增，或在記錄藥物、疫苗、驅蟲時勾選「到時提醒我」。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp).testTag("reminders_empty"),
                        )
                    }
                }
                section("已過期", state.overdue, true, state, vm, onEdit, "section_overdue")
                section("今天", state.todayRows, false, state, vm, onEdit, "section_today")
                section("之後", state.later, false, state, vm, onEdit, "section_later")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<com.zylinn.petdiary.data.reminder.ReminderRow>,
    overdue: Boolean,
    state: RemindersState,
    vm: RemindersViewModel,
    onEdit: (String) -> Unit,
    tag: String,
) {
    if (rows.isEmpty()) return
    item(key = tag) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp).testTag(tag),
        )
    }
    items(rows, key = { it.reminder.id }) { row ->
        ReminderItem(
            row = row,
            today = state.today,
            overdue = overdue,
            onToggle = { vm.complete(row.reminder.id) },
            onClick = { onEdit(row.reminder.id) },
        )
    }
}

@Composable
private fun TipCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    tag: String,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAction, modifier = Modifier.testTag("${tag}_action")) { Text(action) }
                if (secondary != null) TextButton(onClick = onSecondary, modifier = Modifier.testTag("${tag}_dismiss")) { Text(secondary) }
            }
        }
    }
}
