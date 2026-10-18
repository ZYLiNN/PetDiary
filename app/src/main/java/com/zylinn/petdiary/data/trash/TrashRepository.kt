package com.zylinn.petdiary.data.trash

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetDao
import com.zylinn.petdiary.data.photo.PhotoStore
import java.time.Clock

/**
 * Permanent deletion for trashed items. Records, attachments and reminders join the
 * cascade in P2 / P4; for now a pet owns only its avatar photo.
 */
class TrashRepository(
    private val petDao: PetDao,
    private val photos: PhotoStore,
    private val clock: Clock,
) {
    /** Removes everything that has been in the trash for 7 days or more. Returns the count purged. */
    suspend fun purgeExpired(): Int {
        val expired = petDao.getTrashedBefore(TrashPolicy.cutoff(clock.millis()))
        expired.forEach { purge(it) }
        return expired.size
    }

    /** "永久刪除" from the trash screen; only pets already in the trash can be removed. */
    suspend fun deletePermanently(petId: String) {
        val pet = petDao.getById(petId) ?: return
        if (pet.deletedAt != null) purge(pet)
    }

    private suspend fun purge(pet: Pet) {
        pet.photoFile?.let(photos::delete)
        petDao.deletePermanently(pet.id)
    }
}
