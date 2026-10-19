package com.zylinn.petdiary.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.ui.detail.PetChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class ReminderEditorState(
    val form: ReminderForm,
    val pets: List<PetChoice> = emptyList(),
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val dirty: Boolean = false,
)

class ReminderEditorViewModel(
    private val reminderId: String?,
    initialPetId: String?,
    initialDay: LocalDate?,
    private val pets: PetRepository,
    private val reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {

    fun today(): LocalDate = LocalDate.now(clock)

    private val _state = MutableStateFlow(ReminderEditorState(defaultForm(initialPetId, initialDay), isEdit = reminderId != null))
    val state: StateFlow<ReminderEditorState> = _state.asStateFlow()

    /** New reminders start at the next full hour today (or 09:00 tomorrow late at night). */
    private fun defaultForm(petId: String?, day: LocalDate?): ReminderForm {
        val now = LocalDateTime.now(clock)
        val nextHour = now.hour + 1
        val (date, minute) = when {
            day != null -> day to 9 * 60
            nextHour <= 22 -> now.toLocalDate() to nextHour * 60
            else -> now.toLocalDate().plusDays(1) to 9 * 60
        }
        return ReminderForm(petId = petId, date = date, minuteOfDay = minute)
    }

    init {
        viewModelScope.launch {
            val choices = pets.activePets.first().map { PetChoice(it.id, it.name) }
            val existing = reminderId?.let { reminders.get(it) }
            _state.update { s ->
                val form = existing?.let(ReminderForm::from) ?: s.form.copy(petId = s.form.petId ?: choices.firstOrNull()?.id)
                s.copy(form = form, pets = choices, loading = false)
            }
        }
    }

    fun edit(transform: (ReminderForm) -> ReminderForm) = _state.update { it.copy(form = transform(it.form), dirty = true) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.saving || s.form.problem(today(), s.isEdit) != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (reminderId == null) reminders.add(s.form.toInput()) else reminders.update(reminderId, s.form.toInput())
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = reminderId ?: return
        viewModelScope.launch {
            reminders.delete(id)
            onDeleted()
        }
    }
}
