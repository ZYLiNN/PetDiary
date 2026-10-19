package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.reminder.ReminderRepeat
import java.time.LocalDate
import java.time.ZoneId

/** Pure scheduling rules for reminders, unit-tested. */
object ReminderSchedule {

    const val EVERY_N_MAX = 365

    /**
     * Next due date after completing a reminder on [today] (P4-04): the first occurrence of the
     * schedule that is later than both [today] and the current [due] date. null = does not repeat.
     * Monthly / yearly keep the original day of month where the month allows it.
     */
    fun next(due: LocalDate, repeat: ReminderRepeat, every: Int, today: LocalDate): LocalDate? {
        val base = if (today.isAfter(due)) today else due
        return when (repeat) {
            ReminderRepeat.NONE -> null
            ReminderRepeat.DAILY -> base.plusDays(1)
            ReminderRepeat.EVERY_N_DAYS -> {
                val step = every.coerceIn(1, EVERY_N_MAX).toLong()
                val k = Math.floorDiv(base.toEpochDay() - due.toEpochDay(), step) + 1
                due.plusDays(k * step)
            }
            ReminderRepeat.MONTHLY -> generateSequence(1L) { it + 1 }.map(due::plusMonths).first { it.isAfter(base) }
            ReminderRepeat.YEARLY -> generateSequence(1L) { it + 1 }.map(due::plusYears).first { it.isAfter(base) }
        }
    }

    /** D37: next date counted from the day it was actually done (today + one interval). null = does not repeat. */
    fun nextFromDone(repeat: ReminderRepeat, every: Int, today: LocalDate): LocalDate? = when (repeat) {
        ReminderRepeat.NONE -> null
        ReminderRepeat.DAILY -> today.plusDays(1)
        ReminderRepeat.EVERY_N_DAYS -> today.plusDays(every.coerceIn(1, EVERY_N_MAX).toLong())
        ReminderRepeat.MONTHLY -> today.plusMonths(1)
        ReminderRepeat.YEARLY -> today.plusYears(1)
    }

    fun repeatLabel(repeat: ReminderRepeat, every: Int): String = when (repeat) {
        ReminderRepeat.NONE -> "只提醒一次"
        ReminderRepeat.DAILY -> "每天"
        ReminderRepeat.EVERY_N_DAYS -> "每 $every 天"
        ReminderRepeat.MONTHLY -> "每月"
        ReminderRepeat.YEARLY -> "每年"
    }

    fun dueMillis(dueEpochDay: Long, minuteOfDay: Int, zone: ZoneId): Long =
        LocalDate.ofEpochDay(dueEpochDay).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

    /** Overdue = the due time has passed (P4-05). */
    fun isOverdue(dueEpochDay: Long, minuteOfDay: Int, nowMillis: Long, zone: ZoneId) =
        dueMillis(dueEpochDay, minuteOfDay, zone) < nowMillis

    private val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

    /** "今天 09:00" / "明天 09:00" / "昨天 09:00" / "10/25（六）09:00" (year shown when not this year). */
    fun dueLabel(day: LocalDate, minuteOfDay: Int, today: LocalDate): String {
        val time = RecordFormat.time(minuteOfDay)
        return when (day.toEpochDay() - today.toEpochDay()) {
            0L -> "今天 $time"
            1L -> "明天 $time"
            -1L -> "昨天 $time"
            else -> RecordFormat.shortDate(day, today) + "（${weekdays[day.dayOfWeek.value - 1]}）" + time
        }
    }
}
