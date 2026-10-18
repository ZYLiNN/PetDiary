package com.zylinn.petdiary.ui.pets.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.ui.pets.PetAvatar
import com.zylinn.petdiary.ui.pets.label
import com.zylinn.petdiary.ui.pets.silhouette
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun PetEditorScreen(vm: PetEditorViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val cancel = {
        if (state.dirty) confirmDiscard = true else { vm.discard(); onDone() }
    }
    BackHandler(onBack = cancel)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放棄這次的編輯？") },
            text = { Text("剛剛輸入的內容不會儲存。") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.discard(); onDone() }, modifier = Modifier.testTag("confirm_discard")) {
                    Text("放棄")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("繼續編輯") } },
        )
    }

    PetEditorContent(
        state = state,
        today = vm.today(),
        photoFile = vm::photoFile,
        onEdit = vm::edit,
        onToggleMore = vm::toggleMore,
        onPickedPhoto = vm::importPhoto,
        onRemovePhoto = vm::removePhoto,
        onCancel = cancel,
        onSave = { vm.save { onDone() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PetEditorContent(
    state: PetEditorState,
    today: LocalDate,
    photoFile: (String) -> File,
    onEdit: ((PetForm) -> PetForm) -> Unit,
    onToggleMore: () -> Unit,
    onPickedPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val form = state.form
    val problems = form.problems(today)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("cancel_button")) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                },
                title = { Text(if (state.isEdit) "編輯寵物" else "新增寵物") },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !problems.any && !state.saving && !state.loading,
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("editor_scroll"),
            ) {
                PhotoSection(form, state, photoFile, onPickedPhoto, onRemovePhoto)

                // 類型
                SectionLabel("類型", required = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Species.entries.forEach { s ->
                        FilterChip(
                            selected = form.species == s,
                            onClick = { onEdit { it.copy(species = s) } },
                            label = { Text(s.label) },
                            leadingIcon = { Icon(painterResource(s.silhouette), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("species_${s.name}"),
                        )
                    }
                }
                Hint(problems.species, "hint_species")
                if (form.species == Species.OTHER) {
                    OutlinedTextField(
                        value = form.customSpecies,
                        onValueChange = { v -> onEdit { it.copy(customSpecies = v.take(20)) } },
                        label = { Text("類型名稱（選填，例如：刺蝟）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("field_custom_species"),
                    )
                }

                // 名字
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> if (v.length <= NAME_MAX) onEdit { it.copy(name = v) } },
                    label = { Text("名字 *") },
                    singleLine = true,
                    supportingText = { Text(problems.name ?: "${form.name.length}/$NAME_MAX") },
                    modifier = Modifier.fillMaxWidth().testTag("field_name"),
                )

                // 生日
                SectionLabel("生日", required = true)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BirthMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = form.birthMode == mode,
                            onClick = { onEdit { it.copy(birthMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, BirthMode.entries.size),
                            modifier = Modifier.testTag("birth_mode_${mode.name}"),
                        ) { Text(if (mode == BirthMode.EXACT) "知道生日" else "大約幾歲") }
                    }
                }
                when (form.birthMode) {
                    BirthMode.EXACT -> BirthdayField(form, today, onEdit)
                    BirthMode.APPROXIMATE -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(form.approxYears, "歲", "field_years", Modifier.weight(1f)) { v -> onEdit { it.copy(approxYears = v) } }
                        NumberField(form.approxMonths, "個月", "field_months", Modifier.weight(1f)) { v -> onEdit { it.copy(approxMonths = v) } }
                    }
                }
                Hint(problems.birth, "hint_birth")

                // 更多資料
                TextButton(onClick = onToggleMore, modifier = Modifier.testTag("toggle_more")) {
                    Text("更多資料（選填）")
                    Icon(if (state.showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(visible = state.showMore) {
                    MoreFields(form, onEdit)
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PhotoSection(
    form: PetForm,
    state: PetEditorState,
    photoFile: (String) -> File,
    onPickedPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickedPhoto(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (ok && uri != null) onPickedPhoto(uri)
        pendingCameraUri = null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            PetAvatar(
                name = form.name.ifBlank { "?" },
                seed = state.avatarSeed ?: form.name,
                photo = form.photoFile?.let(photoFile),
                size = 96.dp,
                modifier = Modifier.testTag("editor_avatar"),
            )
            if (state.importingPhoto) CircularProgressIndicator()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AssistChip(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("相簿") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("photo_gallery"),
            )
            AssistChip(
                onClick = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    pendingCameraUri = uri.toString()
                    takePhoto.launch(uri)
                },
                label = { Text("拍照") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("photo_camera"),
            )
            if (form.photoFile != null) {
                AssistChip(
                    onClick = onRemovePhoto,
                    label = { Text("移除") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("photo_remove"),
                )
            }
        }
        state.photoError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(form: PetForm, today: LocalDate, onEdit: ((PetForm) -> PetForm) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("field_birthday")) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            when {
                form.birthday == null -> "選擇生日"
                form.birthdayEstimated -> "${form.birthday.format(fmt)}（估計）"
                else -> form.birthday.format(fmt)
            },
        )
    }
    if (open) {
        val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = form.birthday?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { ms ->
                            val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            onEdit { it.copy(birthday = date, birthdayEstimated = false) }
                        }
                        open = false
                    },
                    modifier = Modifier.testTag("date_confirm"),
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun MoreFields(form: PetForm, onEdit: ((PetForm) -> PetForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("more_fields")) {
        SectionLabel("性別")
        Segmented(
            options = listOf(Sex.UNKNOWN to "未設定", Sex.MALE to "公", Sex.FEMALE to "母"),
            selected = form.sex,
            tagPrefix = "sex",
        ) { v -> onEdit { it.copy(sex = v) } }

        SectionLabel("結紮")
        Segmented(
            options = listOf<Pair<Boolean?, String>>(null to "未設定", true to "已結紮", false to "未結紮"),
            selected = form.neutered,
            tagPrefix = "neutered",
        ) { v -> onEdit { it.copy(neutered = v) } }

        OutlinedTextField(
            value = form.breed,
            onValueChange = { v -> onEdit { it.copy(breed = v.take(30)) } },
            label = { Text("品種") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field_breed"),
        )
        OutlinedTextField(
            value = form.chipNumber,
            onValueChange = { v -> onEdit { it.copy(chipNumber = v.filter(Char::isLetterOrDigit).take(20)) } },
            label = { Text("晶片號碼") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("field_chip"),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { v -> onEdit { it.copy(note = v.take(500)) } },
            label = { Text("備註") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("field_note"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, tagPrefix: String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                modifier = Modifier.testTag("${tagPrefix}_$i"),
            ) { Text(text) }
        }
    }
}

@Composable
private fun NumberField(value: String, suffix: String, tag: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(2)) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun SectionLabel(text: String, required: Boolean = false) {
    Text(
        text = if (required) "$text *" else text,
        style = MaterialTheme.typography.titleSmall,
    )
}

/** Explains why saving is disabled, right next to the field (P1-03). */
@Composable
private fun Hint(problem: String?, tag: String) {
    if (problem != null) {
        Text(
            text = problem,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
    }
}
