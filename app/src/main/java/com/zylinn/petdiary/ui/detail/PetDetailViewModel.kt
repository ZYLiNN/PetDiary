package com.zylinn.petdiary.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.domain.PetAge
import com.zylinn.petdiary.domain.RecordFormat
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
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class PetHeader(val id: String, val name: String, val subtitle: String, val photo: File?)

data class RecordCard(
    val id: String,
    val type: RecordType,
    val time: String,
    val summary: String,
    val note: String?,
    val attachmentCount: Int,
)

data class LastChip(val type: RecordType, val text: String)

data class PetChoice(val id: String, val name: String)

/** The most recent feeding, offered as "餵食・同上次" (S2). */
data class QuickFeeding(val source: Record, val summary: String)

data class CalendarState(
    val today: LocalDate,
    val selected: LocalDate,
    val expanded: Boolean,
    val visibleMonth: YearMonth,
    val daysWithRecords: Set<LocalDate>,
) {
    /** Sunday-first week containing the selected day. */
    val week: List<LocalDate>
        get() {
            val start = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            return (0L until 7L).map(start::plusDays)
        }
}

/** Types shown in the "上次紀錄" strip, in this order. Feeding is daily, so it is left out. */
private val lastChipOrder = listOf(
    RecordType.DEWORMING, RecordType.VACCINE, RecordType.VET, RecordType.MEDICINE,
    RecordType.GROOMING, RecordType.WEIGHT, RecordType.ABNORMAL,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PetDetailViewModel(
    val petId: String,
    private val pets: PetRepository,
    private val records: RecordRepository,
    photos: FileStore,
    private val clock: Clock,
    initialDay: LocalDate? = null,
) : ViewModel() {

    private fun today() = LocalDate.now(clock)

    private val startDay = initialDay?.takeIf { !it.isAfter(today()) } ?: today()
    private val selected = MutableStateFlow(startDay)
    private val expanded = MutableStateFlow(false)
    private val visibleMonth = MutableStateFlow(YearMonth.from(startDay))

    val header: StateFlow<PetHeader?> = combine(
        pets.observe(petId),
        records.observeLatest(petId, RecordType.WEIGHT),
    ) { pet: Pet?, weight: Record? ->
        pet?.let {
            val age = PetAge.label(LocalDate.ofEpochDay(it.birthEpochDay), it.birthEstimated, today())
            val kg = weight?.weightKg?.let { w -> "・${RecordFormat.number(w)} kg" }.orEmpty()
            PetHeader(it.id, it.name, age + kg, it.photoFile?.let(photos::file))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val calendar: StateFlow<CalendarState> = combine(selected, expanded, visibleMonth) { s, e, m -> Triple(s, e, m) }
        .flatMapLatest { (sel, exp, month) ->
            // Dots for both the selected week and the visible month (they can differ while browsing).
            val weekStart = sel.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val from = minOf(weekStart, month.atDay(1))
            val to = maxOf(weekStart.plusDays(6), month.atEndOfMonth())
            records.observeDaysWithRecords(petId, from.toEpochDay(), to.toEpochDay()).map { days ->
                CalendarState(today(), sel, exp, month, days.map(LocalDate::ofEpochDay).toSet())
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarState(today(), startDay, false, YearMonth.from(startDay), emptySet()),
        )

    /** null while loading. */
    val cards: StateFlow<List<RecordCard>?> = selected
        .flatMapLatest { day -> records.observeDay(petId, day.toEpochDay()) }
        .flatMapLatest { list ->
            val counts = if (list.isEmpty()) flowOf(emptyList()) else records.observeAttachmentCounts(list.map { it.id })
            combine(counts, records.supplementsList) { c, supps ->
                val countMap = c.associate { it.recordId to it.count }
                val names = supps.associate { it.id to it.name }
                list.map { r ->
                    RecordCard(
                        id = r.id,
                        type = r.type,
                        time = RecordFormat.time(r.minuteOfDay),
                        summary = RecordFormat.summary(r, names, today()),
                        note = r.note,
                        attachmentCount = countMap[r.id] ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastChips: StateFlow<List<LastChip>> = records.observeLastOfEachType(petId)
        .map { list ->
            val byType = list.associate { it.type to LocalDate.ofEpochDay(it.lastDay) }
            lastChipOrder.mapNotNull { t -> byType[t]?.let { LastChip(t, RecordFormat.lastChip(t, it, today())) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All active pets for the multi-select chips (S3). */
    val petChoices: StateFlow<List<PetChoice>> = pets.activePets
        .map { list -> list.map { PetChoice(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickFeeding: StateFlow<QuickFeeding?> = combine(
        records.observeLatest(petId, RecordType.FEEDING),
        records.supplementsList,
    ) { latest, supps ->
        latest?.let { QuickFeeding(it, RecordFormat.summary(it, supps.associate { s -> s.id to s.name }, today())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _justAdded = MutableStateFlow<List<String>?>(null)
    /** Ids created by the last one-tap save, for the "復原" snackbar. */
    val justAdded: StateFlow<List<String>?> = _justAdded

    /**
     * One-tap feeding: same content as the last feeding, on the selected day at the current time (S2).
     */
    fun quickFeed(petIds: List<String>) {
        val source = quickFeeding.value?.source ?: return
        viewModelScope.launch {
            val now = LocalTime.now(clock)
            val input = RecordInput(
                type = RecordType.FEEDING,
                dateEpochDay = selected.value.toEpochDay(),
                minuteOfDay = now.hour * 60 + now.minute,
                note = source.note,
                portions = source.portions,
                drankWater = source.drankWater,
                supplementIds = source.supplementIdList,
            )
            _justAdded.value = records.addForPets(petIds, input)
        }
    }

    fun undoQuickFeed(ids: List<String>) {
        viewModelScope.launch { ids.forEach { records.moveToTrash(it) } }
        _justAdded.value = null
    }

    private val _justDeleted = MutableStateFlow<RecordCard?>(null)
    /** A record just swiped away, for the 5-second "復原" snackbar (S6 / S12). */
    val justDeleted: StateFlow<RecordCard?> = _justDeleted

    fun deleteRecord(card: RecordCard) {
        viewModelScope.launch {
            records.moveToTrash(card.id)
            _justDeleted.value = card
        }
    }

    fun undoDelete(card: RecordCard) {
        viewModelScope.launch { records.restore(card.id) }
        _justDeleted.value = null
    }

    fun deleteAcknowledged() {
        _justDeleted.value = null
    }

    fun quickFeedAcknowledged() {
        _justAdded.value = null
    }

    fun selectedDay(): LocalDate = selected.value

    /** Picks a day (future days are not selectable); collapses the month view (S5). */
    fun select(day: LocalDate) {
        if (day.isAfter(today())) return
        selected.value = day
        visibleMonth.value = YearMonth.from(day)
        expanded.value = false
    }

    fun shiftWeek(weeks: Long) {
        val target = selected.value.plusWeeks(weeks)
        select(if (target.isAfter(today())) today() else target)
    }

    fun toggleExpanded() = expanded.update { !it }

    fun shiftMonth(months: Long) = visibleMonth.update { m ->
        val next = m.plusMonths(months)
        if (next.isAfter(YearMonth.from(today()))) m else next
    }
}
