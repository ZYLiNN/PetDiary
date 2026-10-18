package com.zylinn.petdiary.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.trash.TrashPolicy
import com.zylinn.petdiary.data.trash.TrashRepository
import com.zylinn.petdiary.domain.RecordFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate

data class TrashItem(
    val id: String,
    val name: String,
    val photo: File?,
    val remainingDays: Int,
)

data class TrashRecordItem(
    val id: String,
    val petId: String,
    val type: RecordType,
    /** "雷米・10/15" */
    val subtitle: String,
    val remainingDays: Int,
    /** The record's pet is in the trash too; restoring the record brings the pet back (T-04a). */
    val petAlsoTrashed: Boolean,
)

data class TrashContent(val pets: List<TrashItem>, val records: List<TrashRecordItem>) {
    val isEmpty get() = pets.isEmpty() && records.isEmpty()
}

class TrashViewModel(
    private val pets: PetRepository,
    private val records: RecordRepository,
    private val trash: TrashRepository,
    photos: FileStore,
    private val clock: Clock,
) : ViewModel() {

    /** null while loading. */
    val content: StateFlow<TrashContent?> = combine(
        pets.activePets,
        pets.trashedPets,
        records.observeTrashed(),
    ) { active, trashedPets, trashedRecords ->
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val names = (active + trashedPets).associate { it.id to it.name }
        val trashedIds = trashedPets.map { it.id }.toSet()
        TrashContent(
            pets = trashedPets.map {
                TrashItem(it.id, it.name, it.photoFile?.let(photos::file), TrashPolicy.remainingDays(it.deletedAt ?: now, now))
            },
            records = trashedRecords.map { r ->
                TrashRecordItem(
                    id = r.id,
                    petId = r.petId,
                    type = r.type,
                    subtitle = "${names[r.petId].orEmpty()}・${RecordFormat.shortDate(LocalDate.ofEpochDay(r.dateEpochDay), today)}",
                    remainingDays = TrashPolicy.remainingDays(r.deletedAt ?: now, now),
                    petAlsoTrashed = r.petId in trashedIds,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: String) {
        viewModelScope.launch { pets.restore(id) }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch { trash.deletePermanently(id) }
    }

    /** Restores the record, and its pet too when the pet is also in the trash (T-04a). */
    fun restoreRecord(item: TrashRecordItem) {
        viewModelScope.launch {
            if (item.petAlsoTrashed) pets.restore(item.petId)
            records.restore(item.id)
        }
    }

    fun deleteRecordPermanently(id: String) {
        viewModelScope.launch { trash.deleteRecordPermanently(id) }
    }
}
