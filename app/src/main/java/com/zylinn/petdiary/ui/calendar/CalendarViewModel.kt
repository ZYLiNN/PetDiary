package com.zylinn.petdiary.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.record.FAMILY_PET_ID
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.domain.CalendarItem
import com.zylinn.petdiary.domain.CalendarModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** One pet in the filter row; [visible] false = hidden from the calendar. */
data class CalendarPet(val id: String, val name: String, val short: String, val color: PetColor, val visible: Boolean)

data class CalendarUi(
    val month: YearMonth,
    val today: LocalDate,
    val selected: LocalDate,
    val days: List<LocalDate>,
    val pets: List<CalendarPet>,
    /** Only items of visible pets. */
    val items: Map<LocalDate, List<CalendarItem>>,
) {
    val selectedItems get() = items[selected].orEmpty()
}

/** The calendar tab (P10): every pet's records and open reminders for one month. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    pets: PetRepository,
    private val records: RecordRepository,
    private val reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {

    private fun today() = LocalDate.now(clock)

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val selected = MutableStateFlow(today())
    private val hidden = MutableStateFlow(emptySet<String>())

    private val monthData = month.flatMapLatest { m ->
        val days = CalendarModel.gridDays(m)
        val from = days.first().toEpochDay()
        val to = days.last().toEpochDay()
        combine(records.observeRange(from, to), reminders.observeOpenInRange(from, to), records.itemNames) { recs, rems, names ->
            CalendarModel.items(recs, rems.map { it.reminder }, names, today())
        }
    }

    val ui: StateFlow<CalendarUi> = combine(month, selected, hidden, pets.activePets, monthData) { m, sel, hide, petList, all ->
        val colors = PetColor.effective(petList)
        val shorts = CalendarModel.shortNames(petList.associate { it.id to it.name })
        val chips = petList.map { p -> CalendarPet(p.id, p.name, shorts.getValue(p.id), colors.getValue(p.id), p.id !in hide) }
        // Shared purchases belong to no single pet, so they show only while every pet is shown (D84).
        val visible = chips.filter { it.visible }.map { it.id }.toSet() + if (hide.isEmpty()) setOf(FAMILY_PET_ID) else emptySet()
        CalendarUi(m, today(), sel, CalendarModel.gridDays(m), chips, all.filter { it.petId in visible }.groupBy { it.day })
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        CalendarUi(month.value, today(), selected.value, CalendarModel.gridDays(month.value), emptyList(), emptyMap()),
    )

    fun previousMonth() = showMonth(month.value.minusMonths(1))
    fun nextMonth() = showMonth(month.value.plusMonths(1))
    fun goToday() { month.value = YearMonth.from(today()); selected.value = today() }

    private fun showMonth(m: YearMonth) {
        month.value = m
        // Keep the selection inside the month shown: today when it is that month, otherwise the 1st.
        selected.value = if (YearMonth.from(today()) == m) today() else m.atDay(1)
    }

    /** Tapping a greyed-out day of the neighbouring month jumps to that month. */
    fun select(day: LocalDate) {
        if (YearMonth.from(day) != month.value) month.value = YearMonth.from(day)
        selected.value = day
    }

    fun togglePet(id: String) = hidden.update { if (id in it) it - id else it + id }

    fun showAllPets() { hidden.value = emptySet() }
}
