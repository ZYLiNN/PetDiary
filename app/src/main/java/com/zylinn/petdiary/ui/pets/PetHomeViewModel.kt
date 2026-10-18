package com.zylinn.petdiary.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.PhotoStore
import com.zylinn.petdiary.domain.PetAge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate

data class PetTile(
    val id: String,
    val name: String,
    val species: Species,
    val ageLabel: String,
    val photo: File?,
)

/** A pet that was just moved to the trash; shown in the 5-second "復原" snackbar. */
data class DeletedPet(val id: String, val name: String)

sealed interface PetHomeState {
    data object Loading : PetHomeState
    data class Ready(val pets: List<PetTile>) : PetHomeState
}

class PetHomeViewModel(
    private val repository: PetRepository,
    photos: PhotoStore,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<PetHomeState> = repository.activePets
        .map { pets ->
            val today = LocalDate.now(clock)
            PetHomeState.Ready(
                pets.map { pet ->
                    PetTile(
                        id = pet.id,
                        name = pet.name,
                        species = pet.species,
                        ageLabel = PetAge.label(LocalDate.ofEpochDay(pet.birthEpochDay), pet.birthEstimated, today),
                        photo = pet.photoFile?.let(photos::file),
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetHomeState.Loading)

    private val _justDeleted = MutableStateFlow<DeletedPet?>(null)
    val justDeleted: StateFlow<DeletedPet?> = _justDeleted.asStateFlow()

    /** Moves the pet to the trash immediately; [undo] within the snackbar window restores it. */
    fun delete(id: String, name: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
            _justDeleted.value = DeletedPet(id, name)
        }
    }

    fun undo(deleted: DeletedPet) {
        viewModelScope.launch { repository.restore(deleted.id) }
        _justDeleted.value = null
    }

    /** Snackbar closed without undo: the pet simply stays in the trash. */
    fun undoExpired() {
        _justDeleted.value = null
    }
}
