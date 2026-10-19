package com.zylinn.petdiary.ui.reminders

import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReminderFormTest {

    private val today = LocalDate.parse("2026-10-19")
    private val ok = ReminderForm(petId = "p1", type = RecordType.DEWORMING, date = today.plusDays(3))

    // P4-01
    @Test fun validFormHasNoProblem() = assertNull(ok.problem(today, isEdit = false))

    @Test fun petRequired() = assertEquals("請選擇寵物", ok.copy(petId = null).problem(today, false))

    @Test fun newReminderCannotBeInThePast() {
        assertEquals("日期不能是過去", ok.copy(date = today.minusDays(1)).problem(today, false))
        assertNull(ok.copy(date = today).problem(today, false))
    }

    @Test fun editingAnOverdueReminderIsAllowed() = assertNull(ok.copy(date = today.minusDays(5)).problem(today, true))

    @Test fun everyNDaysNeedsValidNumber() {
        val f = ok.copy(repeat = ReminderRepeat.EVERY_N_DAYS)
        assertNull(f.copy(repeatEvery = "3").problem(today, false))
        assertEquals("天數請填 1～365", f.copy(repeatEvery = "").problem(today, false))
        assertEquals("天數請填 1～365", f.copy(repeatEvery = "0").problem(today, false))
        assertEquals("天數請填 1～365", f.copy(repeatEvery = "366").problem(today, false))
    }

    @Test fun everyIsIgnoredForOtherRepeats() = assertNull(ok.copy(repeat = ReminderRepeat.DAILY, repeatEvery = "").problem(today, false))

    @Test fun titleLimit() =
        assertEquals("名稱最多 50 個字", ok.copy(title = "藥".repeat(51)).problem(today, false))

    @Test fun toInputCarriesEverything() {
        val i = ok.copy(title = "點眼藥", minuteOfDay = 570, repeat = ReminderRepeat.EVERY_N_DAYS, repeatEvery = "12").toInput()
        assertEquals("p1", i.petId)
        assertEquals(RecordType.DEWORMING, i.type)
        assertEquals("點眼藥", i.title)
        assertEquals(570, i.minuteOfDay)
        assertEquals(12, i.repeatEvery)
        assertEquals(today.plusDays(3).toEpochDay(), i.dueEpochDay)
    }
}
