package com.zylinn.petdiary.ui.reminders

import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderInput
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import com.zylinn.petdiary.domain.ReminderSchedule
import java.time.LocalDate

const val REMINDER_TITLE_MAX = 50

/** Everything the reminder editor holds; pure data so the rules are unit-tested. */
data class ReminderForm(
    val petId: String?,
    val type: RecordType = RecordType.MEDICINE,
    val title: String = "",
    val date: LocalDate,
    val minuteOfDay: Int = 9 * 60,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val repeatEvery: String = "3",
) {
    /** First problem to fix, or null when the form can be saved. A new reminder can't be in the past. */
    fun problem(today: LocalDate, isEdit: Boolean): String? {
        if (petId == null) return "請選擇寵物"
        if (title.trim().length > REMINDER_TITLE_MAX) return "名稱最多 $REMINDER_TITLE_MAX 個字"
        if (!isEdit && date.isBefore(today)) return "日期不能是過去"
        if (repeat == ReminderRepeat.EVERY_N_DAYS) {
            val n = repeatEvery.toIntOrNull()
            if (n == null || n !in 1..ReminderSchedule.EVERY_N_MAX) return "天數請填 1～${ReminderSchedule.EVERY_N_MAX}"
        }
        return null
    }

    fun toInput() = ReminderInput(
        petId = petId!!,
        type = type,
        title = title,
        dueEpochDay = date.toEpochDay(),
        minuteOfDay = minuteOfDay,
        repeat = repeat,
        repeatEvery = repeatEvery.toIntOrNull() ?: 1,
    )

    companion object {
        fun from(r: Reminder) = ReminderForm(
            petId = r.petId, type = r.type, title = r.title.orEmpty(), date = LocalDate.ofEpochDay(r.dueEpochDay),
            minuteOfDay = r.minuteOfDay, repeat = r.repeat, repeatEvery = r.repeatEvery.toString(),
        )
    }
}
