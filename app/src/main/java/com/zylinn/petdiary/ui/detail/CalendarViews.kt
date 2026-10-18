package com.zylinn.petdiary.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private val weekdayNames = listOf("日", "一", "二", "三", "四", "五", "六")

/** "10 月 18 日（六）" */
fun LocalDate.titleText(): String = "$monthValue 月 $dayOfMonth 日（${weekdayNames[dayOfWeek.value % 7]}）"

/** One-week strip with record dots (S5). Arrows move a week; future weeks clamp to today. */
@Composable
fun WeekStrip(
    state: CalendarState,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onToggleExpand: () -> Unit,
    showExpandToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .testTag("week_strip"),
        ) {
            IconButton(onClick = { onShiftWeek(-1) }, modifier = Modifier.testTag("week_prev")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一週")
            }
            state.week.forEach { day ->
                DayCell(
                    day = day,
                    showWeekday = true,
                    selected = day == state.selected,
                    hasRecords = day in state.daysWithRecords,
                    enabled = !day.isAfter(state.today),
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f),
                )
            }
            IconButton(
                onClick = { onShiftWeek(1) },
                enabled = state.week.last().isBefore(state.today),
                modifier = Modifier.testTag("week_next"),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一週") }
        }
        if (showExpandToggle) {
            TextButton(onClick = onToggleExpand, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("toggle_month")) {
                Icon(if (state.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                Text(if (state.expanded) "收合月曆" else "展開整個月曆", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Full month grid with record dots; picking a day collapses back to the week (S5). */
@Composable
fun MonthCalendar(
    state: CalendarState,
    onSelect: (LocalDate) -> Unit,
    onShiftMonth: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month: YearMonth = state.visibleMonth
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(8.dp)
            .testTag("month_calendar"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftMonth(-1) }, modifier = Modifier.testTag("month_prev")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上個月")
            }
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag("month_title"),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { onShiftMonth(1) },
                enabled = month.isBefore(YearMonth.from(state.today)),
                modifier = Modifier.testTag("month_next"),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下個月") }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayNames.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        var weekStart = first
        while (!weekStart.isAfter(last)) {
            Row(Modifier.fillMaxWidth()) {
                (0L until 7L).map(weekStart::plusDays).forEach { day ->
                    if (YearMonth.from(day) == month) {
                        DayCell(
                            day = day,
                            showWeekday = false,
                            selected = day == state.selected,
                            hasRecords = day in state.daysWithRecords,
                            enabled = !day.isAfter(state.today),
                            onClick = { onSelect(day) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    showWeekday: Boolean,
    selected: Boolean,
    hasRecords: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = when {
        selected -> scheme.onPrimary
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> scheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "${day.monthValue} 月 ${day.dayOfMonth} 日" + if (hasRecords) "，有紀錄" else ""
            }
            .padding(vertical = 4.dp)
            .testTag("day_${day.toEpochDay()}"),
    ) {
        if (showWeekday) {
            Text(weekdayNames[day.dayOfWeek.value % 7], style = MaterialTheme.typography.labelSmall, color = fg)
        }
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = fg,
        )
        Box(Modifier.height(6.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (hasRecords) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (selected) scheme.onPrimary else scheme.primary)
                        .testTag("dot_${day.toEpochDay()}"),
                )
            }
        }
    }
}
