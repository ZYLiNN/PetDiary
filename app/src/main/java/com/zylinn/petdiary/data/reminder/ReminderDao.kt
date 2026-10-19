package com.zylinn.petdiary.data.reminder

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val OPEN_ROWS = "SELECT r.*, p.name AS petName FROM reminders r JOIN pets p ON p.id = r.petId " +
    "WHERE r.deletedAt IS NULL AND r.completedAt IS NULL AND p.deletedAt IS NULL"

@Dao
interface ReminderDao {

    /** Every open reminder of every living pet, soonest first. */
    @Query(OPEN_ROWS + " ORDER BY r.dueEpochDay, r.minuteOfDay")
    fun observeOpen(): Flow<List<ReminderRow>>

    @Query(OPEN_ROWS + " AND r.petId = :petId AND r.dueEpochDay BETWEEN :from AND :to ORDER BY r.dueEpochDay, r.minuteOfDay")
    fun observeOpenForPet(petId: String, from: Long, to: Long): Flow<List<ReminderRow>>

    @Query("SELECT title FROM reminders WHERE type = 'CUSTOM' AND deletedAt IS NULL AND title IS NOT NULL GROUP BY title ORDER BY MAX(updatedAt) DESC LIMIT 8")
    fun observeCustomTitles(): Flow<List<String>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): Reminder?

    @Query("SELECT r.*, p.name AS petName FROM reminders r JOIN pets p ON p.id = r.petId WHERE r.id = :id")
    suspend fun getRow(id: String): ReminderRow?

    @Query("SELECT * FROM reminders WHERE sourceRecordId = :recordId")
    suspend fun getBySource(recordId: String): List<Reminder>

    @Upsert
    suspend fun upsert(reminder: Reminder)

    @Upsert
    suspend fun upsertAll(reminders: List<Reminder>)

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now WHERE sourceRecordId = :recordId AND deletedAt IS NULL")
    suspend fun softDeleteBySource(recordId: String, now: Long)

    @Query("UPDATE reminders SET deletedAt = NULL, updatedAt = :now WHERE sourceRecordId = :recordId AND completedAt IS NULL")
    suspend fun restoreBySource(recordId: String, now: Long)

    @Query("UPDATE reminders SET notifiedFor = :dueMillis WHERE id = :id")
    suspend fun markNotified(id: String, dueMillis: Long)

    @Query("DELETE FROM reminders WHERE sourceRecordId = :recordId")
    suspend fun deleteBySource(recordId: String)

    @Query("DELETE FROM reminders WHERE petId = :petId")
    suspend fun deleteForPet(petId: String)
}
