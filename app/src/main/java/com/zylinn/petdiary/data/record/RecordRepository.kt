package com.zylinn.petdiary.data.record

import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderDao
import com.zylinn.petdiary.data.reminder.ReminderTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    val foodKind: FoodKind? = null,
    val toyIds: List<String> = emptyList(),
    /** Purchase category id (P14-03). */
    val category: String? = null,
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

/** Add / rename / delete of a user-managed pick list; supplements and toys share the management screen. */
interface NameListOps {
    val active: Flow<List<NamedItem>>
    suspend fun add(name: String): NamedItem
    /** false when another item already has this name (or the name is blank). */
    suspend fun rename(id: String, name: String): Boolean
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

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
    /** The toy list for play records (D77); optional so tests that don't need it can skip it. */
    private val toys: ToyDao? = null,
    /** The user's own purchase categories (P14-03); optional like [toys]. */
    private val categories: CategoryDao? = null,
) {
    fun observeDay(petId: String, day: Long): Flow<List<Record>> = records.observeDay(petId, day)
    fun observeRange(from: Long, to: Long) = records.observeRange(from, to)
    fun observeDaysWithRecords(petId: String, from: Long, to: Long) = records.observeDaysWithRecords(petId, from, to)
    fun observeLastOfEachType(petId: String) = records.observeLastOfEachType(petId)
    fun observeLatest(petId: String, type: RecordType) = records.observeLatest(petId, type)
    fun observeTrashed() = records.observeTrashed()
    fun observeForStats(petIds: List<String>, types: List<RecordType>, from: Long, to: Long) =
        if (petIds.isEmpty()) flowOf(emptyList()) else records.observeForStats(petIds, types, from, to)
    fun observeAttachments(recordId: String) = attachments.observeForRecord(recordId)
    fun observeAttachmentCounts(recordIds: List<String>) = attachments.observeCounts(recordIds)
    fun observeAlbum() = attachments.observeAlbum()
    val supplementsList = supplements.observeActive()
    /** id → name for every supplement ever created, deleted ones included, for showing old records. */
    val supplementNames = supplements.observeAll().map { list -> list.associate { it.id to it.name } }
    val supplementsAll = supplements.observeAll()
    val toysList: Flow<List<Toy>> = toys?.observeActive() ?: flowOf(emptyList())
    /** id → name for every toy ever created, deleted ones included. */
    val toyNames: Flow<Map<String, String>> = (toys?.observeAll() ?: flowOf(emptyList())).map { list -> list.associate { it.id to it.name } }
    val categoriesList: Flow<List<PurchaseCategory>> = categories?.observeActive() ?: flowOf(emptyList())
    /** Purchase category id → name: the built-in ones plus every one the user ever added. */
    val categoryNames: Flow<Map<String, String>> = (categories?.observeAll() ?: flowOf(emptyList()))
        .map { list -> BuiltInCategory.names + list.associate { it.id to it.name } }
    /** Supplement, toy and category names together (ids never collide), for record summaries. */
    val itemNames: Flow<Map<String, String>> = combine(supplementNames, toyNames, categoryNames) { s, t, c -> s + t + c }

    /** P3 search. Blank queries return nothing so the screen can show a hint instead. */
    fun search(query: SearchQuery): Flow<List<Record>> {
        if (query.isBlank) return flowOf(emptyList())
        return flow {
            val text = query.text.trim()
            val like = RecordSearch.likePattern(text)
            val suppIds = if (text.isEmpty()) emptyList() else supplements.idsMatching(like)
            val toyIds = if (text.isEmpty() || toys == null) emptyList() else toys.idsMatching(like)
            val categoryIds = if (text.isEmpty()) emptyList() else
                BuiltInCategory.entries.filter { it.label.contains(text) }.map { it.id } + (categories?.idsMatching(like) ?: emptyList())
            emitAll(records.observeSearch(RecordSearch.build(query, suppIds, toyIds, categoryIds)))
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

    val supplementOps: NameListOps = object : NameListOps {
        override val active: Flow<List<NamedItem>> = supplementsList
        override suspend fun add(name: String) = addSupplement(name)
        override suspend fun rename(id: String, name: String) = renameSupplement(id, name)
        override suspend fun delete(id: String) = deleteSupplement(id)
        override suspend fun restore(id: String) = restoreSupplement(id)
    }

    /** Returns the existing toy with this name, or creates it (D77: add from the play form). */
    suspend fun addToy(name: String): Toy {
        val dao = checkNotNull(toys) { "no toy list" }
        val trimmed = name.trim().take(50)
        require(trimmed.isNotEmpty()) { "name required" }
        dao.findByName(trimmed)?.let { return it }
        return clock.millis().let { Toy(newId(), trimmed, it, it) }.also { dao.insert(it) }
    }

    val toyOps: NameListOps = object : NameListOps {
        override val active: Flow<List<NamedItem>> = toysList
        override suspend fun add(name: String) = addToy(name)
        override suspend fun rename(id: String, name: String): Boolean {
            val dao = toys ?: return false
            val trimmed = name.trim().take(50)
            if (trimmed.isEmpty()) return false
            val clash = dao.findByName(trimmed)
            if (clash != null && clash.id != id) return false
            dao.rename(id, trimmed, clock.millis())
            return true
        }
        override suspend fun delete(id: String) { toys?.softDelete(id, clock.millis()) }
        override suspend fun restore(id: String) { toys?.restore(id, clock.millis()) }
    }

    /**
     * Returns the category with this name — a built-in one, an existing one of the user's — or creates it
     * (P14-03: add from the purchase form).
     */
    suspend fun addCategory(name: String): NamedItem {
        val dao = checkNotNull(categories) { "no category list" }
        val trimmed = name.trim().take(50)
        require(trimmed.isNotEmpty()) { "name required" }
        BuiltInCategory.entries.firstOrNull { it.label == trimmed }?.let { return BuiltInItem(it.id, it.label) }
        dao.findByName(trimmed)?.let { return it }
        return clock.millis().let { PurchaseCategory(newId(), trimmed, it, it) }.also { dao.insert(it) }
    }

    val categoryOps: NameListOps = object : NameListOps {
        override val active: Flow<List<NamedItem>> = categoriesList
        override suspend fun add(name: String) = addCategory(name)
        override suspend fun rename(id: String, name: String): Boolean {
            val dao = categories ?: return false
            val trimmed = name.trim().take(50)
            // A built-in name would show up twice in the form.
            if (trimmed.isEmpty() || BuiltInCategory.entries.any { it.label == trimmed }) return false
            val clash = dao.findByName(trimmed)
            if (clash != null && clash.id != id) return false
            dao.rename(id, trimmed, clock.millis())
            return true
        }
        override suspend fun delete(id: String) { categories?.softDelete(id, clock.millis()) }
        override suspend fun restore(id: String) { categories?.restore(id, clock.millis()) }
    }

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
        foodKind = foodKind.takeIf { type == RecordType.FEEDING },
        toyIds = toyIds.takeIf { it.isNotEmpty() && type == RecordType.PLAY }?.joinToString(","),
        category = category.takeIf { type == RecordType.PURCHASE },
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
