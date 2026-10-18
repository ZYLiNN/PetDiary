package com.zylinn.petdiary.data.record

import com.zylinn.petdiary.data.photo.FileStore
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

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
) {
    fun observeDay(petId: String, day: Long): Flow<List<Record>> = records.observeDay(petId, day)
    fun observeDaysWithRecords(petId: String, from: Long, to: Long) = records.observeDaysWithRecords(petId, from, to)
    fun observeLastOfEachType(petId: String) = records.observeLastOfEachType(petId)
    fun observeLatest(petId: String, type: RecordType) = records.observeLatest(petId, type)
    fun observeTrashed() = records.observeTrashed()
    fun observeAttachments(recordId: String) = attachments.observeForRecord(recordId)
    fun observeAttachmentCounts(recordIds: List<String>) = attachments.observeCounts(recordIds)
    val supplementsList = supplements.observeActive()

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
        records.upsertAll(created.map { it.first })
        attachments.insertAll(created.flatMap { it.second })
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
        records.upsert(
            input.toRecord(id = id, petId = existing.petId, createdAt = existing.createdAt, updatedAt = now)
                .copy(deletedAt = existing.deletedAt),
        )
        val current = attachments.getForRecord(id).associateBy { it.id }
        removedAttachmentIds.mapNotNull(current::get).forEach {
            attachments.delete(it.id)
            files.delete(it.fileName)
        }
        attachments.insertAll(added.map { Attachment(newId(), id, it.fileName, it.mimeType, it.displayName, it.sizeBytes, now) })
    }

    suspend fun moveToTrash(id: String) = records.softDelete(id, clock.millis())

    suspend fun restore(id: String) = records.restore(id, clock.millis())

    /** Returns the existing supplement with this name, or creates it (S7: add from the feeding form). */
    suspend fun addSupplement(name: String): Supplement {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        supplements.findByName(trimmed)?.let { return it }
        return Supplement(newId(), trimmed, clock.millis()).also { supplements.insert(it) }
    }

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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
