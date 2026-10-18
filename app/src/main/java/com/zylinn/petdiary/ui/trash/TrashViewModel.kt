package com.zylinn.petdiary.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.PhotoStore
import com.zylinn.petdiary.data.trash.TrashPolicy
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock

data class TrashItem(
    val id: String,
    val name: String,
    val photo: File?,
    val remainingDays: Int,
)

class TrashViewModel(
    private val pets: PetRepository,
    private val trash: TrashRepository,
    photos: PhotoStore,
    private val clock: Clock,
) : ViewModel() {

    /** null while loading. */
    val items: StateFlow<List<TrashItem>?> = pets.trashedPets
        .map { list ->
            val now = clock.millis()
            list.map {
                TrashItem(
                    id = it.id,
                    name = it.name,
                    photo = it.photoFile?.let(photos::file),
                    remainingDays = TrashPolicy.remainingDays(it.deletedAt ?: now, now),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: String) {
        viewModelScope.launch { pets.restore(id) }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch { trash.deletePermanently(id) }
    }
}
