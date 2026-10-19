package com.zylinn.petdiary.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.SearchQuery
import com.zylinn.petdiary.domain.RecordFormat
import com.zylinn.petdiary.ui.detail.PetChoice
import com.zylinn.petdiary.ui.detail.RecordCard
import com.zylinn.petdiary.ui.detail.titleText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate

enum class DateRange(val label: String) { ALL("全部"), DAYS_30("近 30 天"), YEAR("近 1 年"), CUSTOM("自訂") }

data class SearchFilters(
    val text: String = "",
    val petIds: Set<String> = emptySet(),
    val types: Set<RecordType> = emptySet(),
    val range: DateRange = DateRange.ALL,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
)

data class SearchHit(val card: RecordCard, val petId: String, val petName: String, val day: LocalDate)

data class ResultGroup(val day: LocalDate, val header: String, val hits: List<SearchHit>)

sealed interface SearchResults {
    /** Nothing typed and no type chosen yet. */
    data object Idle : SearchResults
    data class Found(val groups: List<ResultGroup>, val count: Int) : SearchResults
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    initialPetId: String?,
    pets: PetRepository,
    private val records: RecordRepository,
    private val clock: Clock,
    private val authorNames: Flow<Map<String, String>> = flowOf(emptyMap()),
) : ViewModel() {

    private val _filters = MutableStateFlow(SearchFilters(petIds = setOfNotNull(initialPetId)))
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    val petChoices: StateFlow<List<PetChoice>> = pets.activePets
        .map { list -> list.map { PetChoice(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun today() = LocalDate.now(clock)

    private fun SearchFilters.toQuery(): SearchQuery {
        val (from, to) = when (range) {
            DateRange.ALL -> null to null
            DateRange.DAYS_30 -> today().minusDays(29) to today()
            DateRange.YEAR -> today().minusYears(1).plusDays(1) to today()
            DateRange.CUSTOM -> customFrom to customTo
        }
        return SearchQuery(text, petIds, types, from?.toEpochDay(), to?.toEpochDay())
    }

    val results: StateFlow<SearchResults> = _filters
        .debounce(250)
        .flatMapLatest { f ->
            val query = f.toQuery()
            if (query.isBlank) return@flatMapLatest flowOf(SearchResults.Idle)
            combine(records.search(query), pets.activePets, records.supplementNames, authorNames) { found, petList, supps, authors ->
                val names = petList.associate { it.id to it.name }
                val suppNames = supps
                val today = today()
                val groups = found.groupBy { it.dateEpochDay }.map { (epochDay, list) ->
                    val day = LocalDate.ofEpochDay(epochDay)
                    ResultGroup(
                        day = day,
                        header = (if (day.year == today.year) "" else "${day.year} 年 ") + "${day.titleText()}・${RecordFormat.daysAgo(day, today)}",
                        hits = list.map { r ->
                            SearchHit(
                                card = RecordCard(
                                    id = r.id,
                                    type = r.type,
                                    time = RecordFormat.time(r.minuteOfDay),
                                    summary = RecordFormat.summary(r, suppNames, today),
                                    note = r.note,
                                    attachmentCount = 0,
                                    author = r.authorUid?.let(authors::get),
                                ),
                                petId = r.petId,
                                petName = names[r.petId].orEmpty(),
                                day = day,
                            )
                        },
                    )
                }
                SearchResults.Found(groups, found.size)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults.Idle)

    fun setText(text: String) = _filters.update { it.copy(text = text.take(50)) }

    fun togglePet(id: String) = _filters.update { f ->
        f.copy(petIds = if (id in f.petIds) f.petIds - id else f.petIds + id)
    }

    fun toggleType(type: RecordType) = _filters.update { f ->
        f.copy(types = if (type in f.types) f.types - type else f.types + type)
    }

    fun setRange(range: DateRange) = _filters.update { it.copy(range = range) }

    fun setCustomRange(from: LocalDate?, to: LocalDate?) = _filters.update {
        val (a, b) = if (from != null && to != null && from.isAfter(to)) to to from else from to to
        it.copy(range = DateRange.CUSTOM, customFrom = a, customTo = b)
    }
}
