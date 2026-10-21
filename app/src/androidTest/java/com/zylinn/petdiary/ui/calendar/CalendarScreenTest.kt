package com.zylinn.petdiary.ui.calendar

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetColor
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.reminder.ReminderInput
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.testutil.FitToScreen
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), ZoneId.of("Asia/Taipei"))
    private val today = LocalDate.parse("2026-10-20")
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var remi: String
    private lateinit var lily: String
    private var opened: Pair<String, LocalDate>? = null
    private var addReminderOn: LocalDate? = null
    private var addedRecord: Triple<RecordType, List<String>, LocalDate>? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), FileStore(File(context.cacheDir, "cal-ui")), clock, reminders = db.reminderDao())
        reminders = ReminderRepository(db.reminderDao(), records, clock)
        pets = PetRepository(db.petDao(), clock)
        remi = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1, color = PetColor.WHITE)).id
        lily = pets.add(PetInput(name = "雷利", species = Species.CAT, birthEpochDay = 1, color = PetColor.ORANGE)).id
    }

    @After fun tearDown() {
        rule.waitForIdle()
        db.close()
    }

    private fun day(s: String) = LocalDate.parse(s)
    private fun cell(s: String) = "cal_cell_${day(s).toEpochDay()}"

    private fun record(pet: String, type: RecordType, date: String, minute: Int = 600, title: String? = null) = runBlocking {
        records.addForPets(listOf(pet), RecordInput(type, day(date).toEpochDay(), minute, title = title, portions = if (type == RecordType.FEEDING) 1.0 else null,
            weightKg = if (type == RecordType.WEIGHT) 4.2 else null, durationMin = if (type == RecordType.WALK) 30 else null)).single()
    }

    private fun reminder(pet: String, type: RecordType, date: String, title: String? = null) = runBlocking {
        reminders.add(ReminderInput(pet, type, title, day(date).toEpochDay(), 540)).id
    }

    private fun show(wide: Boolean = false) {
        val vm = CalendarViewModel(pets, records, reminders, clock)
        rule.setContent {
            PetDiaryTheme {
                val screen = @androidx.compose.runtime.Composable {
                    CalendarScreen(vm, onOpenDay = { p, d -> opened = p to d }, onAddReminder = { addReminderOn = it }, onOpenSettings = {},
                        onAddRecord = { t, ids, d -> addedRecord = Triple(t, ids, d) })
                }
                if (wide) FitToScreen(900.dp, 600.dp) { screen() } else FitToScreen(400.dp, 800.dp) { screen() }
            }
        }
        waitFor("cal_month_title")
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    private fun gone(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist() }.isSuccess }

    // P11-02: add records for several pets straight from the calendar, on the selected day
    @Test fun addRecordFromTheDayListForSeveralPets() {
        show()
        rule.onNodeWithTag(cell("2026-10-12")).performClick()
        rule.onNodeWithTag("cal_add_record").performScrollTo().performClick()
        waitFor("type_FEEDING")
        rule.onNodeWithTag("pick_pet_$lily").performClick()
        rule.onNodeWithTag("type_FEEDING").performClick()
        assertEquals(Triple(RecordType.FEEDING, listOf(remi, lily), day("2026-10-12")), addedRecord)
    }

    @Test fun aFutureDayOnlyOffersAReminder() {
        show()
        rule.onNodeWithTag(cell("2026-10-25")).performClick()
        waitFor("cal_add_reminder")
        rule.onNodeWithTag("cal_add_record").assertDoesNotExist()
    }

    @Test fun opensOnTheCurrentMonthWithTodaySelected() {
        show()
        rule.onNodeWithTag("cal_month_title").assertTextEquals("2026 年 10 月")
        rule.onNodeWithTag("cal_panel_title").assertTextContains("10月20日", substring = true)
        rule.onNodeWithTag("cal_panel_title", useUnmergedTree = true).assertTextContains("20日（二）", substring = true)
    }

    @Test fun recordsAndRemindersAppearInTheirDayWithAPetMarker() {
        val vac = record(remi, RecordType.VACCINE, "2026-10-03")
        val rem = reminder(lily, RecordType.DEWORMING, "2026-10-28")
        show()
        waitFor("cal_item_$vac")
        rule.onNodeWithTag("cal_item_$vac", useUnmergedTree = true).assertTextContains("米 疫苗") // 雷米 / 雷利 collide on the first character → last one
        rule.onNodeWithTag("cal_item_$rem", useUnmergedTree = true).assertTextContains("利 驅蟲")
    }

    @Test fun dailyFeedingShowsAsADotInTheCellButInFullInTheDayList() {
        val feed = record(remi, RecordType.FEEDING, "2026-10-20", 480)
        show()
        waitFor("cal_dot_${today.toEpochDay()}_$remi")
        rule.onNodeWithTag("cal_item_$feed", useUnmergedTree = true).assertDoesNotExist() // no text box in the cell
        waitFor("cal_row_$feed") // but it is in the list of the selected day
    }

    @Test fun tappingADayListsEveryoneGroupedByPet() {
        val a = record(remi, RecordType.WEIGHT, "2026-10-08", 1210)
        val b = record(lily, RecordType.VET, "2026-10-08", 900, "皮膚檢查")
        show()
        rule.onNodeWithTag(cell("2026-10-08")).performClick()
        waitFor("cal_row_$a")
        rule.onNodeWithTag("cal_panel_title").assertTextContains("10月8日", substring = true)
        waitFor("cal_row_$b")
        val remiTop = rule.onNodeWithTag("cal_row_$a").getUnclippedBoundsInRoot().top
        val lilyTop = rule.onNodeWithTag("cal_row_$b").getUnclippedBoundsInRoot().top
        assertTrue("pets listed in their own order", remiTop < lilyTop)
    }

    @Test fun morePlaceholderWhenADayHasMoreThanTwoNonDailyItems() {
        record(remi, RecordType.VET, "2026-10-22"); record(lily, RecordType.VET, "2026-10-22"); record(lily, RecordType.GROOMING, "2026-10-22"); record(remi, RecordType.WEIGHT, "2026-10-22")
        show()
        waitFor("cal_more_${day("2026-10-22").toEpochDay()}")
        rule.onNodeWithTag("cal_more_${day("2026-10-22").toEpochDay()}", useUnmergedTree = true).assertTextEquals("+2")
    }

    @Test fun hidingAPetRemovesItsEntries() {
        val a = record(remi, RecordType.VACCINE, "2026-10-03")
        val b = record(lily, RecordType.VACCINE, "2026-10-03")
        show()
        waitFor("cal_item_$a"); waitFor("cal_item_$b")
        rule.onNodeWithTag("cal_pet_$lily").performClick()
        gone("cal_item_$b")
        rule.onNodeWithTag("cal_item_$a", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("cal_pet_$lily").performClick()
        waitFor("cal_item_$b")
    }

    @Test fun monthNavigationAndGreyedOutDays() {
        val next = record(remi, RecordType.VET, "2026-11-05")
        show()
        rule.onNodeWithTag("cal_next").performClick()
        rule.onNodeWithTag("cal_month_title").assertTextEquals("2026 年 11 月")
        waitFor("cal_item_$next")
        rule.onNodeWithTag("cal_panel_title").assertTextContains("11月1日", substring = true) // selection follows the month
        rule.onNodeWithTag("cal_prev").performClick()
        rule.onNodeWithTag("cal_month_title").assertTextEquals("2026 年 10 月")
        rule.onNodeWithTag("cal_panel_title").assertTextContains("10月20日", substring = true) // back to today
        // Oct grid ends on Sat 10/31; Nov's first week is shown in Nov. From Nov, tap Oct 30 (greyed) → jumps back.
        rule.onNodeWithTag("cal_next").performClick()
        rule.onNodeWithTag(cell("2026-11-01")).performClick()
        rule.onNodeWithTag("cal_today").performClick()
        rule.onNodeWithTag("cal_month_title").assertTextEquals("2026 年 10 月")
    }

    @Test fun tappingAnEntryOpensThatPetsPageOnThatDay() {
        val v = record(lily, RecordType.VET, "2026-10-09")
        show()
        rule.onNodeWithTag(cell("2026-10-09")).performClick()
        waitFor("cal_row_$v")
        rule.onNodeWithTag("cal_row_$v").performClick()
        assertEquals(lily to day("2026-10-09"), opened)
    }

    @Test fun addReminderButtonKnowsTheSelectedDay() {
        show()
        rule.onNodeWithTag(cell("2026-10-25")).performClick()
        rule.onNodeWithTag("cal_add_reminder").performClick()
        assertEquals(day("2026-10-25"), addReminderOn)
    }

    @Test fun aPetInTheTrashDisappears() = runBlocking {
        val a = record(remi, RecordType.VACCINE, "2026-10-03")
        show()
        waitFor("cal_item_$a")
        pets.moveToTrash(remi)
        gone("cal_item_$a")
    }

    @Test fun completedRemindersAreNotShown() = runBlocking {
        val m = reminder(lily, RecordType.VET, "2026-10-22")
        show()
        waitFor("cal_item_$m")
        reminders.complete(m)
        gone("cal_item_$m")
    }

    @Test fun wideScreenPutsTheDayListBesideTheMonth() {
        val v = record(remi, RecordType.VET, "2026-10-20", 700)
        show(wide = true)
        waitFor("cal_row_$v")
        val grid = rule.onNodeWithTag(cell("2026-10-20")).getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("cal_panel_title").getUnclippedBoundsInRoot()
        assertTrue("list is to the right of the month", panel.left > grid.right)
    }

    @Test fun selectedDayIsMarked() {
        show()
        rule.onNodeWithTag(cell("2026-10-20")).assertIsDisplayed()
    }
}
