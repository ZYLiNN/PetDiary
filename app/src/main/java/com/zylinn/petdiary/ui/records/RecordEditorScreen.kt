package com.zylinn.petdiary.ui.records

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.record.isMedical
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.domain.RecordFormat.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditorScreen(
    vm: RecordEditorViewModel,
    onDone: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val supplements by vm.supplements.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val cancel = { if (state.dirty) confirmDiscard = true else onDone() }
    BackHandler(onBack = cancel)
    val today = vm.today()
    val form = state.form
    val problem = form.problem(today)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放棄這次的編輯？") },
            text = { Text("剛剛輸入的內容不會儲存。") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDone() }, modifier = Modifier.testTag("confirm_discard")) { Text("放棄") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("繼續編輯") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = cancel, modifier = Modifier.testTag("cancel_button")) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                },
                title = {
                    Column {
                        Text((if (state.isEdit) "編輯" else "新增") + form.type.label, modifier = Modifier.testTag("editor_title"))
                        if (state.petNames.isNotEmpty()) {
                            Text(state.petNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(onDone) },
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
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("record_editor_scroll"),
            ) {
                DateTimeRow(form, today, vm::edit)
                when {
                    form.type == RecordType.FEEDING -> FeedingFields(form, supplements, vm::edit, vm::addSupplement)
                    form.type == RecordType.WEIGHT -> WeightField(form, vm::edit)
                    form.type == RecordType.GROOMING -> GroomingFields(form, vm::edit)
                    form.type == RecordType.ABNORMAL -> TextInput(form.title, "狀況 *（例如：嘔吐、拉肚子）", "field_title") { v -> vm.edit { it.copy(title = v) } }
                    form.type.isMedical -> MedicalFields(form, vm::edit)
                }
                extraContent()
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { v -> vm.edit { it.copy(note = v.take(NOTE_MAX)) } },
                    label = { Text("備註") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("field_note"),
                )
                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("form_problem"))
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(form: RecordForm, today: LocalDate, onEdit: ((RecordForm) -> RecordForm) -> Unit) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).testTag("field_date")) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("${form.date.year}/${form.date.monthValue}/${form.date.dayOfMonth}")
        }
        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).testTag("field_time")) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(RecordFormat.time(form.minuteOfDay))
        }
    }
    if (pickDate) {
        DateDialog(form.date, maxDate = today, onDismiss = { pickDate = false }) { d -> onEdit { it.copy(date = d) } }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = form.minuteOfDay / 60, initialMinute = form.minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onEdit { it.copy(minuteOfDay = state.hour * 60 + state.minute) }
                    pickTime = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("取消") } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Material date picker restricted to [minDate]..[maxDate] (either bound optional). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
    initial: LocalDate?,
    maxDate: LocalDate? = null,
    minDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    fun LocalDate.millis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial?.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                (maxDate == null || utcTimeMillis <= maxDate.millis()) && (minDate == null || utcTimeMillis >= minDate.millis())
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }, modifier = Modifier.testTag("date_confirm")) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedingFields(
    form: RecordForm,
    supplements: List<Supplement>,
    onEdit: ((RecordForm) -> RecordForm) -> Unit,
    onAddSupplement: (String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = form.portions,
        onValueChange = { v -> onEdit { it.copy(portions = v.filter { c -> c.isDigit() || c == '.' }.take(5)) } },
        label = { Text("主食份數") },
        suffix = { Text("份") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("field_portions"),
    )
    CheckRow("有喝水", form.drankWater, "field_water") { c -> onEdit { it.copy(drankWater = c) } }
    Text("保健食品", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        supplements.forEach { s ->
            FilterChip(
                selected = s.id in form.supplementIds,
                onClick = {
                    onEdit { f -> f.copy(supplementIds = if (s.id in f.supplementIds) f.supplementIds - s.id else f.supplementIds + s.id) }
                },
                label = { Text(s.name) },
                modifier = Modifier.testTag("supp_${s.name}"),
            )
        }
        AssistChip(
            onClick = { adding = true },
            label = { Text("新增") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.testTag("supp_add"),
        )
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("新增保健食品") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(TEXT_MAX) },
                    singleLine = true,
                    placeholder = { Text("例如：魚油") },
                    modifier = Modifier.testTag("supp_new_name"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAddSupplement(newName); newName = ""; adding = false },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.testTag("supp_new_confirm"),
                ) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun WeightField(form: RecordForm, onEdit: ((RecordForm) -> RecordForm) -> Unit) {
    OutlinedTextField(
        value = form.weightKg,
        onValueChange = { v -> onEdit { it.copy(weightKg = v.filter { c -> c.isDigit() || c == '.' }.take(6)) } },
        label = { Text("體重 *") },
        suffix = { Text("kg") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("field_weight"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroomingFields(form: RecordForm, onEdit: ((RecordForm) -> RecordForm) -> Unit) {
    Text("項目 *", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GroomingItems.forEach { item ->
            FilterChip(
                selected = item in form.groomingItems,
                onClick = {
                    onEdit { f -> f.copy(groomingItems = if (item in f.groomingItems) f.groomingItems - item else f.groomingItems + item) }
                },
                label = { Text(item) },
                modifier = Modifier.testTag("groom_$item"),
            )
        }
    }
    TextInput(form.hospital, "店家", "field_hospital") { v -> onEdit { it.copy(hospital = v) } }
    CostField(form, onEdit)
}

/** Shared layout for medicine / vaccine / deworming / vet (S11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicalFields(form: RecordForm, onEdit: ((RecordForm) -> RecordForm) -> Unit) {
    val labels = medicalLabels(form.type)
    if (form.type == RecordType.DEWORMING) {
        val kinds = listOf<DewormKind?>(null, DewormKind.INTERNAL, DewormKind.EXTERNAL)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = form.dewormKind == k,
                    onClick = { onEdit { it.copy(dewormKind = k) } },
                    shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    modifier = Modifier.testTag("deworm_$i"),
                ) { Text(k?.let { with(RecordFormat) { it.label } } ?: "未指定") }
            }
        }
    }
    TextInput(form.title, labels.title, "field_title") { v -> onEdit { it.copy(title = v) } }
    labels.detail?.let { TextInput(form.detail, it, "field_detail") { v -> onEdit { f -> f.copy(detail = v) } } }
    TextInput(form.hospital, labels.hospital, "field_hospital") { v -> onEdit { it.copy(hospital = v) } }
    CostField(form, onEdit)

    var pickNext by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { pickNext = true }, modifier = Modifier.fillMaxWidth().testTag("field_next")) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(form.nextDate?.let { "${labels.next}：${it.year}/${it.monthValue}/${it.dayOfMonth}" } ?: "${labels.next}（選填）")
    }
    if (form.nextDate != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckRow("到時提醒我", form.remindNext, "field_remind", Modifier.weight(1f)) { c -> onEdit { it.copy(remindNext = c) } }
            TextButton(onClick = { onEdit { it.copy(nextDate = null, remindNext = false) } }) { Text("清除日期") }
        }
    }
    if (pickNext) {
        DateDialog(form.nextDate, minDate = form.date.plusDays(1), onDismiss = { pickNext = false }) { d ->
            onEdit { it.copy(nextDate = d, remindNext = true) }
        }
    }
}

@Composable
private fun CostField(form: RecordForm, onEdit: ((RecordForm) -> RecordForm) -> Unit) {
    OutlinedTextField(
        value = form.cost,
        onValueChange = { v -> onEdit { it.copy(cost = v.filter { c -> c.isDigit() || c == ',' }.take(12)) } },
        label = { Text("費用") },
        prefix = { Text("NT$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("field_cost"),
    )
}

/** Checkbox whose whole row (including the label) is the touch target. */
@Composable
private fun CheckRow(label: String, checked: Boolean, tag: String, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(end = 12.dp)
            .testTag(tag),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label)
    }
}

@Composable
private fun TextInput(value: String, label: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(TEXT_MAX)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
