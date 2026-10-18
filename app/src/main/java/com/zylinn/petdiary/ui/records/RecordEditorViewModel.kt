package com.zylinn.petdiary.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

data class RecordEditorState(
    val isEdit: Boolean,
    val loading: Boolean,
    val petNames: String = "",
    val form: RecordForm,
    val initialForm: RecordForm = form,
    val saving: Boolean = false,
) {
    val dirty get() = form != initialForm
}

/**
 * New record(s) for [petIds] (one independent record per pet), or edit [recordId].
 * Date defaults to the day chosen on the detail page; time defaults to now (S1 / P2-05b).
 */
class RecordEditorViewModel(
    private val petIds: List<String>,
    type: RecordType,
    date: LocalDate,
    private val recordId: String?,
    pets: PetRepository,
    private val records: RecordRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecordEditorState(
            isEdit = recordId != null,
            loading = true,
            form = RecordForm(type = type, date = date, minuteOfDay = nowMinute()),
        ),
    )
    val state: StateFlow<RecordEditorState> = _state.asStateFlow()

    val supplements: StateFlow<List<Supplement>> =
        records.supplementsList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val names = petIds.mapNotNull { pets.get(it)?.name }.joinToString("、")
            val existing = recordId?.let { records.get(it) }
            _state.update { s ->
                if (existing == null) s.copy(loading = false, petNames = names)
                else RecordForm.from(existing).let { f ->
                    s.copy(loading = false, petNames = names, form = f, initialForm = f)
                }
            }
        }
    }

    private fun nowMinute() = LocalTime.now(clock).let { it.hour * 60 + it.minute }

    fun today(): LocalDate = LocalDate.now(clock)

    fun edit(transform: (RecordForm) -> RecordForm) = _state.update { it.copy(form = transform(it.form)) }

    /** S7: create a supplement right from the feeding form and select it. */
    fun addSupplement(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val s = records.addSupplement(name.take(TEXT_MAX))
            edit { it.copy(supplementIds = it.supplementIds + s.id) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving || !current.form.canSave(today())) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val input = current.form.toInput(today())
            if (recordId == null) records.addForPets(petIds, input) else records.update(recordId, input)
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }
}
