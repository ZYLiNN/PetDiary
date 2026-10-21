package com.zylinn.petdiary.ui.reminders

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderInput
import com.zylinn.petdiary.data.reminder.ReminderRepeat
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderScreensTest {

    @get:Rule val rule = createComposeRule()
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), zone) // Mon 2026-10-19 10:30 Taipei
    private val today = LocalDate.parse("2026-10-19")
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var cat: String
    private lateinit var dog: String
    private var edited: String? = null
    private var done = 0

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "reminder-ui").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock, reminders = db.reminderDao())
        reminders = ReminderRepository(db.reminderDao(), records, clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun add(day: LocalDate, minute: Int = 9 * 60, pet: String = cat, type: RecordType = RecordType.DEWORMING, title: String? = null, repeat: ReminderRepeat = ReminderRepeat.NONE) =
        runBlocking { reminders.add(ReminderInput(pet, type, title, day.toEpochDay(), minute, repeat)).id }

    private fun showList() {
        val vm = RemindersViewModel(reminders, clock)
        rule.setContent { PetDiaryTheme { RemindersScreen(vm = vm, onAdd = {}, onEdit = { edited = it }) } }
    }

    private fun showEditor(reminderId: String? = null, petId: String? = null, day: LocalDate? = null) {
        val vm = ReminderEditorViewModel(reminderId, petId, day, pets, reminders, clock)
        rule.setContent { PetDiaryTheme { ReminderEditorScreen(vm = vm, onDone = { done++ }) } }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("save_button").assertExists() }.isSuccess }
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    private fun open() = runBlocking { reminders.observeOpen().first() }

    // empty state
    @Test fun emptyListExplainsHowToAdd() {
        showList()
        waitFor("reminders_empty")
    }

    // P4-05: overdue first, in red, with the word 已過期
    @Test fun overdueShownFirstAndLabelled() {
        val later = add(today.plusDays(3))
        val todayLater = add(today, 15 * 60)
        val overdue = add(today.minusDays(1))
        val overdueToday = add(today, 8 * 60) // 08:00 < now 10:30
        showList()
        waitFor("reminder_$later")
        rule.onNodeWithTag("section_overdue").assertIsDisplayed()
        rule.onNodeWithTag("section_today").assertIsDisplayed()
        rule.onNodeWithTag("section_later").assertIsDisplayed()
        fun top(id: String) = rule.onNodeWithTag("reminder_$id").getUnclippedBoundsInRoot().top
        assertTrue(top(overdue) < top(overdueToday))
        assertTrue(top(overdueToday) < top(todayLater))
        assertTrue(top(todayLater) < top(later))
        rule.onNodeWithTag("reminder_$overdue").assertTextContains("已過期", substring = true)
        rule.onNodeWithTag("reminder_$later").assertTextContains("10/22", substring = true)
    }

    @Test fun rowShowsWhatPetAndRepeat() {
        val id = add(today.plusDays(1), 14 * 60 + 30, dog, RecordType.MEDICINE, "點眼藥", ReminderRepeat.DAILY)
        showList()
        waitFor("reminder_$id")
        rule.onNodeWithTag("reminder_$id").assertTextContains("點眼藥・雷利", substring = true)
        rule.onNodeWithTag("reminder_$id").assertTextContains("明天 14:30・每天", substring = true)
    }

    @Test fun tappingRowOpensEditor() {
        val id = add(today.plusDays(1))
        showList()
        waitFor("reminder_$id")
        rule.onNodeWithTag("reminder_$id").performClick()
        assertEquals(id, edited)
    }

    // P4-03, P4-11, P4-12
    @Test fun tickingCreatesRecordAndUndoRestores() {
        val id = add(today, 8 * 60, title = "蚤不到")
        showList()
        waitFor("reminder_$id")
        rule.onNodeWithTag("reminder_check_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已完成並記錄").assertExists() }.isSuccess }
        assertTrue(open().isEmpty())
        assertEquals("蚤不到", runBlocking { records.observeDay(cat, today.toEpochDay()).first() }.single().title)

        rule.onNodeWithText("復原").performClick()
        rule.waitUntil(5_000) { open().size == 1 }
        assertTrue(runBlocking { records.observeDay(cat, today.toEpochDay()).first() }.isEmpty())
    }

    @Test fun tickingRepeatingReminderKeepsItInTheListForNextTime() {
        val id = add(today, 8 * 60, repeat = ReminderRepeat.DAILY)
        showList()
        waitFor("reminder_$id")
        rule.onNodeWithTag("reminder_check_$id").performClick()
        rule.waitUntil(5_000) { open().single().reminder.dueEpochDay == today.plusDays(1).toEpochDay() }
        rule.onNodeWithTag("reminder_$id").assertTextContains("明天", substring = true)
    }

    // ---- editor ----

    // P4-01
    @Test fun creatingAReminderForTheChosenPet() {
        showEditor(petId = dog)
        rule.onNodeWithTag("rem_type_VACCINE").performClick()
        rule.onNodeWithTag("rem_type_VACCINE").assertIsSelected()
        rule.onNodeWithTag("rem_title").performTextInput("年度疫苗")
        rule.onNodeWithTag("rem_repeat_YEARLY").performClick()
        rule.onNodeWithTag("save_button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { done == 1 }
        val r = open().single().reminder
        assertEquals(dog, r.petId)
        assertEquals(RecordType.VACCINE, r.type)
        assertEquals("年度疫苗", r.title)
        assertEquals(ReminderRepeat.YEARLY, r.repeat)
        assertEquals(11 * 60, r.minuteOfDay) // next full hour after 10:30
        assertEquals(today.toEpochDay(), r.dueEpochDay)
    }

    @Test fun everyNDaysNeedsANumber() {
        showEditor(petId = cat)
        rule.onNodeWithTag("rem_repeat_EVERY_N_DAYS").performClick()
        rule.onNodeWithTag("rem_every").assertIsDisplayed()
        rule.onNodeWithTag("save_button").assertIsEnabled() // default 3
        rule.onNodeWithTag("rem_every").performTextReplacement("0")
        rule.onNodeWithTag("save_button").assertIsNotEnabled()
        rule.onNodeWithTag("rem_problem").assertTextContains("天數請填 1～365")
        rule.onNodeWithTag("rem_every").performTextReplacement("12")
        rule.onNodeWithTag("save_button").assertIsEnabled()
    }

    @Test fun startingFromAFutureDayUsesThatDay() {
        showEditor(petId = cat, day = today.plusDays(10))
        rule.onNodeWithTag("rem_date").assertTextContains("2026/10/29")
        rule.onNodeWithTag("rem_time").assertTextContains("09:00")
    }

    @Test fun editingLoadsFieldsAndSaves() {
        val id = add(today.plusDays(2), 8 * 60, dog, RecordType.MEDICINE, "心臟藥", ReminderRepeat.DAILY)
        showEditor(reminderId = id)
        rule.onNodeWithTag("editor_title").assertTextContains("編輯提醒")
        rule.onNodeWithTag("rem_type_MEDICINE").assertIsSelected()
        rule.onNodeWithTag("rem_repeat_DAILY").assertIsSelected()
        rule.onNodeWithTag("rem_title").assertTextContains("心臟藥")
        rule.onNodeWithTag("rem_repeat_MONTHLY").performClick()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        assertEquals(ReminderRepeat.MONTHLY, open().single().reminder.repeat)
    }

    @Test fun deletingNeedsConfirmation() {
        val id = add(today.plusDays(2))
        showEditor(reminderId = id)
        rule.onNodeWithTag("delete_button").performClick()
        rule.onNodeWithTag("confirm_delete").performClick()
        rule.waitUntil(5_000) { done == 1 }
        assertTrue(open().isEmpty())
    }

    @Test fun leavingWithChangesAsksFirst() {
        showEditor(petId = cat)
        rule.onNodeWithTag("rem_title").performTextInput("x")
        rule.onNodeWithTag("cancel_button").performClick()
        rule.onNodeWithTag("confirm_discard").assertIsDisplayed()
        assertEquals(0, done)
    }

    @Test fun petChoiceShownOnlyWithSeveralPets() {
        showEditor(petId = cat)
        rule.onNodeWithTag("rem_pet_$dog").assertIsDisplayed()
        rule.onNodeWithTag("rem_pet_$dog").performClick()
        rule.onNodeWithTag("rem_pet_$dog").assertIsSelected()
    }

    // P11-03: a new reminder can cover several pets → one reminder each
    @Test fun newReminderForTwoPetsCreatesOneEach() {
        showEditor(petId = cat)
        rule.onNodeWithTag("rem_pet_$dog").performClick()
        rule.onNodeWithTag("rem_pet_$cat").assertIsSelected()
        rule.onNodeWithTag("rem_pet_$dog").assertIsSelected()
        rule.onNodeWithTag("rem_type_DEWORMING").performClick()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        val made = open().map { it.reminder }
        assertEquals(setOf(cat, dog), made.map { it.petId }.toSet())
        assertTrue(made.all { it.type == RecordType.DEWORMING && it.dueEpochDay == made[0].dueEpochDay && it.minuteOfDay == made[0].minuteOfDay })
    }

    @Test fun theLastPetCannotBeUnticked() {
        showEditor(petId = cat)
        rule.onNodeWithTag("rem_pet_$cat").performClick()
        rule.onNodeWithTag("rem_pet_$cat").assertIsSelected()
        rule.onNodeWithTag("save_button").assertIsEnabled()
    }

    @Test fun editingKeepsASinglePet() {
        val id = add(today.plusDays(2), pet = cat)
        showEditor(reminderId = id)
        rule.onNodeWithTag("rem_pet_$dog").performClick()
        rule.onNodeWithTag("rem_pet_$cat").assertIsNotSelected()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }
        assertEquals(listOf(dog), open().map { it.reminder.petId })
    }
}
