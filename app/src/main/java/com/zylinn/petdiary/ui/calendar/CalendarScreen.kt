package com.zylinn.petdiary.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.record.FAMILY_PET_ID
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.ui.detail.AddRecordPanel
import com.zylinn.petdiary.ui.detail.PetChoice
import com.zylinn.petdiary.domain.CalendarItem
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.ui.common.SettingsAction
import com.zylinn.petdiary.ui.theme.PetPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WIDE_AT = 720.dp

/**
 * Calendar tab (P10): the whole month for every pet at a glance. Each pet has its own colour; records are solid,
 * open reminders dashed; feeding / walks (daily) are just dots in the cell. Tap a day for its full list below
 * (beside it on a wide screen); tap an entry to open that pet's page on that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    onOpenDay: (petId: String, day: LocalDate) -> Unit,
    onAddReminder: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onAddRecord: (type: RecordType, petIds: List<String>, day: LocalDate) -> Unit = { _, _, _ -> },
    /** The full reminder list (P13: the 提醒 tab moved here). */
    onOpenReminders: (() -> Unit)? = null,
    /** A shared purchase has no pet page, so it opens in the record editor (D84). */
    onOpenSharedRecord: (recordId: String, day: LocalDate) -> Unit = { _, _ -> },
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var addingRecord by rememberSaveable { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = ui.pets.associate { it.id to PetPalette.swatch(it.color, dark) }
    val shorts = ui.pets.associate { it.id to it.short }
    val names = ui.pets.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("行事曆") },
                actions = {
                    // Pet filter lives here instead of a chip row, so the month and the day list get the room (P13).
                    if (ui.pets.size > 1) PetFilterAction(ui.pets, colors, vm::togglePet, vm::showAllPets)
                    onOpenReminders?.let { open ->
                        IconButton(onClick = open, modifier = Modifier.testTag("cal_open_reminders")) {
                            Icon(Icons.Outlined.NotificationsNone, contentDescription = "提醒清單")
                        }
                    }
                    SettingsAction(onOpenSettings)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= WIDE_AT
            val month: @Composable (Modifier) -> Unit = { m ->
                MonthColumn(ui, vm, colors, shorts, wide, m)
            }
            val panel: @Composable (Modifier) -> Unit = { m ->
                DayPanel(
                    ui.selected, ui.today, ui.selectedItems, ui.pets, colors, names, onOpenDay, onAddReminder,
                    onAddRecord = { addingRecord = true }, onOpenSharedRecord = onOpenSharedRecord, modifier = m,
                )
            }
            if (addingRecord && ui.pets.isNotEmpty()) {
                // Same panel as on a pet's page: pick one or more pets, then a type (P11-02).
                AddRecordPanel(
                    wide = wide,
                    currentPetId = (ui.pets.firstOrNull { it.visible } ?: ui.pets.first()).id,
                    pets = ui.pets.map { PetChoice(it.id, it.name) },
                    quickFeeding = null,
                    quickWalk = null,
                    onDismiss = { addingRecord = false },
                    onPickType = { type, petIds ->
                        addingRecord = false
                        onAddRecord(type, petIds, ui.selected)
                    },
                    onQuickFeed = {},
                    onQuickWalk = {},
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    month(Modifier.weight(3f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    panel(Modifier.weight(1.2f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    month(Modifier.fillMaxWidth())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthColumn(
    ui: CalendarUi,
    vm: CalendarViewModel,
    colors: Map<String, com.zylinn.petdiary.ui.theme.PetSwatch>,
    shorts: Map<String, String>,
    wide: Boolean,
    modifier: Modifier,
) {
    val weeks = ui.days.chunked(7)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ui.month.year} 年 ${ui.month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).testTag("cal_month_title"),
            )
            TextButton(onClick = vm::goToday, modifier = Modifier.testTag("cal_today")) { Text("今天") }
            IconButton(onClick = vm::previousMonth, modifier = Modifier.testTag("cal_prev")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上個月") }
            IconButton(onClick = vm::nextMonth, modifier = Modifier.testTag("cal_next")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下個月") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            for (i in 0 until 7) {
                val dow = DayOfWeek.SUNDAY.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.TRADITIONAL_CHINESE).let { if (it.length > 1) it.takeLast(1) else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == 0 || i == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        // Swipe sideways to change month.
        var drag = 0f
        Column(
            Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier).pointerInput(ui.month) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 120f) vm.previousMonth() else if (drag < -120f) vm.nextMonth() },
                ) { _, delta -> drag += delta }
            },
        ) {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier.height(CompactCellHeight))) {
                    week.forEach { day ->
                        DayCell(
                            day = day, ui = ui, colors = colors, shorts = shorts, wide = wide,
                            onClick = { vm.select(day) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            }
        }
    }
}

private val CompactCellHeight = 60.dp

/** Filter button: a menu with a checkbox per pet; the icon fills in while some pet is hidden. */
@Composable
private fun PetFilterAction(
    pets: List<CalendarPet>,
    colors: Map<String, com.zylinn.petdiary.ui.theme.PetSwatch>,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val filtering = pets.any { !it.visible }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("cal_filter")) {
            Icon(
                if (filtering) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                contentDescription = if (filtering) "選擇寵物（已篩選）" else "選擇寵物",
                tint = if (filtering) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            pets.forEach { p ->
                val sw = colors.getValue(p.id)
                DropdownMenuItem(
                    text = { Text(p.name) },
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = p.visible, onCheckedChange = null)
                            Box(Modifier.size(12.dp).background(sw.fill, CircleShape).border(1.dp, sw.line, CircleShape))
                        }
                    },
                    onClick = { onToggle(p.id) }, // menu stays open so several pets can be switched at once
                    modifier = Modifier.testTag("cal_pet_${p.id}"),
                )
            }
            if (filtering) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("全部顯示") }, onClick = { onShowAll(); open = false }, modifier = Modifier.testTag("cal_pet_all"))
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    ui: CalendarUi,
    colors: Map<String, com.zylinn.petdiary.ui.theme.PetSwatch>,
    shorts: Map<String, String>,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val inMonth = day.month == ui.month.month
    val items = ui.items[day].orEmpty()
    // Purchases are listed under the day, not drawn in the cell, so the month stays readable (P14-03).
    val marks = items.filter { !it.daily && it.type != RecordType.PURCHASE }
    val maxShown = if (wide) 4 else 2
    val dots = items.filter { it.daily }.map { it.petId }.distinct()
    val selected = day == ui.selected
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .background(if (inMonth) scheme.surface else scheme.surfaceVariant.copy(alpha = 0.5f))
            .then(if (selected) Modifier.border(2.dp, scheme.primary) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .semantics { contentDescription = "${day.monthValue}月${day.dayOfMonth}日，${items.size} 件" }
            .testTag("cal_cell_${day.toEpochDay()}"),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(
            Modifier.size(20.dp).align(if (wide) Alignment.Start else Alignment.CenterHorizontally)
                .then(if (day == ui.today) Modifier.background(scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = when {
                    day == ui.today -> scheme.onPrimary
                    inMonth -> scheme.onSurface
                    else -> scheme.onSurface.copy(alpha = 0.4f)
                },
            )
        }
        marks.take(maxShown).forEach { CellChip(it, colors, shorts, wide) }
        if (marks.size > maxShown) {
            Text("+${marks.size - maxShown}", fontSize = 10.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp).testTag("cal_more_${day.toEpochDay()}"))
        }
        Box(Modifier.weight(1f, fill = false))
        if (dots.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 1.dp), horizontalArrangement = Arrangement.Center) {
                dots.forEach { id ->
                    Box(Modifier.padding(horizontal = 1.dp).size(5.dp).background(colors[id]?.line ?: Color.Gray, CircleShape).testTag("cal_dot_${day.toEpochDay()}_$id"))
                }
            }
        }
    }
}

@Composable
private fun CellChip(item: CalendarItem, colors: Map<String, com.zylinn.petdiary.ui.theme.PetSwatch>, shorts: Map<String, String>, wide: Boolean) {
    val sw = colors[item.petId] ?: return
    val shape = RoundedCornerShape(4.dp)
    val text = (if (wide) RecordFormat.time(item.minuteOfDay) + " " else "") + (shorts[item.petId] ?: "") + " " + item.label
    val base = Modifier.fillMaxWidth().heightIn(min = if (wide) 18.dp else 14.dp)
    Text(
        text, fontSize = if (wide) 12.sp else 10.sp, lineHeight = if (wide) 16.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (item.isReminder) MaterialTheme.colorScheme.onSurface else sw.ink,
        modifier = (if (item.isReminder) base.dashedBorder(sw.line, 4.dp) else base.background(sw.fill, shape).border(1.dp, sw.line, shape))
            .padding(horizontal = 3.dp, vertical = 1.dp)
            .testTag("cal_item_${item.id}"),
    )
}

/** A dashed rounded outline, used for reminders that are still to do. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color, cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPanel(
    day: LocalDate,
    today: LocalDate,
    items: List<CalendarItem>,
    pets: List<CalendarPet>,
    colors: Map<String, com.zylinn.petdiary.ui.theme.PetSwatch>,
    names: Map<String, String>,
    onOpenDay: (String, LocalDate) -> Unit,
    onAddReminder: (LocalDate) -> Unit,
    onAddRecord: () -> Unit,
    onOpenSharedRecord: (String, LocalDate) -> Unit,
    modifier: Modifier,
) {
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${day.monthValue}月${day.dayOfMonth}日（${weekday.takeLast(1)}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("cal_panel_title"))
            when {
                day == today -> Text("今天", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                day.isAfter(today) -> Text("還沒到", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (items.isEmpty()) {
            Text("這天沒有紀錄或提醒。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("cal_panel_empty"))
        }
        pets.filter { p -> items.any { it.petId == p.id } }.forEach { p ->
            val sw = colors.getValue(p.id)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.size(12.dp).background(sw.fill, CircleShape).border(1.dp, sw.line, CircleShape))
                Text(names.getValue(p.id), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items.filter { it.petId == p.id }.forEach { item ->
                PanelRow(item, sw) { onOpenDay(item.petId, day) }
            }
        }
        val shared = items.filter { it.petId == FAMILY_PET_ID }
        if (shared.isNotEmpty()) {
            val sw = PetPalette.familySwatch(MaterialTheme.colorScheme.background.luminance() < 0.5f)
            Text(RecordFormat.FAMILY_NAME, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            shared.forEach { item -> PanelRow(item, sw) { onOpenSharedRecord(item.id, day) } }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Records can't be in the future, so a day still to come only offers a reminder.
            if (pets.isNotEmpty() && !day.isAfter(today)) {
                OutlinedButton(onClick = onAddRecord, modifier = Modifier.testTag("cal_add_record")) { Text("＋ 新增紀錄") }
            }
            OutlinedButton(onClick = { onAddReminder(day) }, modifier = Modifier.testTag("cal_add_reminder")) { Text("＋ 新增提醒") }
        }
    }
}

@Composable
private fun PanelRow(item: CalendarItem, sw: com.zylinn.petdiary.ui.theme.PetSwatch, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape).clickable(onClick = onClick)
            .semantics { contentDescription = (if (item.isReminder) "提醒，" else "") + item.label }
            .testTag("cal_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(40.dp).background(sw.line))
        Box(
            Modifier.padding(start = 10.dp).size(12.dp)
                .then(if (item.isReminder) Modifier.dashedBorder(MaterialTheme.colorScheme.onSurface, 6.dp) else Modifier.background(MaterialTheme.colorScheme.onSurface, CircleShape)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(item.label + if (item.isReminder) "（提醒）" else "", style = MaterialTheme.typography.bodyMedium)
            if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(RecordFormat.time(item.minuteOfDay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
    }
}

