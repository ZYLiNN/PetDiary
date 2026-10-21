package com.zylinn.petdiary.ui.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderTypes
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.domain.ReminderSchedule
import com.zylinn.petdiary.ui.records.DateDialog
import com.zylinn.petdiary.ui.records.color
import com.zylinn.petdiary.ui.records.icon
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorScreen(vm: ReminderEditorViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val customTitles by vm.customTitles.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val problem = form.problem(vm.today(), state.isEdit)
    val cancel = { if (state.dirty) confirmDiscard = true else onDone() }
    BackHandler(onBack = cancel)

    // After saving: if notifications are still off, say so once instead of failing silently (P4-07).
    val finishSave = {
        vm.save { if (ReminderTips.hasNotificationPermission(context)) onDone() else permissionDenied = true }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finishSave() }
    val onSave = {
        // The permission dialog appears only now — the first time a reminder is created.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderTips.hasNotificationPermission(context) && !ReminderTips.alreadyAsked(context)
        ) {
            ReminderTips.markAsked(context)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishSave()
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放棄這次的編輯？") },
            text = { Text("剛剛輸入的內容不會儲存。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }, modifier = Modifier.testTag("confirm_discard")) { Text("放棄") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("繼續編輯") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這個提醒？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }, modifier = Modifier.testTag("confirm_delete")) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false; onDone() },
            title = { Text("提醒已儲存，但通知未開啟") },
            text = { Text("時間到了不會跳出通知。你可以到系統設定開啟通知；不開也沒關係，提醒仍會顯示在提醒頁和寵物頁的「今天待辦」。") },
            confirmButton = {
                TextButton(onClick = { ReminderTips.openNotificationSettings(context); permissionDenied = false; onDone() }, modifier = Modifier.testTag("open_notification_settings")) { Text("前往設定") }
            },
            dismissButton = { TextButton(onClick = { permissionDenied = false; onDone() }, modifier = Modifier.testTag("skip_notification_settings")) { Text("知道了") } },
            modifier = Modifier.testTag("permission_denied_dialog"),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = cancel, modifier = Modifier.testTag("cancel_button")) { Icon(Icons.Filled.Close, contentDescription = "取消") }
                },
                title = { Text(if (state.isEdit) "編輯提醒" else "新增提醒", modifier = Modifier.testTag("editor_title")) },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_button")) {
                            Icon(Icons.Outlined.Delete, contentDescription = "刪除提醒")
                        }
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = problem == null && !state.saving && !state.loading,
                        modifier = Modifier.testTag("save_button"),
                    ) { Text("儲存", style = MaterialTheme.typography.titleMedium) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            ) {
                if (state.pets.size > 1) {
                    Text(if (state.isEdit) "寵物 *" else "寵物 *（可多選，每隻各一則提醒）", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.pets.forEach { pet ->
                            FilterChip(
                                selected = pet.id in form.petIds,
                                onClick = { vm.togglePet(pet.id) },
                                label = { Text(pet.name) },
                                modifier = Modifier.testTag("rem_pet_${pet.id}"),
                            )
                        }
                    }
                }
                Text("類型", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderTypes.forEach { type ->
                        FilterChip(
                            selected = form.type == type,
                            onClick = { vm.edit { it.copy(type = type) } },
                            label = { Text(type.label) },
                            leadingIcon = { Icon(type.icon, contentDescription = null, tint = type.color, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("rem_type_${type.name}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> if (v.length <= REMINDER_TITLE_MAX) vm.edit { it.copy(title = v) } },
                    label = { Text(if (form.type == RecordType.CUSTOM) "名稱 *（例如：剪指甲）" else "名稱（選填，例如：點眼藥）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rem_title"),
                )
                if (form.type == RecordType.CUSTOM && customTitles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        customTitles.forEach { name ->
                            AssistChip(onClick = { vm.edit { it.copy(title = name) } }, label = { Text(name) }, modifier = Modifier.testTag("custom_name_$name"))
                        }
                    }
                }
                Text("時間", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).testTag("rem_date")) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(form.date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                    }
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).testTag("rem_time")) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(RecordFormat.time(form.minuteOfDay))
                    }
                }
                Text("重複", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderRepeat.entries.forEach { repeat ->
                        FilterChip(
                            selected = form.repeat == repeat,
                            onClick = { vm.edit { it.copy(repeat = repeat) } },
                            label = { Text(if (repeat == ReminderRepeat.EVERY_N_DAYS) "每 N 天" else ReminderSchedule.repeatLabel(repeat, 1).replace("只提醒一次", "不重複")) },
                            modifier = Modifier.testTag("rem_repeat_${repeat.name}"),
                        )
                    }
                }
                if (form.repeat == ReminderRepeat.EVERY_N_DAYS) {
                    OutlinedTextField(
                        value = form.repeatEvery,
                        onValueChange = { v -> vm.edit { it.copy(repeatEvery = v.filter(Char::isDigit).take(3)) } },
                        label = { Text("每幾天") },
                        suffix = { Text("天") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("rem_every"),
                    )
                }
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("rem_problem"))
                }
            }
        }
    }

    if (pickDate) {
        DateDialog(form.date, minDate = if (state.isEdit) null else vm.today(), onDismiss = { pickDate = false }) { d -> vm.edit { it.copy(date = d) } }
    }
    if (pickTime) {
        val tp = rememberTimePickerState(initialHour = form.minuteOfDay / 60, initialMinute = form.minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { vm.edit { it.copy(minuteOfDay = tp.hour * 60 + tp.minute) }; pickTime = false }, modifier = Modifier.testTag("time_confirm")) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("取消") } },
            text = { TimePicker(state = tp) },
        )
    }
}
