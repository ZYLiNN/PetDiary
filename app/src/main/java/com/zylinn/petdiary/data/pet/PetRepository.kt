package com.zylinn.petdiary.data.pet

import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

/** Input for creating or editing a pet; timestamps and id are filled in by the repository. */
data class PetInput(
    val name: String,
    val species: Species,
    val customSpecies: String? = null,
    val birthEpochDay: Long,
    val birthEstimated: Boolean = false,
    val sex: Sex = Sex.UNKNOWN,
    val neutered: Boolean? = null,
    val breed: String? = null,
    val chipNumber: String? = null,
    val note: String? = null,
    val photoFile: String? = null,
)

class PetRepository(
    private val dao: PetDao,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val activePets: Flow<List<Pet>> = dao.observeActive()
    val trashedPets: Flow<List<Pet>> = dao.observeTrashed()

    suspend fun get(id: String): Pet? = dao.getById(id)

    suspend fun add(input: PetInput): Pet {
        val now = clock.millis()
        val pet = input.toPet(id = newId(), createdAt = now, updatedAt = now)
        dao.upsert(pet)
        return pet
    }

    suspend fun update(id: String, input: PetInput): Pet? {
        val existing = dao.getById(id) ?: return null
        val pet = input.toPet(id = id, createdAt = existing.createdAt, updatedAt = clock.millis())
            .copy(deletedAt = existing.deletedAt)
        dao.upsert(pet)
        return pet
    }

    suspend fun moveToTrash(id: String) = dao.softDelete(id, clock.millis())

    suspend fun restore(id: String) = dao.restore(id, clock.millis())

    private fun PetInput.toPet(id: String, createdAt: Long, updatedAt: Long) = Pet(
        id = id,
        name = name.trim(),
        species = species,
        customSpecies = customSpecies?.trim()?.ifEmpty { null }.takeIf { species == Species.OTHER },
        birthEpochDay = birthEpochDay,
        birthEstimated = birthEstimated,
        sex = sex,
        neutered = neutered,
        breed = breed?.trim()?.ifEmpty { null },
        chipNumber = chipNumber?.trim()?.ifEmpty { null },
        note = note?.trim()?.ifEmpty { null },
        photoFile = photoFile,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
