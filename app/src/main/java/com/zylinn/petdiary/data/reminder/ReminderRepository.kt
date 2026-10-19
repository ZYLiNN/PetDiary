package com.zylinn.petdiary.data.reminder

import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.domain.ReminderSchedule
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ReminderInput(
    val petId: String,
    val type: RecordType,
    val title: String?,
    val dueEpochDay: Long,
    val minuteOfDay: Int,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val repeatEvery: Int = 1,
)

/** What a completion changed, so the "復原" button can put everything back (P4-12). */
data class Completion(
    val reminderId: String,
    val recordId: String?,
    val previous: Reminder,
)

/** A repeating reminder finished late, where "keep the old rhythm" and "count from today" differ (D37). */
data class LateChoice(val id: String, val lateDays: Int, val rolled: LocalDate, val anchored: LocalDate)

class ReminderRepository(
    private val reminders: ReminderDao,
    private val records: RecordRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeOpen(): Flow<List<ReminderRow>> = reminders.observeOpen()

    /** Names used for 自訂 reminders, newest first, offered as quick picks (D36). */
    val customTitles: Flow<List<String>> = reminders.observeCustomTitles()
    fun observeOpenInRange(from: Long, to: Long) = reminders.observeOpenInRange(from, to)
    fun observeOpenForPet(petId: String, from: Long, to: Long) = reminders.observeOpenForPet(petId, from, to)
    suspend fun get(id: String): Reminder? = reminders.getById(id)

    private fun ReminderInput.normalized() = copy(
        title = title?.trim()?.ifEmpty { null },
        minuteOfDay = minuteOfDay.coerceIn(0, 1439),
        repeatEvery = if (repeat == ReminderRepeat.EVERY_N_DAYS) repeatEvery.coerceIn(1, ReminderSchedule.EVERY_N_MAX) else 1,
    )

    suspend fun add(input: ReminderInput, sourceRecordId: String? = null): Reminder {
        val i = input.normalized()
        val now = clock.millis()
        return Reminder(
            id = newId(), petId = i.petId, type = i.type, title = i.title,
            dueEpochDay = i.dueEpochDay, minuteOfDay = i.minuteOfDay, repeat = i.repeat, repeatEvery = i.repeatEvery,
            sourceRecordId = sourceRecordId, createdAt = now, updatedAt = now,
        ).also { reminders.upsert(it) }
    }

    /** Editing resets the "already notified" mark when the time changes, so the new time fires. */
    suspend fun update(id: String, input: ReminderInput) {
        val existing = reminders.getById(id) ?: return
        val i = input.normalized()
        val moved = existing.dueEpochDay != i.dueEpochDay || existing.minuteOfDay != i.minuteOfDay
        reminders.upsert(
            existing.copy(
                petId = i.petId, type = i.type, title = i.title, dueEpochDay = i.dueEpochDay, minuteOfDay = i.minuteOfDay,
                repeat = i.repeat, repeatEvery = i.repeatEvery, updatedAt = clock.millis(),
                notifiedFor = if (moved) null else existing.notifiedFor,
            ),
        )
    }

    suspend fun delete(id: String) = reminders.softDelete(id, clock.millis())

    suspend fun markNotified(id: String, dueMillis: Long) = reminders.markNotified(id, dueMillis)

    suspend fun row(id: String) = reminders.getRow(id)

    /** Non-null only when finishing [id] now is late AND the two ways of picking the next date disagree. */
    suspend fun lateChoice(id: String): LateChoice? {
        val r = reminders.getById(id) ?: return null
        if (r.deletedAt != null || r.completedAt != null || r.repeat == ReminderRepeat.NONE) return null
        val today = LocalDate.now(clock)
        val due = LocalDate.ofEpochDay(r.dueEpochDay)
        if (!today.isAfter(due)) return null
        val anchored = ReminderSchedule.next(due, r.repeat, r.repeatEvery, today) ?: return null
        val rolled = ReminderSchedule.nextFromDone(r.repeat, r.repeatEvery, today) ?: return null
        if (anchored == rolled) return null
        return LateChoice(id, (today.toEpochDay() - due.toEpochDay()).toInt(), rolled, anchored)
    }

    /**
     * Ticking a reminder off (list, today's to-do, or the notification button): creates the matching
     * record today at the current time, then moves a repeating reminder to its next date (P4-03/04/11).
     * When finished late, [rollFromToday] picks the next date: counted from today (default, D37) or kept on the
     * original rhythm. Returns null if the reminder is gone or already done.
     */
    suspend fun complete(id: String, rollFromToday: Boolean = true): Completion? {
        val r = reminders.getById(id) ?: return null
        if (r.deletedAt != null || r.completedAt != null) return null
        val today = LocalDate.now(clock)
        val now = LocalTime.now(clock)
        // A 自訂 reminder has no matching record type, so ticking it off only advances / closes it (D36).
        val recordId = if (r.type == RecordType.CUSTOM) null else records.addForPets(
            listOf(r.petId),
            RecordInput(
                type = r.type,
                dateEpochDay = today.toEpochDay(),
                minuteOfDay = now.hour * 60 + now.minute,
                title = r.title ?: r.type.label,
            ),
        ).single()
        val due = LocalDate.ofEpochDay(r.dueEpochDay)
        val next = if (rollFromToday && today.isAfter(due)) ReminderSchedule.nextFromDone(r.repeat, r.repeatEvery, today)
        else ReminderSchedule.next(due, r.repeat, r.repeatEvery, today)
        reminders.upsert(
            if (next == null) r.copy(completedAt = clock.millis(), updatedAt = clock.millis())
            else r.copy(dueEpochDay = next.toEpochDay(), notifiedFor = null, updatedAt = clock.millis()),
        )
        return Completion(r.id, recordId, r)
    }

    /** P4-12: removes the auto-created record and puts the reminder back as it was. */
    suspend fun undo(c: Completion) {
        c.recordId?.let { records.moveToTrash(it) }
        reminders.upsert(c.previous.copy(updatedAt = clock.millis()))
    }
}
