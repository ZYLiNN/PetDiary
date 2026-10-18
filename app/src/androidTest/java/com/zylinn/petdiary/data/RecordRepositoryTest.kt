package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.LastOfType
import com.zylinn.petdiary.data.record.NewAttachment
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RecordRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-10-19T08:00:00Z")
    private fun clockAt(d: Duration = Duration.ZERO) = Clock.fixed(start.plus(d), ZoneOffset.UTC)
    private val today = LocalDate.parse("2026-10-19").toEpochDay()

    private lateinit var db: PetDiaryDatabase
    private lateinit var dir: File
    private lateinit var files: FileStore
    private lateinit var repo: RecordRepository
    private lateinit var cat: String
    private lateinit var dog: String

    private fun repoAt(d: Duration = Duration.ZERO) =
        RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clockAt(d))

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        dir = File(context.cacheDir, "record-test-files").apply { deleteRecursively(); mkdirs() }
        files = FileStore(dir)
        repo = repoAt()
        val pets = PetRepository(db.petDao(), clockAt())
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private val deworm = RecordInput(
        type = RecordType.DEWORMING, dateEpochDay = today, minuteOfDay = 9 * 60,
        title = "蚤不到", hospital = "愛心動物醫院", cost = 1200, nextEpochDay = today + 30, remindNext = true,
        dewormKind = DewormKind.EXTERNAL, note = "滴在後頸",
    )

    private fun attachment(name: String): NewAttachment {
        files.file(name).writeText("x-ray")
        return NewAttachment(name, "image/jpeg", "X光.jpg", 5)
    }

    // P2-13
    @Test fun multiPetRecordsAreIdentical() = runTest {
        val ids = repo.addForPets(listOf(cat, dog), deworm, listOf(attachment("a.jpg")))
        assertEquals(2, ids.size)
        val (r1, r2) = ids.map { repo.get(it)!! }
        assertEquals(cat, r1.petId)
        assertEquals(dog, r2.petId)
        listOf(r1, r2).forEach { r ->
            assertEquals(RecordType.DEWORMING, r.type)
            assertEquals(today, r.dateEpochDay)
            assertEquals(540, r.minuteOfDay)
            assertEquals("蚤不到", r.title)
            assertEquals("愛心動物醫院", r.hospital)
            assertEquals(1200L, r.cost)
            assertEquals(today + 30, r.nextEpochDay)
            assertTrue(r.remindNext)
            assertEquals(DewormKind.EXTERNAL, r.dewormKind)
            assertEquals("滴在後頸", r.note)
        }
        val a1 = repo.attachmentsOf(r1.id).single()
        val a2 = repo.attachmentsOf(r2.id).single()
        assertEquals(a1.displayName, a2.displayName)
        assertNotEquals("each pet gets its own file copy", a1.fileName, a2.fileName)
        assertEquals(files.file(a1.fileName).readText(), files.file(a2.fileName).readText())
    }

    // P2-14
    @Test fun multiPetRecordsAreIndependent() = runTest {
        val (id1, id2) = repo.addForPets(listOf(cat, dog), deworm, listOf(attachment("b.jpg")))
        val a1 = repo.attachmentsOf(id1).single()
        repo.update(id1, deworm.copy(title = "全能貓"), removedAttachmentIds = listOf(a1.id))
        repo.moveToTrash(id1)

        val r2 = repo.get(id2)!!
        assertEquals("蚤不到", r2.title)
        assertNull(r2.deletedAt)
        val a2 = repo.attachmentsOf(id2).single()
        assertTrue(files.exists(a2.fileName))
        assertFalse(files.exists(a1.fileName))
    }

    // P2-12
    @Test fun backdatedRecordAppearsOnItsDay() = runTest {
        val lastWeek = today - 7
        repo.addForPets(listOf(cat), RecordInput(RecordType.WEIGHT, lastWeek, 600, weightKg = 4.2))
        assertEquals(1, repo.observeDay(cat, lastWeek).first().size)
        assertTrue(repo.observeDay(cat, today).first().isEmpty())
        assertEquals(listOf(lastWeek), repo.observeDaysWithRecords(cat, today - 30, today).first())
    }

    @Test fun dayListSortedByTime() = runTest {
        repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today, 18 * 60, portions = 1.0))
        repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today, 8 * 60, portions = 1.5))
        assertEquals(listOf(480, 1080), repo.observeDay(cat, today).first().map { it.minuteOfDay })
    }

    // P2-03 (data)
    @Test fun lastOfEachTypeIgnoresTrashedAndOtherPets() = runTest {
        repo.addForPets(listOf(cat), deworm.copy(dateEpochDay = today - 45))
        val newer = repo.addForPets(listOf(cat), deworm.copy(dateEpochDay = today - 3)).single()
        repo.addForPets(listOf(cat), RecordInput(RecordType.VACCINE, today - 120, 600))
        repo.addForPets(listOf(dog), deworm.copy(dateEpochDay = today))
        repo.moveToTrash(newer)

        val last = repo.observeLastOfEachType(cat).first().toSet()
        assertEquals(setOf(LastOfType(RecordType.DEWORMING, today - 45), LastOfType(RecordType.VACCINE, today - 120)), last)
    }

    // S2 data source
    @Test fun latestFeedingIsMostRecentByDateThenTime() = runTest {
        repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today - 1, 20 * 60, portions = 2.0))
        repo.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today, 7 * 60, portions = 1.5, drankWater = true))
        val latest = repo.latest(cat, RecordType.FEEDING)!!
        assertEquals(1.5, latest.portions!!, 0.0)
        assertEquals(true, latest.drankWater)
        assertNull(repo.latest(dog, RecordType.FEEDING))
    }

    @Test fun supplementsDeduplicatedByName() = runTest {
        val a = repo.addSupplement(" 魚油 ")
        val b = repo.addSupplement("魚油")
        assertEquals(a.id, b.id)
        assertEquals(listOf("魚油"), repo.supplementsList.first().map { it.name })
    }

    // T-02
    @Test fun purgingPetRemovesItsRecordsAndAttachmentFiles() = runTest {
        val id = repo.addForPets(listOf(cat), deworm, listOf(attachment("c.jpg"))).single()
        val fileName = repo.attachmentsOf(id).single().fileName
        PetRepository(db.petDao(), clockAt()).moveToTrash(cat)

        val trash = TrashRepository(db.petDao(), db.recordDao(), db.attachmentDao(), files, files, clockAt(Duration.ofDays(7)))
        trash.purgeExpired()

        assertNull(repo.get(id))
        assertTrue(repo.attachmentsOf(id).isEmpty())
        assertFalse(files.exists(fileName))
    }

    // T-00
    @Test fun trashedRecordPurgedAfterSevenDaysOnly() = runTest {
        val id = repo.addForPets(listOf(cat), deworm).single()
        repo.moveToTrash(id)
        assertEquals(listOf(id), repo.observeTrashed().first().map { it.id })

        val early = TrashRepository(db.petDao(), db.recordDao(), db.attachmentDao(), files, files, clockAt(Duration.ofDays(6)))
        early.purgeExpired()
        assertTrue(repo.get(id) != null)

        val late = TrashRepository(db.petDao(), db.recordDao(), db.attachmentDao(), files, files, clockAt(Duration.ofDays(7)))
        late.purgeExpired()
        assertNull(repo.get(id))
    }

    @Test fun reminderFlagRequiresNextDate() = runTest {
        val id = repo.addForPets(listOf(cat), deworm.copy(nextEpochDay = null, remindNext = true)).single()
        assertFalse(repo.get(id)!!.remindNext)
    }
}
