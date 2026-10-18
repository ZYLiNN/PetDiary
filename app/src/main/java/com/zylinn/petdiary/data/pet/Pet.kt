package com.zylinn.petdiary.data.pet

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Species { CAT, DOG, RABBIT, RODENT, BIRD, REPTILE, FISH, OTHER }

enum class Sex { UNKNOWN, MALE, FEMALE }

/**
 * A pet. Sync-ready from day one: UUID key, timestamps in epoch millis, soft delete.
 * `deletedAt != null` means the pet is in the trash.
 */
@Entity(tableName = "pets", indices = [Index("deletedAt")])
data class Pet(
    @PrimaryKey val id: String,
    val name: String,
    val species: Species,
    /** Free-text species name, used when [species] is [Species.OTHER]. */
    val customSpecies: String? = null,
    /** Birth date as epoch day (LocalDate.toEpochDay). */
    val birthEpochDay: Long,
    /** True when the user entered an approximate age instead of a real birthday. */
    val birthEstimated: Boolean = false,
    val sex: Sex = Sex.UNKNOWN,
    /** null = not specified. */
    val neutered: Boolean? = null,
    val breed: String? = null,
    val chipNumber: String? = null,
    val note: String? = null,
    /** File name of the avatar inside the app's private photo directory. */
    val photoFile: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
)
