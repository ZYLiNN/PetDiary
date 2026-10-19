package com.zylinn.petdiary.domain

import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarModelTest {

    private val today = LocalDate.parse("2026-10-20")
    private fun d(s: String) = LocalDate.parse(s)

    private fun record(id: String, pet: String, type: RecordType, day: String, minute: Int = 600, deleted: Long? = null) =
        Record(id, pet, type, d(day).toEpochDay(), minute, createdAt = 1, updatedAt = 1, deletedAt = deleted)

    private fun reminder(id: String, pet: String, type: RecordType, day: String, title: String? = null, completed: Long? = null, deleted: Long? = null) =
        Reminder(id, pet, type, title, d(day).toEpochDay(), 540, createdAt = 1, updatedAt = 1, completedAt = completed, deletedAt = deleted)

    @Test fun gridIsWholeSundayFirstWeeks() {
        val days = CalendarModel.gridDays(YearMonth.of(2026, 10))
        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, days.last().dayOfWeek)
        assertEquals(0, days.size % 7)
        assertEquals(d("2026-09-27"), days.first()) // 10/1/2026 is a Thursday
        assertEquals(d("2026-10-31"), days.last())  // 10/31 is already a Saturday
        assertEquals(35, days.size)
    }

    @Test fun monthStartingOnSundayAndEndingOnSaturdayHasExactlyThoseWeeks() {
        val feb2026 = CalendarModel.gridDays(YearMonth.of(2026, 2)) // Sun Feb 1 .. Sat Feb 28
        assertEquals(28, feb2026.size)
        assertEquals(d("2026-02-01"), feb2026.first())
    }

    @Test fun sixWeekMonthsAreSixRows() {
        assertEquals(42, CalendarModel.gridDays(YearMonth.of(2026, 8)).size) // Sat Aug 1 .. Mon Aug 31 → 6 rows
    }

    @Test fun recordsAndOpenRemindersBecomeItemsSortedByDayAndTime() {
        val items = CalendarModel.items(
            listOf(record("r2", "a", RecordType.VET, "2026-10-22", 900), record("r1", "a", RecordType.VACCINE, "2026-10-22", 600)),
            listOf(reminder("m1", "b", RecordType.DEWORMING, "2026-10-21")),
            emptyMap(), today,
        )
        assertEquals(listOf("m1", "r1", "r2"), items.map { it.id })
        assertTrue(items.first().isReminder)
        assertEquals("驅蟲", items.first().label)
    }

    @Test fun deletedRecordsAndDoneOrDeletedRemindersAreLeftOut() {
        val items = CalendarModel.items(
            listOf(record("r1", "a", RecordType.VET, "2026-10-22", deleted = 5)),
            listOf(
                reminder("m1", "a", RecordType.VET, "2026-10-22", completed = 5),
                reminder("m2", "a", RecordType.VET, "2026-10-22", deleted = 5),
                reminder("m3", "a", RecordType.VET, "2026-10-22"),
            ),
            emptyMap(), today,
        )
        assertEquals(listOf("m3"), items.map { it.id })
    }

    @Test fun feedingAndWalksAreDailyButRemindersAndOtherRecordsAreNot() {
        val items = CalendarModel.items(
            listOf(record("f", "a", RecordType.FEEDING, "2026-10-20"), record("w", "a", RecordType.WALK, "2026-10-20"), record("v", "a", RecordType.VET, "2026-10-20")),
            emptyList(), emptyMap(), today,
        ).associateBy { it.id }
        assertTrue(items.getValue("f").daily)
        assertTrue(items.getValue("w").daily)
        assertFalse(items.getValue("v").daily)
    }

    @Test fun aCustomReminderShowsItsOwnNameAndOthersShowTheTypeWithTitleAsDetail() {
        val items = CalendarModel.items(
            emptyList(),
            listOf(reminder("c", "a", RecordType.CUSTOM, "2026-10-22", title = "剪指甲"), reminder("e", "a", RecordType.MEDICINE, "2026-10-22", title = "點眼藥")),
            emptyMap(), today,
        ).associateBy { it.id }
        assertEquals("剪指甲", items.getValue("c").label)
        assertEquals("藥物", items.getValue("e").label)
        assertEquals("點眼藥", items.getValue("e").detail)
    }

    @Test fun shortNamesUseTheFirstCharacterWhenItIsUnique() {
        assertEquals(mapOf("a" to "小", "b" to "阿"), CalendarModel.shortNames(mapOf("a" to "小花", "b" to "阿福")))
    }

    @Test fun shortNamesFallBackToTheLastCharacterWhenTheFirstCollides() {
        assertEquals(mapOf("a" to "米", "b" to "利"), CalendarModel.shortNames(mapOf("a" to "雷米", "b" to "雷利")))
    }

    @Test fun shortNamesFallBackFurtherWhenBothEndsCollide() {
        val r = CalendarModel.shortNames(mapOf("a" to "雷小米", "b" to "雷大米"))
        assertEquals(setOf("雷小", "雷大"), r.values.toSet())
        assertEquals("豆", CalendarModel.shortNames(mapOf("a" to "豆")).getValue("a"))
        assertEquals("?", CalendarModel.shortNames(mapOf("a" to " ")).getValue("a"))
    }
}
