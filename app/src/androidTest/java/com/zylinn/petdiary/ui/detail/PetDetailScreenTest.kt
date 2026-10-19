package com.zylinn.petdiary.ui.detail

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.NewAttachment
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.RecordableTypes
import com.zylinn.petdiary.domain.RecordFormat.label
import com.zylinn.petdiary.testutil.FitToScreen
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class PetDetailScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), zone) // Sat 2026-10-17 12:00 Taipei
    private val today = LocalDate.parse("2026-10-17")
    private lateinit var db: PetDiaryDatabase
    private lateinit var records: RecordRepository
    private lateinit var petId: String

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "detail-test").apply { mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock)
        petId = PetRepository(db.petDao(), clock).add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = today.minusYears(3).toEpochDay())).id
    }

    @After fun tearDown() = db.close()

    private fun add(type: RecordType, day: LocalDate, minute: Int = 600, input: RecordInput.() -> RecordInput = { this }) =
        runBlocking { records.addForPets(listOf(petId), RecordInput(type, day.toEpochDay(), minute).input()).single() }

    private fun show(width: Dp = 411.dp, height: Dp = 900.dp) {
        val vm = PetDetailViewModel(petId, PetRepository(db.petDao(), clock), records, FileStore(context.cacheDir), clock)
        rule.setContent {
            PetDiaryTheme {
                FitToScreen(width, height) {
                    PetDetailScreen(vm = vm, onBack = {}, onPickType = { _, _, _ -> }, onOpenRecord = { _, _, _ -> })
                }
            }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_list").assertExists() }.isSuccess }
    }

    private fun day(d: LocalDate) = rule.onNodeWithTag("day_${d.toEpochDay()}")

    // P2-01
    @Test fun opensOnToday() {
        show()
        rule.onNodeWithTag("detail_title").assertTextEquals("10 月 17 日（六）")
        rule.onNodeWithTag("records_empty").assertIsDisplayed()
    }

    // P2-02
    @Test fun weekShowsDotsAndSwitchesDay() {
        add(RecordType.FEEDING, today.minusDays(2)) { copy(portions = 1.5) }
        show()
        rule.onNodeWithTag("dot_${today.minusDays(2).toEpochDay()}", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("dot_${today.minusDays(1).toEpochDay()}", useUnmergedTree = true).assertDoesNotExist()

        day(today.minusDays(2)).performClick()
        rule.onNodeWithTag("detail_title").assertTextEquals("10 月 15 日（四）")
        rule.onNodeWithText("主食 1.5 份").assertIsDisplayed()
    }

    // D16: the calendar can move into the future (reminders will live there) but records can't be added on those days.
    @Test fun futureDaysCanBeBrowsedButNotRecorded() {
        show()
        rule.onNodeWithTag("fab_add_record").assertExists()
        rule.onNodeWithTag("week_next").performClick()
        rule.onNodeWithTag("detail_title").assertTextEquals("10 月 24 日（六）")
        rule.onNodeWithTag("fab_add_record").assertDoesNotExist()
        rule.onNodeWithTag("records_empty").assertTextContains("未來的日子", substring = true)
        day(today.plusDays(4)).performClick()
        rule.onNodeWithTag("detail_title").assertTextEquals("10 月 21 日（三）")
        rule.onNodeWithTag("week_prev").performClick()
        rule.onNodeWithTag("week_prev").performClick()
        rule.onNodeWithTag("detail_title").assertTextEquals("10 月 7 日（三）")
        rule.onNodeWithTag("fab_add_record").assertExists()
    }

    // P2-02a
    @Test fun monthExpandsAndCollapsesAfterPicking() {
        val lastMonth = LocalDate.parse("2026-09-03")
        add(RecordType.VACCINE, lastMonth)
        show()
        rule.onNodeWithTag("month_calendar").assertDoesNotExist()
        rule.onNodeWithTag("toggle_month").performClick()
        rule.onNodeWithTag("month_title").assertTextEquals("2026 年 10 月")
        rule.onNodeWithTag("month_next").performClick() // future months are allowed (D16)
        rule.onNodeWithTag("month_title").assertTextEquals("2026 年 11 月")
        rule.onNodeWithTag("month_prev").performClick()
        rule.onNodeWithTag("month_prev").performClick()
        rule.onNodeWithTag("month_title").assertTextEquals("2026 年 9 月")
        rule.onNodeWithTag("dot_${lastMonth.toEpochDay()}", useUnmergedTree = true).assertExists()

        day(lastMonth).performClick()
        rule.onNodeWithTag("month_calendar").assertDoesNotExist()
        rule.onNodeWithTag("detail_title").assertTextEquals("9 月 3 日（四）")
        rule.onNodeWithText("疫苗").assertIsDisplayed()
    }

    // P2-03
    @Test fun lastRecordChipsShowDaysAgo() {
        add(RecordType.DEWORMING, today.minusDays(45))
        add(RecordType.VACCINE, today.minusDays(120))
        add(RecordType.FEEDING, today)
        show()
        rule.onNodeWithTag("last_DEWORMING").assertIsDisplayed()
        rule.onNodeWithText("驅蟲 45 天前").assertIsDisplayed()
        rule.onNodeWithText("疫苗 120 天前").assertExists()
        rule.onNodeWithTag("last_FEEDING").assertDoesNotExist()
    }

    @Test fun headerShowsAgeAndLatestWeight() {
        add(RecordType.WEIGHT, today.minusDays(3)) { copy(weightKg = 4.2) }
        show()
        rule.onNodeWithTag("header_subtitle").assertTextEquals("3 歲・4.2 kg")
    }

    // P2-09
    @Test fun everyTypeHasIconAndLabel() {
        RecordableTypes.forEachIndexed { i, t -> add(t, today, minute = 480 + i) }
        show(height = 2400.dp)
        RecordableTypes.forEach { t ->
            rule.onNodeWithContentDescription("${t.label}圖示", useUnmergedTree = true).assertExists()
        }
    }

    // P2-10
    @Test fun wideScreenShowsCalendarBesideList() {
        add(RecordType.FEEDING, today)
        show(width = 840.dp)
        rule.onNodeWithTag("month_calendar").assertIsDisplayed()
        rule.onNodeWithTag("toggle_month").assertDoesNotExist()
        val cal = rule.onNodeWithTag("calendar_pane").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("records_pane").getUnclippedBoundsInRoot()
        assertTrue("calendar should be left of list", cal.right <= list.left)
    }

    // P2-11
    @Test fun fiftyRecordsScrollToLast() {
        val ids = (0 until 50).map { add(RecordType.FEEDING, today, minute = it * 20) { copy(portions = 1.0) } }
        show()
        rule.onNodeWithTag("record_list").performScrollToIndex(49)
        rule.onNodeWithTag("record_${ids.last()}").assertIsDisplayed()
    }

    // P2-16
    @Test fun cardShowsAttachmentCount() {
        val dir = File(context.cacheDir, "detail-test").apply { mkdirs() }
        File(dir, "a.pdf").writeText("x"); File(dir, "b.pdf").writeText("y")
        val id = runBlocking {
            records.addForPets(
                listOf(petId),
                RecordInput(RecordType.VET, today.toEpochDay(), 600, title = "健檢"),
                listOf(NewAttachment("a.pdf", "application/pdf", "a.pdf", 1), NewAttachment("b.pdf", "application/pdf", "b.pdf", 1)),
            ).single()
        }
        show()
        rule.onNodeWithTag("attach_count_$id", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    // P2-05g, P2-05h
    @Test fun swipeLeftDeletesAndUndoRestores() {
        val id = add(RecordType.WEIGHT, today) { copy(weightKg = 4.2) }
        show()
        rule.onNodeWithTag("record_$id").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已刪除「體重」").assertExists() }.isSuccess }
        rule.onNodeWithTag("record_$id").assertDoesNotExist()
        rule.onNodeWithText("復原").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_$id").assertExists() }.isSuccess }
        assertNull(runBlocking { records.get(id) }!!.deletedAt)
    }

    // T-00
    @Test fun swipedRecordStaysInTrashWithoutUndo() {
        val id = add(RecordType.WEIGHT, today) { copy(weightKg = 4.2) }
        show()
        rule.onNodeWithTag("record_$id").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("復原").assertExists() }.isSuccess }
        rule.mainClock.advanceTimeBy(5_100)
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithText("復原").assertDoesNotExist() }.isSuccess }
        assertEquals(listOf(id), runBlocking { records.observeTrashed().first() }.map { it.id })
    }

    @Test fun tappingCardOpensItForEditing() {
        val id = add(RecordType.VACCINE, today) { copy(title = "三合一") }
        var opened: String? = null
        val vm = PetDetailViewModel(petId, PetRepository(db.petDao(), clock), records, FileStore(context.cacheDir), clock)
        rule.setContent {
            PetDiaryTheme { PetDetailScreen(vm = vm, onBack = {}, onPickType = { _, _, _ -> }, onOpenRecord = { rid, _, _ -> opened = rid }) }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_$id").assertExists() }.isSuccess }
        rule.onNodeWithTag("record_$id").performClick()
        assertEquals(id, opened)
    }
}
