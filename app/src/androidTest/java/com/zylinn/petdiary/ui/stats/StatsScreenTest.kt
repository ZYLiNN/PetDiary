package com.zylinn.petdiary.ui.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class StatsScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), zone) // Mon 2026-10-19
    private val today = LocalDate.parse("2026-10-19")
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var cat: String
    private lateinit var dog: String

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "stats-ui").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() {
        rule.waitForIdle() // let in-flight flow collections finish before the DB goes away
        db.close()
    }

    private fun add(pet: String, type: RecordType, day: LocalDate, f: RecordInput.() -> RecordInput = { this }) =
        runBlocking { records.addForPets(listOf(pet), RecordInput(type, day.toEpochDay(), 600).f()) }

    private fun show(): StatsViewModel {
        val vm = StatsViewModel(pets, records, clock)
        rule.setContent { PetDiaryTheme { StatsScreen(vm) } }
        // Either the filters (there are pets) or the guide (there are none) show up once the pets have loaded.
        rule.waitUntil(5_000) {
            listOf("range_WEEK", "stats_no_pets").any { runCatching { rule.onNodeWithTag(it, useUnmergedTree = true).assertExists() }.isSuccess }
        }
        return vm
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    // P5-01
    @Test fun defaultsToAllPetsThisWeekFeeding() {
        add(cat, RecordType.FEEDING, today) { copy(portions = 1.5) }
        show()
        waitFor("stats_chart")
        rule.onNodeWithTag("stats_title").assertTextEquals("10/18 – 10/24")
        rule.onNodeWithTag("range_WEEK").assertIsSelected()
        rule.onNodeWithTag("metric_FEEDING").assertIsSelected()
        rule.onNodeWithTag("stats_pet_$cat").assertIsSelected()
        rule.onNodeWithTag("stats_pet_$dog").assertIsSelected()
    }

    // P5-06 at the screen level: the text under the chart is the DB data
    @Test fun summaryMatchesRecords() {
        add(cat, RecordType.FEEDING, today) { copy(portions = 1.0, drankWater = true) }
        add(cat, RecordType.FEEDING, today.plusDays(1)) { copy(portions = 2.0) }
        add(dog, RecordType.FEEDING, today) { copy(portions = 3.0) }
        show()
        waitFor("stats_summary_$cat")
        rule.onNodeWithTag("stats_summary_$cat").assertTextContains("平均每天 1.5 份（共 3 份）・有喝水 1 次", substring = true)
        rule.onNodeWithTag("stats_summary_$dog").assertTextContains("共 3 份", substring = true)
    }

    // P12-03 (D76): feeding narrowed to one kind of food
    @Test fun feedingCanShowOneKindOnly() {
        add(cat, RecordType.FEEDING, today) { copy(portions = 2.0, foodKind = com.zylinn.petdiary.data.record.FoodKind.DRY) }
        add(cat, RecordType.FEEDING, today) { copy(portions = 0.5, foodKind = com.zylinn.petdiary.data.record.FoodKind.SNACK) }
        show()
        waitFor("stats_summary_$cat")
        rule.onNodeWithTag("stats_food_ALL").assertIsSelected()
        rule.onNodeWithTag("stats_summary_$cat").assertTextContains("（乾食 2 份・零食 0.5 份）", substring = true)
        rule.onNodeWithTag("food_row").performScrollToNode(hasTestTag("stats_food_SNACK"))
        rule.onNodeWithTag("stats_food_SNACK").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("stats_summary_$cat").assertTextContains("共 0.5 份", substring = true) }.isSuccess }
    }

    // P5-02
    @Test fun petsAreMultiSelectAndNeverEmpty() {
        add(cat, RecordType.FEEDING, today) { copy(portions = 1.0) }
        add(dog, RecordType.FEEDING, today) { copy(portions = 1.0) }
        show()
        waitFor("stats_summary_$dog")
        rule.onNodeWithTag("stats_pet_$cat").performClick() // all → only dog
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("stats_summary_$cat").assertDoesNotExist() }.isSuccess }
        rule.onNodeWithTag("stats_pet_$cat").assertIsNotSelected()
        rule.onNodeWithTag("stats_pet_$dog").assertIsSelected()
        rule.onNodeWithTag("stats_pet_$cat").performClick() // add cat back → both = all
        waitFor("stats_summary_$cat")
        rule.onNodeWithTag("stats_pet_$dog").performClick() // only cat
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("stats_summary_$dog").assertDoesNotExist() }.isSuccess }
        rule.onNodeWithTag("stats_pet_$cat").performClick() // can't deselect the last one → back to all
        waitFor("stats_summary_$dog")
        rule.onNodeWithTag("stats_pet_$cat").assertIsSelected()
    }

    @Test fun rangeIsSingleSelectAndTitleFollows() {
        show()
        rule.onNodeWithTag("range_MONTH").performClick()
        rule.onNodeWithTag("range_MONTH").assertIsSelected()
        rule.onNodeWithTag("range_WEEK").assertIsNotSelected()
        waitFor("stats_title")
        rule.onNodeWithTag("stats_title").assertTextEquals("2026 年 10 月")
        rule.onNodeWithTag("range_YEAR").performClick()
        rule.onNodeWithTag("stats_title").assertTextEquals("2026 年")
        rule.onNodeWithTag("stats_next").assertIsNotEnabled()
        rule.onNodeWithTag("stats_prev").performClick()
        rule.onNodeWithTag("stats_title").assertTextEquals("2025 年")
        rule.onNodeWithTag("stats_next").assertIsEnabled().performClick()
        rule.onNodeWithTag("stats_title").assertTextEquals("2026 年")
    }

    // P5-03 / P5-04 / P5-05: chart type follows the metric (all draw a chart card)
    @Test fun everyMetricShowsItsChartOrEmptyMessage() {
        add(cat, RecordType.WEIGHT, today) { copy(weightKg = 4.2) }
        add(cat, RecordType.VET, today) { copy(title = "健檢", cost = 1200) }
        show()
        listOf("WEIGHT", "COST", "VET").forEach { m ->
            rule.onNodeWithTag("metric_row").performScrollToNode(hasTestTag("metric_$m"))
            rule.onNodeWithTag("metric_$m").performClick()
            waitFor("stats_chart")
            rule.onNodeWithTag("stats_chart").assertIsDisplayed()
        }
        rule.onNodeWithTag("metric_row").performScrollToNode(hasTestTag("metric_COST"))
        rule.onNodeWithTag("metric_COST").performClick()
        waitFor("stats_headline")
        rule.onNodeWithTag("stats_headline").assertTextEquals("總花費 NT$ 1,200")
        rule.onNodeWithTag("metric_row").performScrollToNode(hasTestTag("metric_WALK"))
        rule.onNodeWithTag("metric_WALK").performClick()
        waitFor("stats_empty")
    }

    @Test fun weightHintWhenPetsDifferInSize() {
        add(cat, RecordType.WEIGHT, today) { copy(weightKg = 4.0) }
        add(dog, RecordType.WEIGHT, today) { copy(weightKg = 20.0) }
        show()
        rule.onNodeWithTag("metric_row").performScrollToNode(hasTestTag("metric_WEIGHT"))
        rule.onNodeWithTag("metric_WEIGHT").performClick()
        waitFor("stats_weight_hint")
    }

    // P5-07
    @Test fun emptyMetricExplainsInsteadOfBlankChart() {
        show()
        waitFor("stats_empty")
        rule.onNodeWithTag("stats_empty").assertTextContains("沒有「餵食」的紀錄", substring = true)
        rule.onNodeWithTag("stats_chart").assertDoesNotExist()
    }

    @Test fun noPetsShowsGuide() {
        runBlocking { pets.moveToTrash(cat); pets.moveToTrash(dog) }
        show()
        waitFor("stats_no_pets")
    }

    // P5-08
    @Test fun customRangeStartCannotPassEnd() {
        val vm = show()
        rule.onNodeWithTag("range_CUSTOM").performClick()
        rule.onNodeWithTag("stats_from").assertIsDisplayed()
        vm.setCustomRange(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12"))
        vm.setCustomFrom(LocalDate.parse("2026-10-15")) // later than the end → end follows
        rule.waitUntil(5_000) { vm.filters.value.customTo == LocalDate.parse("2026-10-15") }
        vm.setCustomTo(LocalDate.parse("2026-10-01")) // earlier than the start → start follows
        rule.waitUntil(5_000) { vm.filters.value.customFrom == LocalDate.parse("2026-10-01") }
    }
}
