package com.zylinn.petdiary.ui.pets

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class PetDeleteUndoTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PetDiaryDatabase
    private lateinit var repo: PetRepository
    private lateinit var pet: Pet
    private var edited: String? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        repo = PetRepository(db.petDao(), Clock.systemUTC())
        pet = repo.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 19_000))
        val vm = PetHomeViewModel(repo, FileStore(File(context.cacheDir, "undo-test")), Clock.systemUTC())
        rule.setContent {
            PetDiaryTheme { PetHomeScreen(vm = vm, onAddPet = {}, onOpenPet = {}, onEditPet = { edited = it }) }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("pet_tile_${pet.id}").assertExists() }.isSuccess }
    }

    @After fun tearDown() = db.close()

    private fun openMenu() {
        rule.onNodeWithTag("pet_tile_${pet.id}").performTouchInput { longClick() }
    }

    // P1-10
    @Test fun longPressOffersEditAndDelete() {
        openMenu()
        rule.onNodeWithTag("menu_edit").assertIsDisplayed()
        rule.onNodeWithTag("menu_delete").assertIsDisplayed()
        rule.onNodeWithTag("menu_edit").performClick()
        assertEquals(pet.id, edited)
    }

    // P1-10, S6
    @Test fun deleteWithoutConfirmThenUndoRestores() = runBlocking {
        openMenu()
        rule.onNodeWithTag("menu_delete").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("復原").assertExists() }.isSuccess }
        rule.onNodeWithText("已將「雷米」移到垃圾桶").assertIsDisplayed()
        rule.onNodeWithTag("pet_tile_${pet.id}").assertDoesNotExist()

        rule.onNodeWithText("復原").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("pet_tile_${pet.id}").assertExists() }.isSuccess }
        assertNull(repo.get(pet.id)!!.deletedAt)
    }

    // P1-10, T-01
    @Test fun withoutUndoPetStaysInTrashAfterFiveSeconds() = runBlocking {
        openMenu()
        rule.onNodeWithTag("menu_delete").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("復原").assertExists() }.isSuccess }

        rule.mainClock.advanceTimeBy(5_100)
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithText("復原").assertDoesNotExist() }.isSuccess }

        assertNotNull(repo.get(pet.id)!!.deletedAt)
        assertEquals(listOf(pet.id), repo.trashedPets.first().map { it.id })
        rule.onNodeWithTag("pet_tile_${pet.id}").assertDoesNotExist()
    }
}
