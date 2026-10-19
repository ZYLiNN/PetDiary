package com.zylinn.petdiary.ui.pets.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.PhotoImporter
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class PetEditorScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: PetDiaryDatabase
    private lateinit var repo: PetRepository
    private lateinit var photoDir: File
    private lateinit var photos: FileStore
    private var done = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        repo = PetRepository(db.petDao(), clock)
        photoDir = File(context.cacheDir, "editor-test-photos").apply { deleteRecursively(); mkdirs() }
        photos = FileStore(photoDir)
    }

    @After fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun open(petId: String? = null) {
        val vm = PetEditorViewModel(petId, repo, photos, PhotoImporter(context.contentResolver, photos), clock)
        rule.setContent { PetDiaryTheme { PetEditorScreen(vm = vm, onDone = { done++ }) } }
    }

    /** Picks Oct 15, 2026 in the birthday dialog (clock fixed at 2026-10-19; the emulator's English date labels). */
    private fun pickBirthday() {
        rule.onNodeWithTag("field_birthday").performScrollTo().performClick()
        rule.onNode(hasText("October 15, 2026", substring = true), useUnmergedTree = true).performClick()
        rule.onNodeWithTag("date_confirm").performClick()
    }

    // P1-02, P1-03
    @Test fun requiredFieldsExplainWhySaveIsDisabled() {
        open()
        rule.onNodeWithTag("save_button").assertIsNotEnabled()
        rule.onNodeWithTag("hint_species").assertIsDisplayed()
        rule.onNodeWithText("請輸入名字").assertIsDisplayed()
        rule.onNodeWithTag("hint_birth").assertIsDisplayed()

        rule.onNodeWithTag("species_CAT").performClick()
        rule.onNodeWithTag("hint_species").assertDoesNotExist()
        rule.onNodeWithTag("field_name").performTextInput("雷米")
        rule.onNodeWithTag("save_button").assertIsNotEnabled()

        pickBirthday()
        rule.onNodeWithTag("hint_birth").assertDoesNotExist()
        rule.onNodeWithTag("save_button").assertIsEnabled()
    }

    // P1-02, P1-04
    @Test fun savingCreatesPetWithPickedBirthday() = runBlocking {
        open()
        rule.onNodeWithTag("species_DOG").performClick()
        rule.onNodeWithTag("field_name").performTextInput("雷利")
        pickBirthday()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }

        val pet = repo.activePets.first().single()
        assertEquals("雷利", pet.name)
        assertEquals(Species.DOG, pet.species)
        assertFalse(pet.birthEstimated)
        assertEquals(LocalDate.parse("2026-10-15").toEpochDay(), pet.birthEpochDay)
    }

    // P10: calendar colour
    @Test fun newPetStartsWithTheNextFreeColourAndTheChoiceIsSaved() = runBlocking {
        repo.add(PetInput(name = "前一隻", species = Species.CAT, birthEpochDay = 1)) // takes ORANGE
        open()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("color_WHITE").performScrollTo().assertIsSelected() }.isSuccess }
        rule.onNodeWithTag("color_BLUE").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("species_DOG").performClick()
        rule.onNodeWithTag("field_name").performTextInput("雷利")
        pickBirthday()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        assertEquals(PetColor.BLUE, repo.activePets.first().first { it.name == "雷利" }.color?.let { PetColor.valueOf(it) })
    }

    @Test fun editingShowsAnExistingPetsColourAndKeepsItWhenOtherFieldsChange() = runBlocking {
        val pet = repo.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 19000, color = PetColor.PINK))
        open(pet.id)
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("color_PINK").performScrollTo().assertIsSelected() }.isSuccess }
        rule.onNodeWithTag("field_name").performTextInput("2")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        assertEquals("PINK", repo.get(pet.id)!!.color)
    }

    // P1-13
    @Test fun optionalFieldsCollapsedUntilExpanded() = runBlocking {
        open()
        rule.onNodeWithTag("more_fields").assertDoesNotExist()
        rule.onNodeWithTag("toggle_more").performScrollTo().performClick()
        rule.onNodeWithTag("field_breed").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("species_CAT").performScrollTo().performClick()
        rule.onNodeWithTag("field_name").performScrollTo().performTextInput("布丁")
        pickBirthday()
        rule.onNodeWithTag("field_breed").performScrollTo().performTextInput("英短")
        rule.onNodeWithTag("field_chip").performScrollTo().performTextInput("900123")
        rule.onNodeWithTag("sex_2").performScrollTo().performClick()
        rule.onNodeWithTag("neutered_1").performScrollTo().performClick()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }

        val pet = repo.activePets.first().single()
        assertEquals("英短", pet.breed)
        assertEquals("900123", pet.chipNumber)
        assertEquals(com.zylinn.petdiary.data.pet.Sex.FEMALE, pet.sex)
        assertEquals(true, pet.neutered)
    }

    @Test fun editModePrefillsAndKeepsCreatedAt() = runBlocking {
        val pet = repo.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 19_000, breed = "米克斯"))
        open(pet.id)
        rule.onNodeWithText("編輯寵物").assertIsDisplayed()
        rule.onNodeWithText("雷米").assertIsDisplayed()
        rule.onNodeWithTag("more_fields").assertExists() // opened because breed is filled
        rule.onNodeWithTag("field_name").performTextReplacement("雷米米")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        val saved = repo.get(pet.id)!!
        assertEquals("雷米米", saved.name)
        assertEquals(pet.createdAt, saved.createdAt)
    }

    @Test fun cancelWithChangesAsksBeforeDiscarding() {
        open()
        rule.onNodeWithTag("cancel_button").performClick()
        assertEquals(1, done) // nothing typed → leaves immediately
    }

    @Test fun cancelAfterTypingShowsConfirm() {
        open()
        rule.onNodeWithTag("field_name").performTextInput("x")
        rule.onNodeWithTag("cancel_button").performClick()
        assertEquals(0, done)
        rule.onNodeWithTag("confirm_discard").performClick()
        assertEquals(1, done)
    }

    // P1-07 (import part; picking from gallery / camera is checked on device)
    @Test fun importedPhotoIsCopiedAndDownscaled() = runBlocking {
        val src = File(context.cacheDir, "big.png")
        Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8B5E3C.toInt()) }
            .let { bmp -> src.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val name = PhotoImporter(context.contentResolver, photos).import(Uri.fromFile(src))
        src.delete() // original gone; the copy must still work

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photos.file(name).path, bounds)
        assertTrue(photos.exists(name))
        assertTrue("too large: ${bounds.outWidth}x${bounds.outHeight}", max(bounds.outWidth, bounds.outHeight) <= PhotoImporter.MAX_EDGE)
        assertEquals(3f / 2f, bounds.outWidth.toFloat() / bounds.outHeight, 0.01f)
    }
}
