package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.PhotoStore
import com.zylinn.petdiary.data.trash.PurgeTrashWorker
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TrashRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-10-19T08:00:00Z")
    private lateinit var db: PetDiaryDatabase
    private lateinit var photoDir: File
    private lateinit var photos: PhotoStore

    private fun clockAt(offset: Duration) = Clock.fixed(start.plus(offset), ZoneOffset.UTC)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        photoDir = File(context.cacheDir, "trash-test-photos").apply { deleteRecursively(); mkdirs() }
        photos = PhotoStore(photoDir)
    }

    @After fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private suspend fun addPetWithPhoto(id: String, photo: String): Pet {
        photos.file(photo).writeText("fake image")
        return PetRepository(db.petDao(), clockAt(Duration.ZERO)) { id }
            .add(PetInput(name = id, species = Species.CAT, birthEpochDay = 1, photoFile = photo))
    }

    // T-05, T-06
    @Test fun purgeRemovesOnlyExpiredPetsAndTheirPhotos() = runTest {
        addPetWithPhoto("old", "old.jpg")
        addPetWithPhoto("recent", "recent.jpg")
        addPetWithPhoto("active", "active.jpg")
        PetRepository(db.petDao(), clockAt(Duration.ZERO)).moveToTrash("old")
        PetRepository(db.petDao(), clockAt(Duration.ofDays(2))).moveToTrash("recent")

        // 7 days after "old" was deleted; "recent" has been in the trash for 5 days.
        val purged = TrashRepository(db.petDao(), photos, clockAt(Duration.ofDays(7))).purgeExpired()

        assertEquals(1, purged)
        assertNull(db.petDao().getById("old"))
        assertFalse(photos.exists("old.jpg"))
        assertNotNull(db.petDao().getById("recent"))
        assertTrue(photos.exists("recent.jpg"))
        assertNotNull(db.petDao().getById("active"))
        assertTrue(photos.exists("active.jpg"))
    }

    @Test fun notPurgedOneMillisecondBeforeDeadline() = runTest {
        addPetWithPhoto("p", "p.jpg")
        PetRepository(db.petDao(), clockAt(Duration.ZERO)).moveToTrash("p")
        val purged = TrashRepository(db.petDao(), photos, clockAt(Duration.ofDays(7).minusMillis(1))).purgeExpired()
        assertEquals(0, purged)
        assertTrue(photos.exists("p.jpg"))
    }

    // T-01 (pet part), T-04 (pet part)
    @Test fun restoreBringsBackPetUnchanged() = runTest {
        val original = addPetWithPhoto("p", "p.jpg")
        val repo = PetRepository(db.petDao(), clockAt(Duration.ofDays(1)))
        repo.moveToTrash("p")
        assertTrue(repo.activePets.first().isEmpty())

        repo.restore("p")
        val restored = repo.get("p")!!
        assertEquals(original.copy(updatedAt = restored.updatedAt), restored)
        assertTrue(photos.exists("p.jpg"))
    }

    // T-08
    @Test fun permanentDeleteOnlyAffectsTrashedPets() = runTest {
        addPetWithPhoto("active", "a.jpg")
        addPetWithPhoto("trashed", "t.jpg")
        PetRepository(db.petDao(), clockAt(Duration.ZERO)).moveToTrash("trashed")
        val trash = TrashRepository(db.petDao(), photos, clockAt(Duration.ofHours(1)))

        trash.deletePermanently("active")
        trash.deletePermanently("trashed")

        assertNotNull(db.petDao().getById("active"))
        assertTrue(photos.exists("a.jpg"))
        assertNull(db.petDao().getById("trashed"))
        assertFalse(photos.exists("t.jpg"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun photoNameCannotEscapeDirectory() {
        photos.delete("../outside.jpg")
    }

    // T-07
    @Test fun workerPurgesExpiredItemsInAppDatabase() = runTest {
        val container = (context as PetDiaryApplication).container
        val dao = container.database.petDao()
        val longAgo = System.currentTimeMillis() - Duration.ofDays(10).toMillis()
        dao.upsert(
            Pet(
                id = "worker-test", name = "x", species = Species.DOG, birthEpochDay = 1,
                createdAt = longAgo, updatedAt = longAgo, deletedAt = longAgo,
            ),
        )

        val result = TestListenableWorkerBuilder<PurgeTrashWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(dao.getById("worker-test"))
    }
}
