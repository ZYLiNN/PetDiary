package com.zylinn.petdiary.data.pet

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {

    @Query("SELECT * FROM pets WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeActive(): Flow<List<Pet>>

    @Query("SELECT * FROM pets WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<Pet>>

    @Query("SELECT * FROM pets WHERE deletedAt IS NULL ORDER BY createdAt")
    suspend fun getActive(): List<Pet>

    @Query("SELECT * FROM pets WHERE id = :id")
    suspend fun getById(id: String): Pet?

    @Query("SELECT * FROM pets WHERE id = :id")
    fun observeById(id: String): Flow<Pet?>

    @Upsert
    suspend fun upsert(pet: Pet)

    @Query("UPDATE pets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE pets SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("SELECT * FROM pets WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<Pet>

    /** Avatar files still referenced, trashed pets included (they can be restored). */
    @Query("SELECT photoFile FROM pets WHERE photoFile IS NOT NULL")
    suspend fun allPhotoFiles(): List<String>

    @Query("DELETE FROM pets WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
