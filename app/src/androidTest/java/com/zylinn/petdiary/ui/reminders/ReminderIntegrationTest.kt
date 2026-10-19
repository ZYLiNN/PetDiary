package com.zylinn.petdiary.ui.reminders

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderInput
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.testutil.FitToScreen
import com.zylinn.petdiary.ui.detail.PetDetailScreen
import com.zylinn.petdiary.ui.detail.PetDetailViewModel
import com.zylinn.petdiary.ui.pets.PetHomeScreen
import com.zylinn.petdiary.ui.pets.PetHomeViewModel
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

/** Reminders as they appear on the pet page (calendar markers, day list) and on the home to-do block. */
@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), zone) // Mon 10:30
    private val today = LocalDate.parse("2026-10-19")
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var cat: String
    private var addReminderDay: LocalDate? = null
    private var openedReminder: String? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "reminder-int").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock, reminders = db.reminderDao())
        reminders = ReminderRepository(db.reminderDao(), records, clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun add(day: LocalDate, minute: Int = 9 * 60, type: RecordType = RecordType.VET, title: String? = null) =
        runBlocking { reminders.add(ReminderInput(cat, type, title, day.toEpochDay(), minute)).id }

    private fun showDetail() {
        val vm = PetDetailViewModel(cat, pets, records, FileStore(context.cacheDir), clock, reminders = reminders)
        rule.setContent {
            PetDiaryTheme {
                FitToScreen(411.dp, 900.dp) {
                    PetDetailScreen(
                        vm = vm, onBack = {}, onPickType = { _, _, _ -> }, onOpenRecord = { _, _, _ -> },
                        onAddReminder = { addReminderDay = it }, onOpenReminder = { openedReminder = it },
                    )
                }
            }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_list").assertExists() }.isSuccess }
    }

    private fun showHome() {
        val vm = PetHomeViewModel(pets, FileStore(context.cacheDir), clock, reminders)
        rule.setContent { PetDiaryTheme { PetHomeScreen(vm = vm, onAddPet = {}, onOpenPet = {}, onEditPet = {}, onOpenReminder = { openedReminder = it }) } }
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    private fun open() = runBlocking { reminders.observeOpen().first() }

    // D32 / calendar markers
    @Test fun calendarMarksDaysWithReminders() {
        val target = today.plusDays(4)
        add(target)
        showDetail()
        waitFor("reminder_dot_${target.toEpochDay()}")
        rule.onNodeWithTag("reminder_dot_${today.plusDays(2).toEpochDay()}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun futureDayListsItsReminderAndOffersToAddOne() {
        val target = today.plusDays(4)
        val id = add(target, 14 * 60, RecordType.VET, "回診")
        showDetail()
        rule.onNodeWithTag("day_${target.toEpochDay()}").performClick()
        waitFor("reminder_$id")
        rule.onNodeWithTag("reminder_$id").assertTextContains("回診", substring = true)
        rule.onNodeWithTag("fab_add_record").assertDoesNotExist()
        rule.onNodeWithTag("fab_add_reminder").performClick()
        assertEquals(target, addReminderDay)
        rule.onNodeWithTag("reminder_$id").performClick()
        assertEquals(id, openedReminder)
    }

    @Test fun emptyFutureDayInvitesAReminder() {
        showDetail()
        rule.onNodeWithTag("day_${today.plusDays(2).toEpochDay()}").performClick()
        rule.onNodeWithTag("records_empty").assertTextContains("新增提醒", substring = true)
    }

    @Test fun tickingFromThePetPageRecordsIt() {
        val id = add(today, 8 * 60, RecordType.DEWORMING, "蚤不到")
        showDetail()
        waitFor("reminder_check_$id")
        rule.onNodeWithTag("reminder_check_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已完成並記錄").assertExists() }.isSuccess }
        assertTrue(open().isEmpty())
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_list").assertIsDisplayed(); rule.onNodeWithText("蚤不到", substring = true).assertExists() }.isSuccess }
    }

    // ---- home: S4 今天待辦 (P4-10, P4-11, P4-12) ----

    @Test fun todoBlockHiddenWithoutDueReminders() {
        add(today.plusDays(1))
        showHome()
        rule.onNodeWithTag("pet_grid").assertIsDisplayed()
        rule.onNodeWithTag("today_todos").assertDoesNotExist()
    }

    @Test fun todoBlockListsTodayAndOverdue() {
        val overdue = add(today.minusDays(2))
        val dueToday = add(today, 15 * 60)
        val tomorrow = add(today.plusDays(1))
        showHome()
        waitFor("todo_$overdue")
        rule.onNodeWithTag("todo_$dueToday").assertIsDisplayed()
        rule.onNodeWithTag("todo_$tomorrow").assertDoesNotExist()
        rule.onNodeWithTag("todo_$overdue").assertTextContains("已過期", substring = true)
    }

    @Test fun tickingATodoRecordsItAndUndoBringsItBack() {
        val id = add(today, 8 * 60, RecordType.MEDICINE, "心臟藥")
        showHome()
        waitFor("todo_$id")
        rule.onNodeWithTag("reminder_check_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已完成並記錄").assertExists() }.isSuccess }
        rule.onNodeWithTag("today_todos").assertDoesNotExist()
        assertEquals("心臟藥", runBlocking { records.observeDay(cat, today.toEpochDay()).first() }.single().title)

        rule.onNodeWithText("復原").performClick()
        waitFor("todo_$id")
        assertTrue(runBlocking { records.observeDay(cat, today.toEpochDay()).first() }.isEmpty())
    }
}
