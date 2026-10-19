package com.zylinn.petdiary.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.reminder.Reminder
import kotlinx.coroutines.flow.Flow

/** Everything the sync engine needs from Room: "what changed since", "what do I have locally", and writes. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM pets WHERE updatedAt > :t ORDER BY updatedAt")
    suspend fun petsAfter(t: Long): List<Pet>

    @Query("SELECT * FROM records WHERE updatedAt > :t ORDER BY updatedAt")
    suspend fun recordsAfter(t: Long): List<Record>

    @Query("SELECT * FROM reminders WHERE updatedAt > :t ORDER BY updatedAt")
    suspend fun remindersAfter(t: Long): List<Reminder>

    @Query("SELECT * FROM supplements WHERE updatedAt > :t ORDER BY updatedAt")
    suspend fun supplementsAfter(t: Long): List<Supplement>

    /** Emit whenever a table changes; the value is the newest updatedAt (a cheap "something happened" signal). */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM pets")
    fun petsStamp(): Flow<Long>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM records")
    fun recordsStamp(): Flow<Long>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM reminders")
    fun remindersStamp(): Flow<Long>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM supplements")
    fun supplementsStamp(): Flow<Long>

    @Query("SELECT updatedAt FROM pets WHERE id = :id")
    suspend fun petUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM records WHERE id = :id")
    suspend fun recordUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM reminders WHERE id = :id")
    suspend fun reminderUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM supplements WHERE id = :id")
    suspend fun supplementUpdatedAt(id: String): Long?

    /** This device's own "already announced" mark, kept when a reminder is overwritten by a cloud copy. */
    @Query("SELECT notifiedFor FROM reminders WHERE id = :id")
    suspend fun reminderNotifiedFor(id: String): Long?

    @Upsert suspend fun upsertPet(pet: Pet)
    @Upsert suspend fun upsertRecord(record: Record)
    @Upsert suspend fun upsertReminder(reminder: Reminder)
    @Upsert suspend fun upsertSupplement(supplement: Supplement)
}
