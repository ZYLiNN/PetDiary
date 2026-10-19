package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.photo.OrphanCleaner
import com.zylinn.petdiary.data.record.NewAttachment
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class OrphanCleanerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
    private val now = clock.millis()
    private lateinit var db: PetDiaryDatabase
    private lateinit var photoDir: File
    private lateinit var attDir: File
    private lateinit var records: RecordRepository
    private lateinit var pets: PetRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        photoDir = File(context.cacheDir, "orphan-photos").apply { deleteRecursively(); mkdirs() }
        attDir = File(context.cacheDir, "orphan-att").apply { deleteRecursively(); mkdirs() }
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), FileStore(attDir), clock)
        pets = PetRepository(db.petDao(), clock)
    }

    @After fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
        attDir.deleteRecursively()
    }

    private fun file(dir: File, name: String, ageHours: Long) = File(dir, name).apply {
        writeText("x")
        setLastModified(now - ageHours * 3_600_000L)
    }

    private fun cleaner() = OrphanCleaner(db.petDao(), db.attachmentDao(), photoDir, attDir, { now })

    @Test fun removesOldUnreferencedFilesOnly() = runBlocking {
        val cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1, photoFile = "avatar.jpg")).id
        file(photoDir, "avatar.jpg", 100)
        file(photoDir, "orphan-photo.jpg", 100)
        file(photoDir, "fresh-photo.jpg", 1) // might belong to an open editor
        records.addForPets(listOf(cat), RecordInput(RecordType.VET, 20000, 600, title = "x"), listOf(NewAttachment("kept.pdf", "application/pdf", "k", 1)))
        file(attDir, "kept.pdf", 100)
        file(attDir, "orphan.pdf", 100)
        file(attDir, "fresh.pdf", 2)

        assertEquals(2, cleaner().clean())
        assertTrue(File(photoDir, "avatar.jpg").exists())
        assertFalse(File(photoDir, "orphan-photo.jpg").exists())
        assertTrue(File(photoDir, "fresh-photo.jpg").exists())
        assertTrue(File(attDir, "kept.pdf").exists())
        assertFalse(File(attDir, "orphan.pdf").exists())
        assertTrue(File(attDir, "fresh.pdf").exists())
    }

    @Test fun trashedPetAvatarAndAttachmentsAreKept() = runBlocking {
        val cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1, photoFile = "avatar.jpg")).id
        file(photoDir, "avatar.jpg", 100)
        records.addForPets(listOf(cat), RecordInput(RecordType.VET, 20000, 600, title = "x"), listOf(NewAttachment("kept.pdf", "application/pdf", "k", 1)))
        file(attDir, "kept.pdf", 100)
        pets.moveToTrash(cat) // restorable for 7 days
        assertEquals(0, cleaner().clean())
        assertTrue(File(photoDir, "avatar.jpg").exists())
        assertTrue(File(attDir, "kept.pdf").exists())
    }

    @Test fun missingDirectoriesAreFine() = runBlocking {
        photoDir.deleteRecursively()
        attDir.deleteRecursively()
        assertEquals(0, cleaner().clean())
    }
}
