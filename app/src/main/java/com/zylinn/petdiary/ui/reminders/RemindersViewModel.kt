package com.zylinn.petdiary.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.reminder.Completion
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.data.reminder.ReminderRow
import com.zylinn.petdiary.domain.ReminderSchedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RemindersState(
    val today: LocalDate,
    /** Past their time, oldest first (P4-05). */
    val overdue: List<ReminderRow> = emptyList(),
    val todayRows: List<ReminderRow> = emptyList(),
    val later: List<ReminderRow> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty get() = overdue.isEmpty() && todayRows.isEmpty() && later.isEmpty()
}

/** A reminder plus whether its time has already passed, decided with the app clock. */
data class ReminderUi(val row: ReminderRow, val overdue: Boolean)

fun List<ReminderRow>.withOverdue(clock: Clock): List<ReminderUi> = map {
    ReminderUi(it, ReminderSchedule.isOverdue(it.reminder.dueEpochDay, it.reminder.minuteOfDay, clock.millis(), clock.zone))
}

/** Splits open reminders into overdue / today / later, using [clock] for "now". */
fun splitReminders(rows: List<ReminderRow>, clock: Clock): RemindersState {
    val today = LocalDate.now(clock)
    val now = clock.millis()
    val (late, rest) = rows.partition { ReminderSchedule.isOverdue(it.reminder.dueEpochDay, it.reminder.minuteOfDay, now, clock.zone) }
    val (todayRows, later) = rest.partition { it.reminder.dueEpochDay <= today.toEpochDay() }
    return RemindersState(today, late, todayRows, later, loaded = true)
}

class RemindersViewModel(
    private val reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {

    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val state: StateFlow<RemindersState> = combine(reminders.observeOpen(), minuteTick) { rows, _ -> splitReminders(rows, clock) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersState(LocalDate.now(clock)))

    private val _justCompleted = MutableStateFlow<Completion?>(null)
    /** The last tick-off, for the 5-second "復原" snackbar (P4-12). */
    val justCompleted: StateFlow<Completion?> = _justCompleted

    fun complete(id: String) {
        viewModelScope.launch { _justCompleted.value = reminders.complete(id) }
    }

    fun undo(c: Completion) {
        viewModelScope.launch { reminders.undo(c) }
        _justCompleted.value = null
    }

    fun completionAcknowledged() {
        _justCompleted.value = null
    }
}
