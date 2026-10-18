package com.zylinn.petdiary.ui.search

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class SearchScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), ZoneId.of("Asia/Taipei"))
    private val today = LocalDate.parse("2026-10-17")
    private lateinit var db: PetDiaryDatabase
    private lateinit var records: RecordRepository
    private lateinit var pets: PetRepository
    private lateinit var cat: String
    private lateinit var dog: String
    private var opened: Pair<String, LocalDate>? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), FileStore(File(context.cacheDir, "search-ui")), clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun add(pet: String, type: RecordType, day: LocalDate, title: String? = null) = runBlocking {
        records.addForPets(listOf(pet), RecordInput(type, day.toEpochDay(), 600, title = title)).single()
    }

    private fun show(initialPet: String? = null) {
        val vm = SearchViewModel(initialPet, pets, records, clock)
        rule.setContent {
            PetDiaryTheme { SearchScreen(vm = vm, onBack = {}, onOpenHit = { p, d -> opened = p to d }, today = today, autoFocus = false) }
        }
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    // P3-07 (idle)
    @Test fun idleShowsHintAndExamples() {
        show()
        rule.onNodeWithTag("search_idle").assertIsDisplayed()
        rule.onNodeWithTag("example_疫苗").assertIsDisplayed()
    }

    // P3-01, P3-04
    @Test fun fromHomeSearchesAllPetsNewestFirst() {
        val older = add(cat, RecordType.VACCINE, today.minusDays(100), "三合一")
        val newer = add(dog, RecordType.VACCINE, today.minusDays(3), "狂犬病")
        show()
        rule.onNodeWithTag("search_field").performTextInput("疫苗")
        waitFor("record_$newer")
        rule.onNodeWithTag("search_count").assertTextContains("共 2 筆")
        rule.onNodeWithTag("filter_pet_$cat").assertIsNotSelected()
        rule.onNodeWithText("疫苗・雷利").assertIsDisplayed()
        val top = rule.onNodeWithTag("record_$newer").getUnclippedBoundsInRoot().top
        val bottom = rule.onNodeWithTag("record_$older").getUnclippedBoundsInRoot().top
        assertTrue("newest first", top < bottom)
        rule.onNodeWithTag("group_${today.minusDays(3).toEpochDay()}").assertTextContains("10 月 14 日（三）・3 天前")
    }

    // P3-01: from a pet's page the pet is preselected and can be removed
    @Test fun fromPetPagePrefiltersThatPet() {
        val mine = add(cat, RecordType.VACCINE, today, "三合一")
        val other = add(dog, RecordType.VACCINE, today, "狂犬病")
        show(initialPet = cat)
        rule.onNodeWithTag("filter_pet_$cat").assertIsSelected()
        rule.onNodeWithTag("search_field").performTextInput("疫苗")
        waitFor("record_$mine")
        rule.onNodeWithTag("record_$other").assertDoesNotExist()

        rule.onNodeWithTag("filter_pet_$cat").performClick()
        waitFor("record_$other")
    }

    // P3-06
    @Test fun typeChipAloneSearches() {
        val worm = add(cat, RecordType.DEWORMING, today, "蚤不到")
        add(cat, RecordType.VACCINE, today, "三合一")
        show()
        rule.onNodeWithTag("filter_type_DEWORMING").performClick()
        waitFor("record_$worm")
        rule.onNodeWithTag("search_count").assertTextContains("共 1 筆")
    }

    @Test fun dateRangeFilter() {
        val recent = add(cat, RecordType.VACCINE, today.minusDays(10))
        val old = add(cat, RecordType.VACCINE, today.minusDays(60))
        show()
        rule.onNodeWithTag("search_field").performTextInput("疫苗")
        waitFor("record_$old")
        rule.onNodeWithTag("filter_range_DAYS_30").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_$old").assertDoesNotExist() }.isSuccess }
        rule.onNodeWithTag("record_$recent").assertExists()
    }

    // P3-07
    @Test fun noMatchShowsFriendlyMessage() {
        add(cat, RecordType.VACCINE, today)
        show()
        rule.onNodeWithTag("search_field").performTextInput("不存在的東西")
        waitFor("search_empty")
        rule.onNodeWithTag("search_empty").assertTextContains("找不到「不存在的東西」的紀錄", substring = true)
    }

    // P3-05
    @Test fun tappingResultOpensThatPetsDay() {
        val id = add(dog, RecordType.VET, today.minusDays(5), "健檢")
        show()
        rule.onNodeWithTag("search_field").performTextInput("健檢")
        waitFor("record_$id")
        rule.onNodeWithTag("record_$id").performClick()
        assertEquals(dog to today.minusDays(5), opened)
    }

    @Test fun exampleChipFillsQuery() {
        val id = add(cat, RecordType.GROOMING, today, "洗澡")
        show()
        rule.onNodeWithTag("example_洗澡").performClick()
        waitFor("record_$id")
    }
}
