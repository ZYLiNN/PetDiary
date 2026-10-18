package com.zylinn.petdiary.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.PhotoStore
import com.zylinn.petdiary.domain.PetAge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

sealed interface PetHomeState {
    data object Loading : PetHomeState
    data class Ready(val pets: List<PetTile>) : PetHomeState
}

class PetHomeViewModel(
    repository: PetRepository,
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
}
