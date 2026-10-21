package com.zylinn.petdiary.sync

import com.zylinn.petdiary.data.record.Toy
import com.zylinn.petdiary.data.record.FoodKind
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.Record
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.data.reminder.Reminder
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CloudMapperTest {

    private val pet = Pet(
        id = "p1", name = "雷米", species = Species.OTHER, customSpecies = "刺蝟", birthEpochDay = 19000, sex = Sex.FEMALE,
        neutered = true, breed = "x", note = "n", photoFile = "a.jpg", createdAt = 1, updatedAt = 2, deletedAt = 3,
    )

    @Test fun petColourTravelsAndUnknownColoursAreDropped() {
        assertEquals("BLUE", CloudMapper.pet(pet.copy(color = "BLUE"))["color"])
        assertEquals("BLUE", CloudMapper.pet(CloudMapper.pet(pet.copy(color = "BLUE")))!!.color)
        assertNull(CloudMapper.pet(CloudMapper.pet(pet) + ("color" to "MAGENTA"))!!.color)
    }

    @Test fun petRoundTrips() = assertEquals(pet, CloudMapper.pet(CloudMapper.pet(pet)))

    @Test fun petWithOnlyRequiredFieldsRoundTrips() {
        val min = Pet(id = "p2", name = "豆", species = Species.CAT, birthEpochDay = 1, createdAt = 1, updatedAt = 1)
        assertEquals(min, CloudMapper.pet(CloudMapper.pet(min)))
    }

    @Test fun nullFieldsAreLeftOutOfTheDocument() {
        val doc = CloudMapper.pet(pet.copy(note = null, deletedAt = null))
        assertFalse("note" in doc)
        assertFalse("deletedAt" in doc)
    }

    @Test fun recordRoundTripsWithEveryOptionalField() {
        val r = Record(
            id = "r1", petId = "p1", type = RecordType.WALK, dateEpochDay = 20000, minuteOfDay = 480, note = "n", cost = 500,
            portions = 1.5, drankWater = true, supplementIds = "a,b", title = "t", detail = "d", hospital = "h",
            nextEpochDay = 20030, remindNext = true, dewormKind = DewormKind.EXTERNAL, weightKg = 4.25, durationMin = 30,
            peed = true, pooped = false, foodKind = FoodKind.MIXED, toyIds = "t1,t2", createdAt = 1, updatedAt = 2, deletedAt = 3,
        )
        assertEquals(r, CloudMapper.record(CloudMapper.record(r)))
    }

    @Test fun firestoreNumbersComeBackAsLongOrDouble() {
        val doc = CloudMapper.record(Record(id = "r", petId = "p", type = RecordType.WEIGHT, dateEpochDay = 1, minuteOfDay = 2, weightKg = 4.0, createdAt = 1, updatedAt = 1))
            .toMutableMap().apply {
                this["minuteOfDay"] = 2L // Firestore returns every integer as Long
                this["weightKg"] = 4L // and a whole-number double may come back as Long too
            }
        val r = CloudMapper.record(doc)!!
        assertEquals(2, r.minuteOfDay)
        assertEquals(4.0, r.weightKg!!, 0.0)
    }

    @Test fun reminderDoesNotCarryTheDeviceOnlyNotifiedMark() {
        val r = Reminder(
            id = "m1", petId = "p1", type = RecordType.CUSTOM, title = "剪指甲", dueEpochDay = 20001, minuteOfDay = 540,
            repeat = ReminderRepeat.EVERY_N_DAYS, repeatEvery = 14, sourceRecordId = "r1", completedAt = 9, notifiedFor = 12345,
            createdAt = 1, updatedAt = 2, deletedAt = 3,
        )
        val doc = CloudMapper.reminder(r)
        assertFalse("notifiedFor" in doc)
        assertEquals(r.copy(notifiedFor = null), CloudMapper.reminder(doc, notifiedFor = null))
        assertEquals(777L, CloudMapper.reminder(doc, notifiedFor = 777)!!.notifiedFor)
    }

    // P12: toys sync like supplements
    @Test fun toyRoundTrips() {
        val t = Toy("t1", "逗貓棒", 1, 2, null)
        assertEquals(t, CloudMapper.toy(CloudMapper.toy(t)))
        assertNull(CloudMapper.toy(mapOf("id" to "t")))
    }

    /** A newer phone may send a record type this version doesn't know yet: skip it, never crash (P12). */
    @Test fun unknownFoodKindIsDroppedButTheRecordKept() {
        val doc = CloudMapper.record(Record("r", "p", RecordType.FEEDING, 1, 1, portions = 1.0, createdAt = 1, updatedAt = 1)) + ("foodKind" to "CAKE")
        assertNull(CloudMapper.record(doc)!!.foodKind)
    }

    @Test fun supplementRoundTrips() {
        val s = Supplement("s1", "魚油", 1, 2, 3)
        assertEquals(s, CloudMapper.supplement(CloudMapper.supplement(s)))
    }

    @Test fun malformedDocumentsAreSkippedNotCrashed() {
        assertNull(CloudMapper.pet(emptyMap()))
        assertNull(CloudMapper.pet(CloudMapper.pet(pet) + ("species" to "DRAGON")))
        assertNull(CloudMapper.record(CloudMapper.record(Record("r", "p", RecordType.FEEDING, 1, 1, createdAt = 1, updatedAt = 1)) + ("type" to "NOPE")))
        assertNull(CloudMapper.reminder(mapOf("id" to "m"), null))
        assertNull(CloudMapper.supplement(mapOf("id" to "s", "name" to "x")))
    }

    @Test fun tableNamesMatchTheSecurityRules() {
        assertEquals(listOf("pets", "records", "reminders", "supplements", "toys"), SyncTable.entries.map { it.path })
    }

    @Test fun recordCarriesItsAuthorAndAttachmentList() {
        val r = Record("r1", "p1", RecordType.VET, 20000, 480, authorUid = "u1", createdAt = 1, updatedAt = 2)
        val att = listOf(Attachment("a1", "r1", "x.pdf", "application/pdf", "報告.pdf", 1234, 5), Attachment("a2", "r1", "y.jpg", "image/jpeg", "y.jpg", 99, 6))
        val doc = CloudMapper.record(r, att)
        assertEquals("u1", doc["authorUid"])
        assertEquals(r, CloudMapper.record(doc))
        assertEquals(att, CloudMapper.attachments(doc))
    }

    @Test fun anEmptyAttachmentListIsKeptSoRemovalsSync_butAbsentMeansUntouched() {
        val r = Record("r1", "p1", RecordType.VET, 20000, 480, createdAt = 1, updatedAt = 2)
        assertEquals(emptyList<Attachment>(), CloudMapper.attachments(CloudMapper.record(r, emptyList())))
        assertNull(CloudMapper.attachments(CloudMapper.record(r))) // an older document that says nothing
    }

    @Test fun fileNamesFromOtherPhonesCannotEscapeOurFolder() {
        val r = Record("r1", "p1", RecordType.VET, 20000, 480, createdAt = 1, updatedAt = 2)
        val evil = CloudMapper.record(r, emptyList()) + ("attachments" to listOf(
            mapOf("id" to "a1", "fileName" to "../../databases/x", "mimeType" to "image/jpeg", "displayName" to "x", "sizeBytes" to 1L, "createdAt" to 1L),
            mapOf("id" to "a2", "fileName" to "ok.jpg", "mimeType" to "image/jpeg", "displayName" to "ok", "sizeBytes" to 1L, "createdAt" to 1L),
        ))
        assertEquals(listOf("ok.jpg"), CloudMapper.attachments(evil)!!.map { it.fileName })
        val pet = CloudMapper.pet(Pet("p", "n", Species.CAT, birthEpochDay = 1, createdAt = 1, updatedAt = 1, photoFile = "a.jpg")) + ("photoFile" to "/etc/passwd")
        assertNull(CloudMapper.pet(pet)!!.photoFile)
    }
}
