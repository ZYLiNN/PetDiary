package com.zylinn.petdiary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.pet.Species
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PetRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "pet-repo-test.db"
    private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: PetDiaryDatabase

    private fun open() = Room.databaseBuilder(context, PetDiaryDatabase::class.java, dbName).build()

    private val fullInput = PetInput(
        name = " 雷米 ",
        species = Species.CAT,
        birthEpochDay = 19_000,
        birthEstimated = true,
        sex = Sex.FEMALE,
        neutered = true,
        breed = "米克斯",
        chipNumber = "900123456789012",
        note = "怕吹風機 🐱",
        photoFile = "avatar-1.jpg",
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    // P1-06
    @Test fun allOptionalFieldsRoundTrip() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "id-1" }
        repo.add(fullInput)

        val pet = repo.get("id-1")!!
        assertEquals("雷米", pet.name)
        assertEquals(Species.CAT, pet.species)
        assertEquals(19_000L, pet.birthEpochDay)
        assertTrue(pet.birthEstimated)
        assertEquals(Sex.FEMALE, pet.sex)
        assertEquals(true, pet.neutered)
        assertEquals("米克斯", pet.breed)
        assertEquals("900123456789012", pet.chipNumber)
        assertEquals("怕吹風機 🐱", pet.note)
        assertEquals("avatar-1.jpg", pet.photoFile)
        assertEquals(clock.millis(), pet.createdAt)
        assertNull(pet.deletedAt)
    }

    // P10: calendar colours
    @Test fun newPetsGetDistinctColoursInOrderAndATrashedPetsColourIsFreedUp() = runTest {
        var n = 0
        val repo = PetRepository(db.petDao(), clock) { "color-${++n}" }
        val a = repo.add(fullInput.copy(name = "A")); val b = repo.add(fullInput.copy(name = "B")); val c = repo.add(fullInput.copy(name = "C"))
        assertEquals(listOf("ORANGE", "WHITE", "BLUE"), listOf(a, b, c).map { it.color })
        repo.moveToTrash(b.id)
        assertEquals("WHITE", repo.add(fullInput.copy(name = "D")).color)
    }

    @Test fun anExplicitColourIsKeptAndUpdatesOnlyChangeItWhenAsked() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "color-x" }
        repo.add(fullInput.copy(color = PetColor.PURPLE))
        assertEquals("PURPLE", repo.get("color-x")!!.color)
        repo.update("color-x", fullInput.copy(name = "改名", color = null))
        assertEquals("PURPLE", repo.get("color-x")!!.color) // not chosen again → unchanged
        repo.update("color-x", fullInput.copy(color = PetColor.GREEN))
        assertEquals("GREEN", repo.get("color-x")!!.color)
    }

    @Test fun blankOptionalFieldsStoredAsNull() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "id-2" }
        repo.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 18_000, breed = "  ", note = ""))
        val pet = repo.get("id-2")!!
        assertNull(pet.breed)
        assertNull(pet.note)
        assertNull(pet.neutered)
    }

    @Test fun customSpeciesOnlyKeptForOther() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "id-3" }
        repo.add(PetInput(name = "小綠", species = Species.CAT, customSpecies = "守宮", birthEpochDay = 1))
        assertNull(repo.get("id-3")!!.customSpecies)
        repo.update("id-3", PetInput(name = "小綠", species = Species.OTHER, customSpecies = "守宮", birthEpochDay = 1))
        assertEquals("守宮", repo.get("id-3")!!.customSpecies)
    }

    @Test fun updateKeepsCreatedAt() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "id-4" }
        repo.add(fullInput)
        val later = PetRepository(db.petDao(), Clock.offset(clock, Duration.ofHours(1))) { "unused" }
        later.update("id-4", fullInput.copy(name = "雷米米"))
        val pet = repo.get("id-4")!!
        assertEquals("雷米米", pet.name)
        assertEquals(clock.millis(), pet.createdAt)
        assertEquals(clock.millis() + 3_600_000, pet.updatedAt)
    }

    @Test fun trashedPetHiddenFromActiveAndRestorable() = runTest {
        val repo = PetRepository(db.petDao(), clock) { "id-5" }
        repo.add(fullInput)
        repo.moveToTrash("id-5")
        assertTrue(repo.activePets.first().isEmpty())
        assertEquals(listOf("id-5"), repo.trashedPets.first().map { it.id })

        repo.restore("id-5")
        assertEquals(listOf("id-5"), repo.activePets.first().map { it.id })
        assertNull(repo.get("id-5")!!.deletedAt)
    }

    // P1-11
    @Test fun dataSurvivesDatabaseReopen() = runTest {
        PetRepository(db.petDao(), clock) { "id-6" }.add(fullInput)
        db.close()

        db = open()
        val pet = PetRepository(db.petDao(), clock).get("id-6")
        assertNotNull(pet)
        assertEquals("雷米", pet!!.name)
    }
}
