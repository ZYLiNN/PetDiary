package com.zylinn.petdiary.data.trash

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetDao
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.AttachmentDao
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordDao
import com.zylinn.petdiary.data.reminder.ReminderDao
import java.time.Clock

/**
 * Permanent deletion for trashed items.
 * - A trashed pet hides its records too; purging the pet removes the records and their attachments.
 * - A record trashed on its own is purged 7 days after its own deletion.
 * Reminders are removed together with their pet / source record.
 */
class TrashRepository(
    private val petDao: PetDao,
    private val recordDao: RecordDao,
    private val attachmentDao: AttachmentDao,
    private val photos: FileStore,
    private val attachmentFiles: FileStore,
    private val clock: Clock,
    private val reminderDao: ReminderDao? = null,
) {
    /** Removes everything that has been in the trash for 7 days or more. Returns the count purged. */
    suspend fun purgeExpired(): Int {
        val cutoff = TrashPolicy.cutoff(clock.millis())
        val pets = petDao.getTrashedBefore(cutoff)
        pets.forEach { purgePet(it) }
        val records = recordDao.getTrashedBefore(cutoff)
        records.forEach { purgeRecord(it) }
        return pets.size + records.size
    }

    /** "永久刪除" from the trash screen; only pets already in the trash can be removed. */
    suspend fun deletePermanently(petId: String) {
        val pet = petDao.getById(petId) ?: return
        if (pet.deletedAt != null) purgePet(pet)
    }

    /** "永久刪除" for a single trashed record. */
    suspend fun deleteRecordPermanently(recordId: String) {
        val record = recordDao.getById(recordId) ?: return
        if (record.deletedAt != null) purgeRecord(record)
    }

    private suspend fun purgePet(pet: Pet) {
        recordDao.getAllForPet(pet.id).forEach { purgeRecord(it) }
        reminderDao?.deleteForPet(pet.id)
        pet.photoFile?.let(photos::delete)
        petDao.deletePermanently(pet.id)
    }

    private suspend fun purgeRecord(record: Record) {
        attachmentDao.getForRecord(record.id).forEach { attachmentFiles.delete(it.fileName) }
        attachmentDao.deleteForRecord(record.id)
        reminderDao?.deleteBySource(record.id)
        recordDao.deletePermanently(record.id)
    }
}
