package com.zylinn.petdiary.ui.detail

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.PanelTypes
import com.zylinn.petdiary.testutil.FitToScreen
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class AddRecordPanelTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-17T11:30:00Z"), zone) // 19:30 Taipei
    private val today = LocalDate.parse("2026-10-17")
    private lateinit var db: PetDiaryDatabase
    private lateinit var records: RecordRepository
    private lateinit var cat: String
    private lateinit var dog: String
    private var picked: Triple<RecordType, List<String>, LocalDate>? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), FileStore(File(context.cacheDir, "panel-test")), clock)
        val pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun show(width: Dp = 411.dp) {
        val vm = PetDetailViewModel(cat, PetRepository(db.petDao(), clock), records, FileStore(context.cacheDir), clock)
        rule.setContent {
            PetDiaryTheme {
                FitToScreen(width, 900.dp) {
                    PetDetailScreen(vm = vm, onBack = {}, onPickType = { t, p, d -> picked = Triple(t, p, d) }, onOpenRecord = { _, _, _ -> })
                }
            }
        }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_list").assertExists() }.isSuccess }
    }

    private fun openPanel() {
        rule.onNodeWithTag("fab_add_record").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("type_FEEDING").assertExists() }.isSuccess }
    }

    // P2-04, P2-05a
    @Test fun fabOpensPanelAndTypeTapGoesStraightToForm() {
        show()
        openPanel()
        PanelTypes.forEach { rule.onNodeWithTag("type_${it.name}").assertExists() }
        rule.onNodeWithTag("type_DEWORMING").assertDoesNotExist() // shares the 疫苗／驅蟲 tile (P11-05)
        rule.onNodeWithText("疫苗／驅蟲").assertExists()
        rule.onNodeWithTag("type_WEIGHT").performClick()
        assertEquals(Triple(RecordType.WEIGHT, listOf(cat), today), picked)
    }

    // P2-05f
    @Test fun currentPetPreselectedAndOthersCanBeAdded() {
        show()
        openPanel()
        rule.onNodeWithTag("pick_pet_$cat").assertIsSelected()
        rule.onNodeWithTag("pick_pet_$dog").performClick()
        rule.onNodeWithTag("pick_pet_$cat").performClick()
        rule.onNodeWithTag("pick_pet_$dog").performClick() // can't deselect the last one
        rule.onNodeWithTag("pick_pet_$dog").assertIsSelected()
        rule.onNodeWithTag("type_VACCINE").performClick()
        assertEquals(listOf(dog), picked!!.second)
    }

    // P2-05e
    @Test fun noQuickFeedingWithoutPreviousFeeding() {
        show()
        openPanel()
        rule.onNodeWithTag("quick_feeding").assertDoesNotExist()
    }

    // P2-05d
    @Test fun quickFeedingCopiesLastFeedingAtCurrentTime() = runBlocking {
        records.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today.minusDays(1).toEpochDay(), 8 * 60, portions = 1.5, drankWater = true, note = "乾乾"))
        show()
        openPanel()
        rule.onNodeWithText("餵食・同上次（主食 1.5 份・有喝水）").assertIsDisplayed()
        rule.onNodeWithTag("pick_pet_$dog").performClick()
        rule.onNodeWithTag("quick_feeding_save").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已記錄餵食").assertExists() }.isSuccess }

        listOf(cat, dog).forEach { pet ->
            val r = records.observeDay(pet, today.toEpochDay()).first().single()
            assertEquals(RecordType.FEEDING, r.type)
            assertEquals(1.5, r.portions!!, 0.0)
            assertEquals(true, r.drankWater)
            assertEquals("乾乾", r.note)
            assertEquals(19 * 60 + 30, r.minuteOfDay)
        }
    }

    @Test fun quickFeedingCanBeUndone() = runBlocking {
        records.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, today.minusDays(1).toEpochDay(), 8 * 60, portions = 1.0))
        show()
        openPanel()
        rule.onNodeWithTag("quick_feeding_save").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("復原").assertExists() }.isSuccess }
        rule.onNodeWithText("復原").performClick()
        rule.waitUntil(5_000) { runBlocking { records.observeDay(cat, today.toEpochDay()).first().isEmpty() } }
    }

    // D33: walk has its own "同上次"
    @Test fun quickWalkCopiesLastWalk() = runBlocking {
        records.addForPets(listOf(cat), RecordInput(RecordType.WALK, today.minusDays(1).toEpochDay(), 8 * 60, durationMin = 40, drankWater = true, peed = true))
        show()
        openPanel()
        rule.onNodeWithTag("quick_feeding").assertDoesNotExist()
        rule.onNodeWithText("散步・同上次（40 分鐘・有喝水・小便）").assertIsDisplayed()
        rule.onNodeWithTag("quick_walk_save").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("已記錄散步").assertExists() }.isSuccess }
        val r = records.observeDay(cat, today.toEpochDay()).first().single()
        assertEquals(40, r.durationMin)
        assertEquals(true, r.peed)
        assertEquals(19 * 60 + 30, r.minuteOfDay)
    }

    // P2-05c
    @Test fun wideScreenUsesDialog() {
        show(width = 840.dp)
        openPanel()
        rule.onNodeWithTag("add_record_dialog").assertIsDisplayed()
    }
}
