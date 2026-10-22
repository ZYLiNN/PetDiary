package com.zylinn.petdiary.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.record.Toy
import com.zylinn.petdiary.data.record.PurchaseCategory
import com.zylinn.petdiary.data.reminder.Reminder
import kotlinx.coroutines.flow.Flow

/** Everything the sync engine needs from Room: "what changed since", "what do I have locally", and writes. */
@Dao
interface SyncDao {

    // "Dirty" = never synced, or edited since the last sync (updatedAt differs from what we recorded).
    @Query("SELECT p.* FROM pets p LEFT JOIN sync_meta m ON m.tableName = 'pets' AND m.id = p.id WHERE m.id IS NULL OR m.localStamp != p.updatedAt")
    suspend fun dirtyPets(): List<Pet>

    @Query("SELECT r.* FROM records r LEFT JOIN sync_meta m ON m.tableName = 'records' AND m.id = r.id WHERE m.id IS NULL OR m.localStamp != r.updatedAt")
    suspend fun dirtyRecords(): List<Record>

    @Query("SELECT r.* FROM reminders r LEFT JOIN sync_meta m ON m.tableName = 'reminders' AND m.id = r.id WHERE m.id IS NULL OR m.localStamp != r.updatedAt")
    suspend fun dirtyReminders(): List<Reminder>

    @Query("SELECT s.* FROM supplements s LEFT JOIN sync_meta m ON m.tableName = 'supplements' AND m.id = s.id WHERE m.id IS NULL OR m.localStamp != s.updatedAt")
    suspend fun dirtySupplements(): List<Supplement>

    @Query("SELECT t.* FROM toys t LEFT JOIN sync_meta m ON m.tableName = 'toys' AND m.id = t.id WHERE m.id IS NULL OR m.localStamp != t.updatedAt")
    suspend fun dirtyToys(): List<Toy>

    @Query("SELECT c.* FROM purchase_categories c LEFT JOIN sync_meta m ON m.tableName = 'categories' AND m.id = c.id WHERE m.id IS NULL OR m.localStamp != c.updatedAt")
    suspend fun dirtyCategories(): List<PurchaseCategory>

    /** Re-emits whenever a table or the sync marks change; the engine then looks for dirty rows. */
    @Query("SELECT (SELECT COUNT(*) FROM pets) + (SELECT COUNT(*) FROM records) + (SELECT COUNT(*) FROM reminders) + (SELECT COUNT(*) FROM supplements) + (SELECT COUNT(*) FROM toys) + (SELECT COUNT(*) FROM purchase_categories) + (SELECT COALESCE(SUM(updatedAt), 0) FROM pets) + (SELECT COALESCE(SUM(updatedAt), 0) FROM records) + (SELECT COALESCE(SUM(updatedAt), 0) FROM reminders) + (SELECT COALESCE(SUM(updatedAt), 0) FROM supplements) + (SELECT COALESCE(SUM(updatedAt), 0) FROM toys) + (SELECT COALESCE(SUM(updatedAt), 0) FROM purchase_categories) + (SELECT COUNT(*) FROM sync_meta)")
    fun changeSignal(): Flow<Long>

    @Query("SELECT * FROM sync_meta WHERE tableName = :table AND id = :id")
    suspend fun meta(table: String, id: String): SyncMeta?

    @Upsert suspend fun upsertMeta(meta: SyncMeta)

    @Upsert suspend fun upsertMetas(metas: List<SyncMeta>)

    @Query("SELECT updatedAt FROM pets WHERE id = :id")
    suspend fun petUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM records WHERE id = :id")
    suspend fun recordUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM reminders WHERE id = :id")
    suspend fun reminderUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM supplements WHERE id = :id")
    suspend fun supplementUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM toys WHERE id = :id")
    suspend fun toyUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM purchase_categories WHERE id = :id")
    suspend fun categoryUpdatedAt(id: String): Long?

    /** This device's own "already announced" mark, kept when a reminder is overwritten by a cloud copy. */
    @Query("SELECT notifiedFor FROM reminders WHERE id = :id")
    suspend fun reminderNotifiedFor(id: String): Long?

    @Query("SELECT * FROM attachments WHERE recordId IN (:recordIds)")
    suspend fun attachmentsFor(recordIds: List<String>): List<Attachment>

    @Query("DELETE FROM attachments WHERE recordId = :recordId")
    suspend fun deleteAttachmentsOf(recordId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>)

    /** Forget what was synced with the old group, so everything counts as unsent for the new one. */
    @Query("DELETE FROM sync_meta")
    suspend fun clearMeta()

    @Query("SELECT photoFile FROM pets WHERE photoFile IS NOT NULL")
    suspend fun petPhotoFiles(): List<String>

    @Query("SELECT fileName FROM attachments")
    suspend fun attachmentFiles(): List<String>

    @Upsert suspend fun upsertPet(pet: Pet)
    @Upsert suspend fun upsertRecord(record: Record)
    @Upsert suspend fun upsertReminder(reminder: Reminder)
    @Upsert suspend fun upsertSupplement(supplement: Supplement)
    @Upsert suspend fun upsertToy(toy: Toy)
    @Upsert suspend fun upsertCategory(category: PurchaseCategory)
}
