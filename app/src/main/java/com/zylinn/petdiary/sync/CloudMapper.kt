package com.zylinn.petdiary.sync

import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderRepeat

/** The synced collections, named exactly like the Firestore sub-collections the security rules know about. */
enum class SyncTable(val path: String) { PETS("pets"), RECORDS("records"), REMINDERS("reminders"), SUPPLEMENTS("supplements") }

typealias CloudDoc = Map<String, Any>

/**
 * Entity ⇄ Firestore document. Null fields are left out (so clearing a field clears it in the cloud too),
 * and device-only state such as a reminder's `notifiedFor` is never uploaded. Pure functions, unit-tested.
 */
object CloudMapper {

    private fun MutableMap<String, Any>.put(key: String, value: Any?) { if (value != null) this[key] = value }

    // ---- reading helpers: Firestore hands back Long / Double / String / Boolean ----------------------
    private fun CloudDoc.str(k: String) = this[k] as? String
    private fun CloudDoc.long(k: String) = (this[k] as? Number)?.toLong()
    private fun CloudDoc.int(k: String) = (this[k] as? Number)?.toInt()
    private fun CloudDoc.dbl(k: String) = (this[k] as? Number)?.toDouble()
    private fun CloudDoc.bool(k: String) = this[k] as? Boolean
    private inline fun <reified E : Enum<E>> CloudDoc.enum(k: String): E? = str(k)?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    fun pet(p: Pet): CloudDoc = buildMap {
        put("id", p.id); put("name", p.name); put("species", p.species.name); put("customSpecies", p.customSpecies)
        put("birthEpochDay", p.birthEpochDay); put("birthEstimated", p.birthEstimated); put("sex", p.sex.name)
        put("neutered", p.neutered); put("breed", p.breed); put("chipNumber", p.chipNumber); put("note", p.note)
        put("photoFile", p.photoFile); put("color", p.color); put("createdAt", p.createdAt); put("updatedAt", p.updatedAt); put("deletedAt", p.deletedAt)
    }

    /** Null when the document is malformed (missing required fields / unknown enum) — such documents are skipped. */
    fun pet(d: CloudDoc): Pet? {
        return Pet(
            id = d.str("id") ?: return null,
            name = d.str("name") ?: return null,
            species = d.enum<Species>("species") ?: return null,
            customSpecies = d.str("customSpecies"),
            birthEpochDay = d.long("birthEpochDay") ?: return null,
            birthEstimated = d.bool("birthEstimated") ?: false,
            sex = d.enum<Sex>("sex") ?: Sex.UNKNOWN,
            neutered = d.bool("neutered"),
            breed = d.str("breed"), chipNumber = d.str("chipNumber"), note = d.str("note"),
            photoFile = d.str("photoFile")?.takeIf { it.isSafeFileName() },
            color = d.str("color")?.takeIf { PetColor.fromKey(it) != null },
            createdAt = d.long("createdAt") ?: return null,
            updatedAt = d.long("updatedAt") ?: return null,
            deletedAt = d.long("deletedAt"),
        )
    }

    /** The attachment list travels inside the record document; always written (maybe empty) so removals sync too. */
    fun record(r: Record, attachments: List<Attachment>): CloudDoc = record(r) + ("attachments" to attachments.map { a ->
        mapOf(
            "id" to a.id, "fileName" to a.fileName, "mimeType" to a.mimeType, "displayName" to a.displayName,
            "sizeBytes" to a.sizeBytes, "createdAt" to a.createdAt,
        )
    })

    /** null = the document says nothing about attachments (leave ours alone); otherwise the full list. */
    fun attachments(d: CloudDoc): List<Attachment>? {
        val recordId = d.str("id") ?: return null
        val list = d["attachments"] as? List<*> ?: return null
        return list.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any> ?: return@mapNotNull null
            Attachment(
                id = m.str("id") ?: return@mapNotNull null,
                recordId = recordId,
                fileName = m.str("fileName")?.takeIf { it.isSafeFileName() } ?: return@mapNotNull null,
                mimeType = m.str("mimeType") ?: return@mapNotNull null,
                displayName = m.str("displayName") ?: return@mapNotNull null,
                sizeBytes = m.long("sizeBytes") ?: return@mapNotNull null,
                createdAt = m.long("createdAt") ?: return@mapNotNull null,
            )
        }
    }

    /** File names come from other people's phones: never let one point outside our folder. */
    private fun String.isSafeFileName() = isNotEmpty() && length <= 100 && none { it == '/' || it == '\\' } && this != ".." && this != "."

    fun record(r: Record): CloudDoc = buildMap {
        put("id", r.id); put("petId", r.petId); put("type", r.type.name); put("dateEpochDay", r.dateEpochDay)
        put("minuteOfDay", r.minuteOfDay); put("note", r.note); put("cost", r.cost); put("portions", r.portions)
        put("drankWater", r.drankWater); put("supplementIds", r.supplementIds); put("title", r.title); put("detail", r.detail)
        put("hospital", r.hospital); put("nextEpochDay", r.nextEpochDay); put("remindNext", r.remindNext)
        put("dewormKind", r.dewormKind?.name); put("weightKg", r.weightKg); put("durationMin", r.durationMin)
        put("peed", r.peed); put("pooped", r.pooped); put("authorUid", r.authorUid)
        put("createdAt", r.createdAt); put("updatedAt", r.updatedAt); put("deletedAt", r.deletedAt)
    }

    fun record(d: CloudDoc): Record? = Record(
        id = d.str("id") ?: return null,
        petId = d.str("petId") ?: return null,
        type = d.enum<RecordType>("type") ?: return null,
        dateEpochDay = d.long("dateEpochDay") ?: return null,
        minuteOfDay = d.int("minuteOfDay") ?: return null,
        note = d.str("note"), cost = d.long("cost"), portions = d.dbl("portions"), drankWater = d.bool("drankWater"),
        supplementIds = d.str("supplementIds"), title = d.str("title"), detail = d.str("detail"), hospital = d.str("hospital"),
        nextEpochDay = d.long("nextEpochDay"), remindNext = d.bool("remindNext") ?: false,
        dewormKind = d.enum<DewormKind>("dewormKind"), weightKg = d.dbl("weightKg"),
        durationMin = d.int("durationMin"), peed = d.bool("peed"), pooped = d.bool("pooped"), authorUid = d.str("authorUid"),
        createdAt = d.long("createdAt") ?: return null,
        updatedAt = d.long("updatedAt") ?: return null,
        deletedAt = d.long("deletedAt"),
    )

    fun reminder(r: Reminder): CloudDoc = buildMap {
        put("id", r.id); put("petId", r.petId); put("type", r.type.name); put("title", r.title)
        put("dueEpochDay", r.dueEpochDay); put("minuteOfDay", r.minuteOfDay); put("repeat", r.repeat.name)
        put("repeatEvery", r.repeatEvery); put("sourceRecordId", r.sourceRecordId); put("completedAt", r.completedAt)
        put("createdAt", r.createdAt); put("updatedAt", r.updatedAt); put("deletedAt", r.deletedAt)
    }

    /** [notifiedFor] is this device's own "already announced" mark, so it is passed in rather than read from the cloud. */
    fun reminder(d: CloudDoc, notifiedFor: Long?): Reminder? = Reminder(
        id = d.str("id") ?: return null,
        petId = d.str("petId") ?: return null,
        type = d.enum<RecordType>("type") ?: return null,
        title = d.str("title"),
        dueEpochDay = d.long("dueEpochDay") ?: return null,
        minuteOfDay = d.int("minuteOfDay") ?: return null,
        repeat = d.enum<ReminderRepeat>("repeat") ?: return null,
        repeatEvery = d.int("repeatEvery") ?: 1,
        sourceRecordId = d.str("sourceRecordId"),
        completedAt = d.long("completedAt"),
        notifiedFor = notifiedFor,
        createdAt = d.long("createdAt") ?: return null,
        updatedAt = d.long("updatedAt") ?: return null,
        deletedAt = d.long("deletedAt"),
    )

    fun supplement(s: Supplement): CloudDoc = buildMap {
        put("id", s.id); put("name", s.name); put("createdAt", s.createdAt); put("updatedAt", s.updatedAt); put("deletedAt", s.deletedAt)
    }

    fun supplement(d: CloudDoc): Supplement? = Supplement(
        id = d.str("id") ?: return null,
        name = d.str("name") ?: return null,
        createdAt = d.long("createdAt") ?: return null,
        updatedAt = d.long("updatedAt") ?: return null,
        deletedAt = d.long("deletedAt"),
    )
}
