package com.zylinn.petdiary.data.record

import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderDao
import com.zylinn.petdiary.data.reminder.ReminderTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** Reminders created from a record's "下次日期" fire at 09:00 (the reminder can be edited afterwards). */
const val DEFAULT_REMINDER_MINUTE = 9 * 60

/** Everything the record form produces; ids and timestamps are filled in by the repository. */
data class RecordInput(
    val type: RecordType,
    val dateEpochDay: Long,
    val minuteOfDay: Int,
    val note: String? = null,
    val cost: Long? = null,
    val portions: Double? = null,
    val drankWater: Boolean? = null,
    val supplementIds: List<String> = emptyList(),
    val title: String? = null,
    val detail: String? = null,
    val hospital: String? = null,
    val nextEpochDay: Long? = null,
    val remindNext: Boolean = false,
    val dewormKind: DewormKind? = null,
    val weightKg: Double? = null,
    val durationMin: Int? = null,
    val peed: Boolean? = null,
    val pooped: Boolean? = null,
)

/** A file already copied into the attachment store, waiting to be linked to a record. */
data class NewAttachment(val fileName: String, val mimeType: String, val displayName: String, val sizeBytes: Long)

class RecordRepository(
    private val records: RecordDao,
    private val attachments: AttachmentDao,
    private val supplements: SupplementDao,
    private val files: FileStore,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Who is recording right now (cloud user id), or null when cloud sync is off. */
    private val author: () -> String? = { null },
    /** P4-09: records with a "下次日期" + remind flag keep a linked reminder in sync. */
    private val reminders: ReminderDao? = null,
) {
    fun observeDay(petId: String, day: Long): Flow<List<Record>> = records.observeDay(petId, day)
    fun observeDaysWithRecords(petId: String, from: Long, to: Long) = records.observeDaysWithRecords(petId, from, to)
    fun observeLastOfEachType(petId: String) = records.observeLastOfEachType(petId)
    fun observeLatest(petId: String, type: RecordType) = records.observeLatest(petId, type)
    fun observeTrashed() = records.observeTrashed()
    fun observeForStats(petIds: List<String>, types: List<RecordType>, from: Long, to: Long) =
        if (petIds.isEmpty()) flowOf(emptyList()) else records.observeForStats(petIds, types, from, to)
    fun observeAttachments(recordId: String) = attachments.observeForRecord(recordId)
    fun observeAttachmentCounts(recordIds: List<String>) = attachments.observeCounts(recordIds)
    val supplementsList = supplements.observeActive()
    /** id → name for every supplement ever created, deleted ones included, for showing old records. */
    val supplementNames = supplements.observeAll().map { list -> list.associate { it.id to it.name } }
    val supplementsAll = supplements.observeAll()

    /** P3 search. Blank queries return nothing so the screen can show a hint instead. */
    fun search(query: SearchQuery): Flow<List<Record>> {
        if (query.isBlank) return flowOf(emptyList())
        return flow {
            val text = query.text.trim()
            val suppIds = if (text.isEmpty()) emptyList() else supplements.idsMatching(RecordSearch.likePattern(text))
            emitAll(records.observeSearch(RecordSearch.build(query, suppIds)))
        }
    }

    suspend fun get(id: String): Record? = records.getById(id)
    suspend fun attachmentsOf(recordId: String) = attachments.getForRecord(recordId)
    suspend fun latest(petId: String, type: RecordType) = records.latest(petId, type)

    /**
     * Creates one independent record per pet (P2-13 / P2-14). Every pet after the first gets its
     * own copy of each attachment file, so deleting one pet's attachment never affects another.
     * Returns the new record ids in the same order as [petIds].
     */
    suspend fun addForPets(petIds: List<String>, input: RecordInput, newAttachments: List<NewAttachment> = emptyList()): List<String> {
        require(petIds.isNotEmpty()) { "at least one pet" }
        val now = clock.millis()
        val created = petIds.mapIndexed { i, petId ->
            val record = input.toRecord(id = newId(), petId = petId, createdAt = now, updatedAt = now)
            val links = newAttachments.map { a ->
                val fileName = if (i == 0) a.fileName else files.copy(a.fileName)
                Attachment(newId(), record.id, fileName, a.mimeType, a.displayName, a.sizeBytes, now)
            }
            record to links
        }
        // Attachments first: the cloud copy of a record carries its attachment list, so by the time the record
        // shows up as changed its attachments must already be in place.
        attachments.insertAll(created.flatMap { it.second })
        records.upsertAll(created.map { it.first })
        created.forEach { syncReminder(it.first) }
        return created.map { it.first.id }
    }

    /** Updates fields, links newly added attachments and deletes removed ones (rows and files). */
    suspend fun update(
        id: String,
        input: RecordInput,
        added: List<NewAttachment> = emptyList(),
        removedAttachmentIds: List<String> = emptyList(),
    ) {
        val existing = records.getById(id) ?: return
        val now = clock.millis()
        val current = attachments.getForRecord(id).associateBy { it.id }
        removedAttachmentIds.mapNotNull(current::get).forEach {
            attachments.delete(it.id)
            files.delete(it.fileName)
        }
        attachments.insertAll(added.map { Attachment(newId(), id, it.fileName, it.mimeType, it.displayName, it.sizeBytes, now) })
        // The record itself last (see addForPets): this is what marks it as changed for cloud sync.
        records.upsert(
            input.toRecord(id = id, petId = existing.petId, createdAt = existing.createdAt, updatedAt = now)
                .copy(deletedAt = existing.deletedAt, authorUid = existing.authorUid),
        )
        records.getById(id)?.let { syncReminder(it) }
    }

    /** The record's reminder follows its "下次日期": created, moved, or dropped when the flag is cleared. */
    private suspend fun syncReminder(record: Record) {
        val dao = reminders ?: return
        val linked = dao.getBySource(record.id)
        val wanted = record.remindNext && record.nextEpochDay != null && record.type in ReminderTypes && record.deletedAt == null
        if (!wanted) {
            linked.filter { it.completedAt == null }.forEach { dao.softDelete(it.id, clock.millis()) }
            return
        }
        val now = clock.millis()
        val open = linked.firstOrNull { it.completedAt == null && it.deletedAt == null }
        val title = record.title
        when {
            open != null -> if (open.dueEpochDay != record.nextEpochDay || open.title != title || open.petId != record.petId) {
                dao.upsert(
                    open.copy(petId = record.petId, type = record.type, title = title, dueEpochDay = record.nextEpochDay!!, notifiedFor = null, updatedAt = now),
                )
            }
            linked.any { it.completedAt != null } -> Unit // already ticked off from the list; don't bring it back
            else -> dao.upsert(
                Reminder(
                    id = newId(), petId = record.petId, type = record.type, title = title,
                    dueEpochDay = record.nextEpochDay!!, minuteOfDay = DEFAULT_REMINDER_MINUTE,
                    sourceRecordId = record.id, createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    suspend fun moveToTrash(id: String) {
        val now = clock.millis()
        records.softDelete(id, now)
        reminders?.softDeleteBySource(id, now)
    }

    suspend fun restore(id: String) {
        val now = clock.millis()
        records.restore(id, now)
        reminders?.restoreBySource(id, now)
    }

    /** Returns the existing supplement with this name, or creates it (S7: add from the feeding form). */
    suspend fun addSupplement(name: String): Supplement {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        supplements.findByName(trimmed)?.let { return it }
        return clock.millis().let { Supplement(newId(), trimmed, it, it) }.also { supplements.insert(it) }
    }

    /** P6-02: false when another supplement already has this name (or the name is blank). */
    suspend fun renameSupplement(id: String, name: String): Boolean {
        val trimmed = name.trim().take(50)
        if (trimmed.isEmpty()) return false
        val clash = supplements.findByName(trimmed)
        if (clash != null && clash.id != id) return false
        supplements.rename(id, trimmed, clock.millis())
        return true
    }

    /** Soft delete: old records keep the name; the chip just stops being offered. */
    suspend fun deleteSupplement(id: String) = supplements.softDelete(id, clock.millis())

    suspend fun restoreSupplement(id: String) = supplements.restore(id, clock.millis())

    /** Rows for the CSV export (P6-03). */
    suspend fun allForExport(): List<Record> = records.allForExport()

    private fun RecordInput.toRecord(id: String, petId: String, createdAt: Long, updatedAt: Long) = Record(
        id = id,
        petId = petId,
        type = type,
        dateEpochDay = dateEpochDay,
        minuteOfDay = minuteOfDay.coerceIn(0, 1439),
        note = note?.trim()?.ifEmpty { null },
        cost = cost,
        portions = portions,
        drankWater = drankWater,
        supplementIds = supplementIds.takeIf { it.isNotEmpty() }?.joinToString(","),
        title = title?.trim()?.ifEmpty { null },
        detail = detail?.trim()?.ifEmpty { null },
        hospital = hospital?.trim()?.ifEmpty { null },
        nextEpochDay = nextEpochDay,
        remindNext = remindNext && nextEpochDay != null,
        dewormKind = dewormKind,
        weightKg = weightKg,
        durationMin = durationMin,
        peed = peed,
        pooped = pooped,
        authorUid = author(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
