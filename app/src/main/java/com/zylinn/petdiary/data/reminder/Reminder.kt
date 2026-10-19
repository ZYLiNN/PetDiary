package com.zylinn.petdiary.data.reminder

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zylinn.petdiary.data.record.RecordType

enum class ReminderRepeat { NONE, DAILY, EVERY_N_DAYS, MONTHLY, YEARLY }

/** Record types a reminder can be made for; completing one creates a record of that type. */
val ReminderTypes = listOf(RecordType.MEDICINE, RecordType.VACCINE, RecordType.DEWORMING, RecordType.VET, RecordType.GROOMING)

/**
 * One upcoming occurrence. A repeating reminder moves [dueEpochDay] forward when completed;
 * a one-time reminder gets [completedAt]. Sync-ready: UUID key, timestamps, soft delete.
 */
@Entity(
    tableName = "reminders",
    indices = [Index("petId"), Index("dueEpochDay"), Index("sourceRecordId"), Index("deletedAt")],
)
data class Reminder(
    @PrimaryKey val id: String,
    val petId: String,
    val type: RecordType,
    /** Free text such as "點眼藥"; the type name is shown when empty. */
    val title: String? = null,
    val dueEpochDay: Long,
    val minuteOfDay: Int,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    /** Days between occurrences; only used by [ReminderRepeat.EVERY_N_DAYS]. */
    @ColumnInfo(defaultValue = "1") val repeatEvery: Int = 1,
    /** Record whose "下次日期" created this reminder (P4-09). */
    @ColumnInfo(defaultValue = "NULL") val sourceRecordId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val completedAt: Long? = null,
    /** Due time (epoch millis) already announced by a notification, so restarts don't repeat it. */
    @ColumnInfo(defaultValue = "NULL") val notifiedFor: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
)

/** A reminder with its pet's name; only rows whose pet is not in the trash. */
data class ReminderRow(
    @Embedded val reminder: Reminder,
    val petName: String,
)
