package com.zylinn.petdiary.data.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecordType { FEEDING, WALK,
    /** Play time with toys (P12). */
    PLAY,
    MEDICINE, VACCINE, DEWORMING, WEIGHT, VET, GROOMING, ABNORMAL,
    /** Reminder-only (D36): a user-named reminder such as 剪指甲. Never stored as a record. */
    CUSTOM,
}

/** The types a record can have (everything except reminder-only [RecordType.CUSTOM]). */
val RecordableTypes: List<RecordType> = RecordType.entries.filter { it != RecordType.CUSTOM }

/**
 * The tiles of the "新增紀錄" panel: 疫苗 and 驅蟲 share one tile (shown as 疫苗／驅蟲, opened as [RecordType.VACCINE])
 * whose form then picks 疫苗 / 體內驅蟲 / 體外驅蟲 (P11-05). Both types still exist for stats, search and old records.
 */
val PanelTypes: List<RecordType> = RecordableTypes.filter { it != RecordType.DEWORMING }

/** Types sharing the medical form: name, dosage / diagnosis, hospital, cost, next date. */
val RecordType.isMedical get() = this in setOf(RecordType.MEDICINE, RecordType.VACCINE, RecordType.DEWORMING, RecordType.VET)

/** Every record may carry photos / PDFs (D78; before P12 only medical and abnormal ones). */
val RecordType.allowsAttachments get() = this != RecordType.CUSTOM

/** At most this many photos / PDFs per record (D78); the cloud rules enforce the same number. */
const val MAX_ATTACHMENTS = 10

enum class DewormKind { INTERNAL, EXTERNAL }

/** What was fed (D76). Records saved before P12 have none and count as [DRY]. */
enum class FoodKind { DRY, WET, MIXED, SNACK }

/**
 * One record = one pet at one point in time. Common columns plus type-specific nullable ones,
 * so all eight types share a single table. Sync-ready: UUID key, timestamps, soft delete.
 */
@Entity(
    tableName = "records",
    indices = [Index(value = ["petId", "dateEpochDay"]), Index("deletedAt")],
)
data class Record(
    @PrimaryKey val id: String,
    val petId: String,
    val type: RecordType,
    val dateEpochDay: Long,
    /** Minutes since midnight, 0..1439. */
    val minuteOfDay: Int,
    val note: String? = null,
    /** Whole NT dollars. */
    val cost: Long? = null,
    // Feeding
    val portions: Double? = null,
    val drankWater: Boolean? = null,
    /** Supplement ids joined by ','. */
    val supplementIds: String? = null,
    /** null on feeding records from before P12 (= dry food). */
    @ColumnInfo(defaultValue = "NULL") val foodKind: FoodKind? = null,
    // Play: toy ids joined by ','; the minutes use [durationMin]
    @ColumnInfo(defaultValue = "NULL") val toyIds: String? = null,
    // Medical / grooming / abnormal: medicine or vaccine name, grooming items, symptom
    val title: String? = null,
    /** Dosage (medicine) or diagnosis (vet). */
    val detail: String? = null,
    val hospital: String? = null,
    val nextEpochDay: Long? = null,
    /** User asked for a reminder on [nextEpochDay]; reminders are created in P4. */
    @ColumnInfo(defaultValue = "0") val remindNext: Boolean = false,
    val dewormKind: DewormKind? = null,
    // Weight
    val weightKg: Double? = null,
    // Walk: minutes, plus pee / poop checklist (the water checklist reuses [drankWater])
    @ColumnInfo(defaultValue = "NULL") val durationMin: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val peed: Boolean? = null,
    @ColumnInfo(defaultValue = "NULL") val pooped: Boolean? = null,
    /** Cloud user who recorded it (family groups show "誰記錄的"); null when recorded without cloud sync. */
    @ColumnInfo(defaultValue = "NULL") val authorUid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
) {
    val supplementIdList: List<String> get() = supplementIds?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    val toyIdList: List<String> get() = toyIds?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    /** Feeding records from before P12 have no kind: they were dry food (D76). */
    val effectiveFoodKind: FoodKind? get() = if (type == RecordType.FEEDING) foodKind ?: FoodKind.DRY else null
}

/** A file attached to a record, stored in the app's attachments directory. */
@Entity(tableName = "attachments", indices = [Index("recordId")])
data class Attachment(
    @PrimaryKey val id: String,
    val recordId: String,
    val fileName: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** A user-managed name in a pick list: supplements (feeding) or toys (play). */
interface NamedItem {
    val id: String
    val name: String
}

/** User-defined supplement names for the feeding form (S7). */
@Entity(tableName = "supplements")
data class Supplement(
    @PrimaryKey override val id: String,
    override val name: String,
    val createdAt: Long,
    /** Needed for cloud sync (last write wins); old rows start at 0 and are older than any edit. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
) : NamedItem

/** User-defined toy names for the play form (D77), managed like supplements. */
@Entity(tableName = "toys")
data class Toy(
    @PrimaryKey override val id: String,
    override val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) : NamedItem

/** Most recent date of each record type for one pet ("上次驅蟲：45 天前"). */
data class LastOfType(val type: RecordType, val lastDay: Long)

data class AttachmentCount(val recordId: String, val count: Int)
