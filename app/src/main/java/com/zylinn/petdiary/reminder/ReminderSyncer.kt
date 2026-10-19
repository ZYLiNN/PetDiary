package com.zylinn.petdiary.reminder

import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.domain.ReminderSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Keeps AlarmManager in step with the database: whenever the open reminders change, every alarm is
 * (re)armed and alarms of removed / finished reminders are cancelled. Overdue reminders that were never
 * announced fire right away; announced ones stay quiet (no repeat on every app start).
 */
class ReminderSyncer(
    private val scope: CoroutineScope,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    private var job: Job? = null
    private val armed = mutableSetOf<String>()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            repository.observeOpen().collect { rows ->
                val now = clock.millis()
                val openIds = rows.map { it.reminder.id }.toSet()
                (armed - openIds).forEach {
                    scheduler.cancel(it)
                    notifier.cancel(it)
                }
                armed.retainAll(openIds)
                rows.forEach { row ->
                    val r = row.reminder
                    val due = ReminderSchedule.dueMillis(r.dueEpochDay, r.minuteOfDay, clock.zone)
                    when {
                        due > now -> scheduler.schedule(r, due, due)
                        r.notifiedFor != due -> scheduler.schedule(r, now + CATCH_UP_DELAY_MS, due)
                    }
                    armed += r.id
                }
            }
        }
    }

    private companion object {
        const val CATCH_UP_DELAY_MS = 3_000L
    }
}
