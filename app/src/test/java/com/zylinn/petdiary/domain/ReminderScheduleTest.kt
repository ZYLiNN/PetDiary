package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.reminder.ReminderRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderScheduleTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun next(due: String, repeat: ReminderRepeat, every: Int, today: String) =
        ReminderSchedule.next(d(due), repeat, every, d(today))

    // P4-04
    @Test fun oneTimeHasNoNext() = assertNull(next("2026-10-20", ReminderRepeat.NONE, 1, "2026-10-20"))

    @Test fun dailyMovesToTomorrow() {
        assertEquals(d("2026-10-21"), next("2026-10-20", ReminderRepeat.DAILY, 1, "2026-10-20"))
    }

    @Test fun overdueDailyJumpsPastToday() {
        assertEquals(d("2026-10-24"), next("2026-10-20", ReminderRepeat.DAILY, 1, "2026-10-23"))
    }

    @Test fun completingEarlyStillAdvancesBeyondTheDueDate() {
        assertEquals(d("2026-10-21"), next("2026-10-20", ReminderRepeat.DAILY, 1, "2026-10-18"))
    }

    @Test fun everyNDaysStaysOnItsOwnSchedule() {
        // due 10/20, every 3 days: 10/23, 10/26, ...  Completed on 10/24 (late) → next is 10/26, not 10/27.
        assertEquals(d("2026-10-23"), next("2026-10-20", ReminderRepeat.EVERY_N_DAYS, 3, "2026-10-20"))
        assertEquals(d("2026-10-26"), next("2026-10-20", ReminderRepeat.EVERY_N_DAYS, 3, "2026-10-24"))
        assertEquals(d("2026-10-26"), next("2026-10-20", ReminderRepeat.EVERY_N_DAYS, 3, "2026-10-23"))
    }

    @Test fun monthlyKeepsDayOfMonth() {
        assertEquals(d("2026-11-15"), next("2026-10-15", ReminderRepeat.MONTHLY, 1, "2026-10-15"))
        assertEquals(d("2026-12-15"), next("2026-10-15", ReminderRepeat.MONTHLY, 1, "2026-11-20"))
    }

    @Test fun monthlyClampsShortMonths() {
        assertEquals(d("2027-02-28"), next("2026-12-31", ReminderRepeat.MONTHLY, 1, "2027-01-31"))
    }

    @Test fun yearlyHandlesLeapDay() {
        assertEquals(d("2027-10-15"), next("2026-10-15", ReminderRepeat.YEARLY, 1, "2026-10-15"))
        assertEquals(d("2029-02-28"), next("2028-02-29", ReminderRepeat.YEARLY, 1, "2028-03-01").let { it })
    }

    @Test fun everyNIsClamped() {
        assertEquals(d("2026-10-21"), next("2026-10-20", ReminderRepeat.EVERY_N_DAYS, 0, "2026-10-20"))
    }

    // D37: counted from the day it was actually done
    @Test fun nextFromDoneCountsFromToday() {
        val today = d("2026-10-24")
        assertNull(ReminderSchedule.nextFromDone(ReminderRepeat.NONE, 1, today))
        assertEquals(d("2026-10-25"), ReminderSchedule.nextFromDone(ReminderRepeat.DAILY, 1, today))
        assertEquals(d("2026-10-27"), ReminderSchedule.nextFromDone(ReminderRepeat.EVERY_N_DAYS, 3, today))
        assertEquals(d("2026-11-24"), ReminderSchedule.nextFromDone(ReminderRepeat.MONTHLY, 1, today))
        assertEquals(d("2027-10-24"), ReminderSchedule.nextFromDone(ReminderRepeat.YEARLY, 1, today))
    }

    @Test fun labels() {
        assertEquals("只提醒一次", ReminderSchedule.repeatLabel(ReminderRepeat.NONE, 1))
        assertEquals("每天", ReminderSchedule.repeatLabel(ReminderRepeat.DAILY, 1))
        assertEquals("每 3 天", ReminderSchedule.repeatLabel(ReminderRepeat.EVERY_N_DAYS, 3))
        assertEquals("每月", ReminderSchedule.repeatLabel(ReminderRepeat.MONTHLY, 1))
        assertEquals("每年", ReminderSchedule.repeatLabel(ReminderRepeat.YEARLY, 1))
    }

    @Test fun dueLabels() {
        val today = d("2026-10-19")
        assertEquals("今天 09:00", ReminderSchedule.dueLabel(today, 540, today))
        assertEquals("明天 14:30", ReminderSchedule.dueLabel(d("2026-10-20"), 870, today))
        assertEquals("昨天 08:00", ReminderSchedule.dueLabel(d("2026-10-18"), 480, today))
        assertEquals("10/25（日）09:00", ReminderSchedule.dueLabel(d("2026-10-25"), 540, today))
        assertEquals("2027/1/5（二）09:00", ReminderSchedule.dueLabel(d("2027-01-05"), 540, today))
    }

    // P4-05
    @Test fun overdueMeansTheTimeHasPassed() {
        val zone = ZoneId.of("Asia/Taipei")
        val day = d("2026-10-19").toEpochDay()
        val at = ReminderSchedule.dueMillis(day, 600, zone)
        assertFalse(ReminderSchedule.isOverdue(day, 600, at - 1, zone))
        assertFalse(ReminderSchedule.isOverdue(day, 600, at, zone))
        assertTrue(ReminderSchedule.isOverdue(day, 600, at + 1, zone))
    }
}
