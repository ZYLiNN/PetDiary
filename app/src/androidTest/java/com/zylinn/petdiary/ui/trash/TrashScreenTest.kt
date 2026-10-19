package com.zylinn.petdiary.ui.trash

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.Pet
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.trash.TrashRepository
import com.zylinn.petdiary.ui.settings.SettingsScreen
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TrashScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-10-19T08:00:00Z")
    private fun clockAt(d: Duration) = Clock.fixed(start.plus(d), ZoneOffset.UTC)
    private lateinit var db: PetDiaryDatabase
    private lateinit var photos: FileStore
    private lateinit var photoDir: File
    private lateinit var trashed: Pet
    private lateinit var active: Pet

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        photoDir = File(context.cacheDir, "trash-screen-test").apply { deleteRecursively(); mkdirs() }
        photos = FileStore(photoDir)
        photos.file("t.jpg").writeText("img")
        val repo = PetRepository(db.petDao(), clockAt(Duration.ZERO))
        trashed = repo.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1, photoFile = "t.jpg"))
        active = repo.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1))
        repo.moveToTrash(trashed.id)
    }

    @After fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun show(now: Duration) {
        val clock = clockAt(now)
        val vm = TrashViewModel(
            PetRepository(db.petDao(), clock),
            RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), photos, clock),
            TrashRepository(db.petDao(), db.recordDao(), db.attachmentDao(), photos, photos, clock),
            photos,
            clock,
        )
        rule.setContent { PetDiaryTheme { TrashScreen(vm = vm, onBack = {}) } }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_list").assertExists() }.isSuccess }
    }

    // T-03
    @Test fun showsOnlyTrashedPetsWithDaysLeft() {
        show(Duration.ofDays(1).plusHours(3))
        rule.onNodeWithTag("trash_item_${trashed.id}").assertIsDisplayed()
        rule.onNodeWithTag("remaining_${trashed.id}").assertTextEquals("剩餘 6 天")
        rule.onNodeWithTag("trash_item_${active.id}").assertDoesNotExist()
    }

    // T-04
    @Test fun restoreReturnsPetToHome() = runBlocking {
        show(Duration.ofDays(2))
        rule.onNodeWithTag("restore_${trashed.id}").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_empty").assertExists() }.isSuccess }
        val pet = db.petDao().getById(trashed.id)!!
        assertNull(pet.deletedAt)
        assertEquals(setOf(trashed.id, active.id), PetRepository(db.petDao(), Clock.systemUTC()).activePets.first().map { it.id }.toSet())
    }

    // T-08
    @Test fun permanentDeleteNeedsConfirmation() = runBlocking {
        show(Duration.ofDays(2))
        rule.onNodeWithTag("delete_${trashed.id}").performClick()
        rule.onNodeWithText("永久刪除「雷米」？").assertIsDisplayed()
        rule.onNodeWithTag("confirm_permanent_delete").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_empty").assertExists() }.isSuccess }
        assertNull(db.petDao().getById(trashed.id))
        assertFalse(photos.exists("t.jpg"))
    }

    @Test fun cancellingConfirmationKeepsPet() = runBlocking {
        show(Duration.ofDays(2))
        rule.onNodeWithTag("delete_${trashed.id}").performClick()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("trash_item_${trashed.id}").assertIsDisplayed()
        assertEquals(trashed.id, db.petDao().getById(trashed.id)?.id)
    }

    private fun addRecord(petIdArg: String, trashed: Boolean = true): String = runBlocking {
        val repo = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), photos, clockAt(Duration.ZERO))
        repo.addForPets(listOf(petIdArg), com.zylinn.petdiary.data.record.RecordInput(
            com.zylinn.petdiary.data.record.RecordType.DEWORMING, LocalDate.parse("2026-10-15").toEpochDay(), 600, title = "蚤不到",
        )).single().also { if (trashed) repo.moveToTrash(it) }
    }

    // T-00, T-04 (record)
    @Test fun trashedRecordListedAndRestorable() = runBlocking {
        val id = addRecord(active.id)
        show(Duration.ofDays(1))
        rule.onNodeWithTag("trash_record_$id").assertIsDisplayed()
        rule.onNodeWithText("雷利・10/15").assertIsDisplayed()
        rule.onNodeWithTag("remaining_$id").assertTextEquals("剩餘 6 天")
        rule.onNodeWithTag("restore_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_record_$id").assertDoesNotExist() }.isSuccess }
        assertNull(db.recordDao().getById(id)!!.deletedAt)
    }

    // T-04a
    @Test fun restoringRecordOfTrashedPetRestoresPetToo() = runBlocking {
        val id = addRecord(trashed.id)
        show(Duration.ofDays(1))
        rule.onNodeWithText("連同寵物還原").assertIsDisplayed()
        rule.onNodeWithTag("restore_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_record_$id").assertDoesNotExist() }.isSuccess }
        assertNull(db.petDao().getById(trashed.id)!!.deletedAt)
        assertNull(db.recordDao().getById(id)!!.deletedAt)
    }

    @Test fun recordPermanentDeleteNeedsConfirmation() = runBlocking {
        val id = addRecord(active.id)
        show(Duration.ofDays(1))
        rule.onNodeWithTag("delete_$id").performClick()
        rule.onNodeWithText("永久刪除這筆「驅蟲」紀錄？").assertIsDisplayed()
        rule.onNodeWithTag("confirm_permanent_delete").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("trash_record_$id").assertDoesNotExist() }.isSuccess }
        assertNull(db.recordDao().getById(id))
    }
}
