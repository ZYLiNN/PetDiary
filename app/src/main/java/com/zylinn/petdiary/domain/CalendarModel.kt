package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.domain.RecordFormat.label
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** One line on the calendar: something that happened (a record) or is still to do (an open reminder). */
data class CalendarItem(
    val id: String,
    val petId: String,
    val day: LocalDate,
    val minuteOfDay: Int,
    val isReminder: Boolean,
    val type: RecordType,
    /** Short text for the little box inside a day cell. */
    val label: String,
    /** Details for the day list (dosage, minutes walked, reminder title …). */
    val detail: String,
    /** Feeding and walks happen every day; they are shown as dots in the cell, and in full in the day list. */
    val daily: Boolean,
)

/** Pure rules for the calendar tab (P10), unit-tested. */
object CalendarModel {

    /** The weeks shown for [month]: Sunday-first, from the Sunday on or before the 1st to the Saturday on or after the last day. */
    fun gridDays(month: YearMonth): List<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    fun items(
        records: List<Record>,
        reminders: List<Reminder>,
        supplementNames: Map<String, String>,
        today: LocalDate,
    ): List<CalendarItem> {
        val fromRecords = records.filter { it.deletedAt == null }.map { r ->
            CalendarItem(
                id = r.id, petId = r.petId, day = LocalDate.ofEpochDay(r.dateEpochDay), minuteOfDay = r.minuteOfDay, isReminder = false,
                // A 自訂 record (from a ticked-off reminder) is named like the reminder, e.g. 剪指甲 (P14-01).
                type = r.type, label = if (r.type == RecordType.CUSTOM) r.title ?: r.type.label else r.type.label,
                detail = RecordFormat.summary(if (r.type == RecordType.CUSTOM) r.copy(title = null) else r, supplementNames, today),
                daily = r.type == RecordType.FEEDING || r.type == RecordType.WALK || r.type == RecordType.PLAY,
            )
        }
        val fromReminders = reminders.filter { it.deletedAt == null && it.completedAt == null }.map { r ->
            CalendarItem(
                id = r.id, petId = r.petId, day = LocalDate.ofEpochDay(r.dueEpochDay), minuteOfDay = r.minuteOfDay, isReminder = true,
                type = r.type, label = if (r.type == RecordType.CUSTOM) r.title ?: r.type.label else r.type.label,
                detail = if (r.type == RecordType.CUSTOM) "" else r.title.orEmpty(), daily = false,
            )
        }
        return (fromRecords + fromReminders).sortedWith(compareBy({ it.day }, { it.minuteOfDay }, { it.isReminder }))
    }

    /**
     * The one or two characters that stand for each pet inside a cell, so colour is never the only clue.
     * First character when it is unique ("小花" / "阿福" → 小 / 阿), else the last ("雷米" / "雷利" → 米 / 利), else the first two.
     */
    fun shortNames(names: Map<String, String>): Map<String, String> {
        val clean = names.mapValues { it.value.trim().ifEmpty { "?" } }
        fun pick(strategy: (String) -> String): Map<String, String> = clean.mapValues { strategy(it.value) }
        val strategies = listOf<(String) -> String>({ it.take(1) }, { it.takeLast(1) }, { it.take(2) })
        return clean.mapValues { (id, name) ->
            strategies.map { pick(it) }.firstNotNullOfOrNull { candidates ->
                val mine = candidates.getValue(id)
                mine.takeIf { candidates.count { it.value == mine } == 1 }
            } ?: name
        }
    }
}
