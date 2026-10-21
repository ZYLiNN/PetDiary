package com.zylinn.petdiary.data.record

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.RawQuery
import androidx.room.Query
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.zylinn.petdiary.data.pet.Pet
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE petId = :petId AND dateEpochDay = :day AND deletedAt IS NULL ORDER BY minuteOfDay, createdAt")
    fun observeDay(petId: String, day: Long): Flow<List<Record>>

    @Query("SELECT DISTINCT dateEpochDay FROM records WHERE petId = :petId AND deletedAt IS NULL AND dateEpochDay BETWEEN :from AND :to")
    fun observeDaysWithRecords(petId: String, from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT type, MAX(dateEpochDay) AS lastDay FROM records WHERE petId = :petId AND deletedAt IS NULL GROUP BY type")
    fun observeLastOfEachType(petId: String): Flow<List<LastOfType>>

    @Query("SELECT * FROM records WHERE petId = :petId AND type = :type AND deletedAt IS NULL ORDER BY dateEpochDay DESC, minuteOfDay DESC, createdAt DESC LIMIT 1")
    fun observeLatest(petId: String, type: RecordType): Flow<Record?>

    @Query("SELECT * FROM records WHERE petId = :petId AND type = :type AND deletedAt IS NULL ORDER BY dateEpochDay DESC, minuteOfDay DESC, createdAt DESC LIMIT 1")
    suspend fun latest(petId: String, type: RecordType): Record?

    /** Every living record of every living pet in the range, for the calendar tab. */
    @Query("SELECT r.* FROM records r JOIN pets p ON p.id = r.petId WHERE r.deletedAt IS NULL AND p.deletedAt IS NULL AND r.dateEpochDay BETWEEN :from AND :to ORDER BY r.dateEpochDay, r.minuteOfDay")
    fun observeRange(from: Long, to: Long): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: String): Record?

    @Upsert
    suspend fun upsert(record: Record)

    @Upsert
    suspend fun upsertAll(records: List<Record>)

    @Query("UPDATE records SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE records SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("SELECT * FROM records WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<Record>

    @Query("SELECT * FROM records WHERE petId = :petId")
    suspend fun getAllForPet(petId: String): List<Record>

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /** Stats page (P5): records of living pets, filtered by pets / types / date range. */
    @Query(
        "SELECT r.* FROM records r JOIN pets p ON p.id = r.petId " +
            "WHERE r.deletedAt IS NULL AND p.deletedAt IS NULL AND r.petId IN (:petIds) AND r.type IN (:types) " +
            "AND r.dateEpochDay BETWEEN :from AND :to ORDER BY r.dateEpochDay, r.minuteOfDay",
    )
    fun observeForStats(petIds: List<String>, types: List<RecordType>, from: Long, to: Long): Flow<List<Record>>

    /** Everything the CSV export writes: records of living pets that are not in the trash. */
    @Query(
        "SELECT r.* FROM records r JOIN pets p ON p.id = r.petId WHERE r.deletedAt IS NULL AND p.deletedAt IS NULL " +
            "ORDER BY r.dateEpochDay, r.minuteOfDay, r.createdAt",
    )
    suspend fun allForExport(): List<Record>

    /** Search query built by [RecordSearch]; re-runs whenever records or pets change. */
    @RawQuery(observedEntities = [Record::class, Pet::class])
    fun observeSearch(query: SupportSQLiteQuery): Flow<List<Record>>
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE recordId = :recordId ORDER BY createdAt")
    fun observeForRecord(recordId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE recordId = :recordId ORDER BY createdAt")
    suspend fun getForRecord(recordId: String): List<Attachment>

    @Query("SELECT recordId, COUNT(*) AS count FROM attachments WHERE recordId IN (:recordIds) GROUP BY recordId")
    fun observeCounts(recordIds: List<String>): Flow<List<AttachmentCount>>

    @Insert
    suspend fun insertAll(attachments: List<Attachment>)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachments WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: String)

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>
}

@Dao
interface SupplementDao {

    @Query("SELECT * FROM supplements WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeActive(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplements WHERE deletedAt IS NULL AND name = :name LIMIT 1")
    suspend fun findByName(name: String): Supplement?

    @Insert
    suspend fun insert(supplement: Supplement)

    @Query("SELECT id FROM supplements WHERE name LIKE :like ESCAPE '\\'")
    suspend fun idsMatching(like: String): List<String>

    /** Includes deleted ones, so old records keep showing the names they were saved with. */
    @Query("SELECT * FROM supplements ORDER BY createdAt")
    fun observeAll(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getById(id: String): Supplement?

    @Query("UPDATE supplements SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE supplements SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE supplements SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}

/** Same as [SupplementDao], for the toy list of play records (D77). */
@Dao
interface ToyDao {

    @Query("SELECT * FROM toys WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeActive(): Flow<List<Toy>>

    @Query("SELECT * FROM toys WHERE deletedAt IS NULL AND name = :name LIMIT 1")
    suspend fun findByName(name: String): Toy?

    @Insert
    suspend fun insert(toy: Toy)

    @Query("SELECT id FROM toys WHERE name LIKE :like ESCAPE '\\'")
    suspend fun idsMatching(like: String): List<String>

    /** Includes deleted ones, so old records keep showing the names they were saved with. */
    @Query("SELECT * FROM toys ORDER BY createdAt")
    fun observeAll(): Flow<List<Toy>>

    @Query("UPDATE toys SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE toys SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE toys SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}
