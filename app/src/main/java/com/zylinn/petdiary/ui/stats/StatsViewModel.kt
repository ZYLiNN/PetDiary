package com.zylinn.petdiary.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.domain.RangeKind
import com.zylinn.petdiary.domain.StatMetric
import com.zylinn.petdiary.domain.StatsCalculator
import com.zylinn.petdiary.domain.StatsRanges
import com.zylinn.petdiary.domain.StatsResult
import com.zylinn.petdiary.ui.detail.PetChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate

/** P5-01: defaults are every pet, this week, feeding. An empty pet set means "all pets". */
data class StatsFilters(
    val petIds: Set<String> = emptySet(),
    val kind: RangeKind = RangeKind.WEEK,
    val offset: Int = 0,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val metric: StatMetric = StatMetric.FEEDING,
    /** Feeding: one kind only, or null for all (D76). */
    val foodKind: FoodKind? = null,
)

/** A pet shown in the chart; [colorIndex] is its position among all pets so its color never changes when filtering. */
data class StatsPet(val id: String, val name: String, val colorIndex: Int)

data class StatsUi(val result: StatsResult, val pets: List<StatsPet>, val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val pets: PetRepository,
    private val records: RecordRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _filters = MutableStateFlow(StatsFilters())
    val filters: StateFlow<StatsFilters> = _filters.asStateFlow()

    val petChoices: StateFlow<List<PetChoice>> = pets.activePets
        .map { list -> list.map { PetChoice(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun today(): LocalDate = LocalDate.now(clock)

    /** null until the first result (or when there are no pets at all). */
    val ui: StateFlow<StatsUi?> = combine(_filters, pets.activePets) { f, all -> f to all }
        .flatMapLatest { (f, all) ->
            val shown = all.withIndex().filter { f.petIds.isEmpty() || it.value.id in f.petIds }
                .ifEmpty { all.withIndex().toList() }
                .map { StatsPet(it.value.id, it.value.name, it.index) }
            val today = today()
            val span = StatsRanges.span(f.kind, f.offset, today, f.customFrom, f.customTo)
            records.observeForStats(shown.map { it.id }, f.metric.types, span.from.toEpochDay(), span.to.toEpochDay()).map { recs ->
                StatsUi(
                    StatsCalculator.compute(f.metric, span, shown.map { it.id to it.name }, recs, today, f.foodKind),
                    shown,
                    StatsRanges.title(f.kind, span, today),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tapping a pet narrows to it; tapping the last selected pet goes back to all pets. */
    fun togglePet(id: String, allIds: List<String>) = _filters.update { f ->
        val current = if (f.petIds.isEmpty()) allIds.toSet() else f.petIds
        val next = if (id in current) current - id else current + id
        f.copy(petIds = if (next.isEmpty() || next.size == allIds.size) emptySet() else next)
    }

    fun setKind(kind: RangeKind) = _filters.update {
        it.copy(kind = kind, offset = 0, customFrom = it.customFrom ?: today().minusDays(29), customTo = it.customTo ?: today())
    }

    fun shift(delta: Int) = _filters.update { it.copy(offset = it.offset + delta) }

    fun setMetric(metric: StatMetric) = _filters.update { it.copy(metric = metric) }

    fun setFoodKind(kind: FoodKind?) = _filters.update { it.copy(foodKind = kind) }

    /** P5-08: the start can never be after the end; picking a start past the end moves the end along. */
    fun setCustomRange(from: LocalDate, to: LocalDate) = _filters.update {
        it.copy(kind = RangeKind.CUSTOM, customFrom = minOf(from, to), customTo = maxOf(from, to))
    }

    fun setCustomFrom(from: LocalDate) = _filters.update { f ->
        val to = f.customTo?.takeIf { !from.isAfter(it) } ?: from
        f.copy(kind = RangeKind.CUSTOM, customFrom = from, customTo = to)
    }

    fun setCustomTo(to: LocalDate) = _filters.update { f ->
        val from = f.customFrom?.takeIf { !it.isAfter(to) } ?: to
        f.copy(kind = RangeKind.CUSTOM, customFrom = from, customTo = to)
    }
}
