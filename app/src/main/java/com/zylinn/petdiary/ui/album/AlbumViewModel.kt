package com.zylinn.petdiary.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.AlbumEntry
import com.zylinn.petdiary.data.record.RecordRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

enum class AlbumTab { PHOTOS, FILES }

data class AlbumPet(val id: String, val name: String, val color: PetColor)

/** One tile / row; [file] may not exist yet while a family member's upload is still downloading. */
data class AlbumItem(val entry: AlbumEntry, val file: File, val ready: Boolean) {
    val day: LocalDate get() = LocalDate.ofEpochDay(entry.dateEpochDay)
}

data class AlbumUi(
    val tab: AlbumTab = AlbumTab.PHOTOS,
    val petId: String? = null,
    val pets: List<AlbumPet> = emptyList(),
    val photoCount: Int = 0,
    val fileCount: Int = 0,
    /** Shown items grouped by month, newest first. */
    val months: List<Pair<YearMonth, List<AlbumItem>>> = emptyList(),
    val loading: Boolean = true,
)

/** The album tab (P13, D80): every photo and PDF attached to any record, per pet or for all of them. */
class AlbumViewModel(
    pets: PetRepository,
    records: RecordRepository,
    private val files: FileStore,
) : ViewModel() {

    private val tab = MutableStateFlow(AlbumTab.PHOTOS)
    private val petFilter = MutableStateFlow<String?>(null)

    /** Files synced from another phone arrive in the background; look again every few seconds. */
    private val tick = flow { while (true) { emit(Unit); delay(5_000) } }

    val ui: StateFlow<AlbumUi> = combine(records.observeAlbum(), pets.activePets, tab, petFilter, tick) { all, petList, t, pet, _ ->
        val colors = PetColor.effective(petList)
        val chips = petList.map { AlbumPet(it.id, it.name, colors.getValue(it.id)) }
        val chosen = pet?.takeIf { id -> petList.any { it.id == id } }
        val mine = all.filter { chosen == null || it.petId == chosen }
        val shown = mine.filter { if (t == AlbumTab.PHOTOS) it.isImage else !it.isImage }
            .map { e -> files.file(e.fileName).let { AlbumItem(e, it, it.exists()) } }
        AlbumUi(
            tab = t, petId = chosen, pets = chips,
            photoCount = mine.count { it.isImage }, fileCount = mine.count { !it.isImage },
            months = shown.groupBy { YearMonth.from(it.day) }.toList(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUi())

    fun showTab(t: AlbumTab) { tab.value = t }

    /** null = every pet. */
    fun showPet(id: String?) { petFilter.value = id }
}
