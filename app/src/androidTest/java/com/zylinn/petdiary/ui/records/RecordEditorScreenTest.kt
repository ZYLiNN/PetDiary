package com.zylinn.petdiary.ui.records

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.data.photo.AttachmentImporter
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.DewormKind
import com.zylinn.petdiary.data.record.RecordInput
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.RecordType
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
class RecordEditorScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Taipei")
    private val clock = Clock.fixed(Instant.parse("2026-10-17T00:45:00Z"), zone) // 08:45 Taipei
    private val today = LocalDate.parse("2026-10-17")
    private lateinit var db: PetDiaryDatabase
    private lateinit var records: RecordRepository
    private lateinit var files: FileStore
    private lateinit var pets: PetRepository
    private lateinit var cat: String
    private lateinit var dog: String
    private var done = 0

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        files = FileStore(File(context.cacheDir, "editor-rec").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock)
        pets = PetRepository(db.petDao(), clock)
        cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        dog = pets.add(PetInput(name = "雷利", species = Species.DOG, birthEpochDay = 1)).id
    }

    @After fun tearDown() = db.close()

    private fun open(type: RecordType, petIds: List<String> = listOf(cat), date: LocalDate = today, recordId: String? = null) {
        val vm = RecordEditorViewModel(petIds, type, date, recordId, pets, records, clock, files, AttachmentImporter(context.contentResolver, files))
        rule.setContent { PetDiaryTheme { RecordEditorScreen(vm = vm, onDone = { done++ }) } }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("record_editor_scroll").assertExists() }.isSuccess }
    }

    private fun saveAndWait() {
        rule.onNodeWithTag("save_button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { done == 1 }
    }

    private fun day(petId: String, d: LocalDate = today) = runBlocking { records.observeDay(petId, d.toEpochDay()).first() }

    // P2-05b
    @Test fun dateAndTimeDefaultToChosenDayAndNow() {
        open(RecordType.WEIGHT, date = today.minusDays(3))
        rule.onNodeWithTag("field_date").assertTextContains("2026/10/14")
        rule.onNodeWithTag("field_time").assertTextContains("08:45")
        rule.onNodeWithTag("editor_title").assertTextContains("新增體重")
    }

    // D22: walk = minutes (required, with one-tap presets) + optional water checkbox
    @Test fun walkWithDurationPresetAndWater() {
        open(RecordType.WALK)
        rule.onNodeWithTag("save_button").assertIsNotEnabled()
        rule.onNodeWithTag("duration_30").performClick()
        rule.onNodeWithTag("duration_30").assertIsSelected()
        rule.onNodeWithTag("field_water").performClick()
        rule.onNodeWithTag("field_pee").performClick()
        rule.onNodeWithTag("field_poop").performClick()
        saveAndWait()

        val r = day(cat).single()
        assertEquals(true, r.peed)
        assertEquals(true, r.pooped)
        assertEquals(RecordType.WALK, r.type)
        assertEquals(30, r.durationMin)
        assertEquals(true, r.drankWater)
    }

    // P2-06, P2-06a
    @Test fun feedingWithInlineSupplement() {
        open(RecordType.FEEDING)
        rule.onNodeWithTag("save_button").assertIsNotEnabled()
        rule.onNodeWithTag("field_portions").performTextInput("1.5")
        rule.onNodeWithTag("field_water").performClick()
        rule.onNodeWithTag("supp_add").performScrollTo().performClick()
        rule.onNodeWithTag("supp_new_name").performTextInput("魚油")
        rule.onNodeWithTag("supp_new_confirm").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("supp_魚油").assertIsSelected() }.isSuccess }
        saveAndWait()

        val r = day(cat).single()
        assertEquals(1.5, r.portions!!, 0.0)
        assertEquals(true, r.drankWater)
        val suppId = runBlocking { records.supplementsList.first() }.single { it.name == "魚油" }.id
        assertEquals(listOf(suppId), r.supplementIdList)
    }

    @Test fun supplementAddedOnceShowsNextTime() = runBlocking {
        records.addSupplement("益生菌")
        open(RecordType.FEEDING)
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("supp_益生菌").assertExists() }.isSuccess }
        rule.onNodeWithTag("supp_益生菌").performScrollTo().assertIsDisplayed()
        Unit
    }

    // P2-07a: same layout for all medical types
    private fun assertMedicalLayout(t: RecordType) {
        open(t)
        listOf("field_title", "field_hospital", "field_cost", "field_next", "field_note").forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun medicineLayout() = assertMedicalLayout(RecordType.MEDICINE)
    @Test fun vaccineLayout() = assertMedicalLayout(RecordType.VACCINE)
    @Test fun dewormingLayout() = assertMedicalLayout(RecordType.DEWORMING)
    @Test fun vetLayout() = assertMedicalLayout(RecordType.VET)

    // P2-07, P2-08, P2-13
    @Test fun dewormingForTwoPetsSavesSameContent() {
        open(RecordType.DEWORMING, petIds = listOf(cat, dog))
        rule.onNodeWithText("雷米、雷利").assertIsDisplayed()
        rule.onNodeWithTag("vd_EXTERNAL").performClick()
        rule.onNodeWithTag("field_title").performTextInput("蚤不到")
        rule.onNodeWithTag("field_cost").performScrollTo().performTextInput("1,200")
        saveAndWait()

        listOf(cat, dog).forEach { pet ->
            val r = day(pet).single()
            assertEquals("蚤不到", r.title)
            assertEquals(1200L, r.cost)
            assertEquals(DewormKind.EXTERNAL, r.dewormKind)
            assertEquals(8 * 60 + 45, r.minuteOfDay)
        }
    }

    // P11-05: the shared 疫苗／驅蟲 form switches between vaccine and the two deworming kinds
    @Test fun vaccineTileCanBecomeInternalDeworming() {
        open(RecordType.VACCINE)
        rule.onNodeWithTag("vd_VACCINE").assertIsSelected()
        rule.onNodeWithTag("vd_INTERNAL").performClick()
        rule.onNodeWithTag("editor_title").assertTextContains("驅蟲", substring = true)
        rule.onNodeWithTag("field_title").performTextInput("寵愛")
        saveAndWait()
        val r = day(cat).single()
        assertEquals(RecordType.DEWORMING, r.type)
        assertEquals(DewormKind.INTERNAL, r.dewormKind)
    }

    @Test fun weightSaves() {
        open(RecordType.WEIGHT)
        rule.onNodeWithTag("field_weight").performTextInput("4.2")
        saveAndWait()
        assertEquals(4.2, day(cat).single().weightKg!!, 0.0)
    }

    @Test fun abnormalRequiresDescription() {
        open(RecordType.ABNORMAL)
        rule.onNodeWithTag("form_problem").assertTextContains("請描述狀況（例如：嘔吐）")
        rule.onNodeWithTag("field_title").performTextInput("吐毛球")
        saveAndWait()
        assertEquals("吐毛球", day(cat).single().title)
    }

    @Test fun groomingChips() {
        open(RecordType.GROOMING)
        rule.onNodeWithTag("groom_洗澡").performClick()
        rule.onNodeWithTag("groom_剪指甲").performClick()
        saveAndWait()
        assertEquals("洗澡、剪指甲", day(cat).single().title)
    }

    // P2-05 (edit)
    @Test fun editingExistingRecordKeepsIdAndUpdates() = runBlocking {
        val id = records.addForPets(listOf(cat), RecordInput(RecordType.VACCINE, today.toEpochDay(), 600, title = "三合一", cost = 900)).single()
        open(RecordType.VACCINE, recordId = id)
        rule.onNodeWithTag("editor_title").assertTextContains("編輯疫苗")
        rule.onNodeWithTag("field_title").performTextReplacement("五合一")
        saveAndWait()
        val r = records.get(id)!!
        assertEquals("五合一", r.title)
        assertEquals(900L, r.cost)
        assertEquals(1, day(cat).size)
    }

    @Test fun cancelAfterTypingAsksFirst() {
        open(RecordType.ABNORMAL)
        rule.onNodeWithTag("field_title").performTextInput("x")
        rule.onNodeWithTag("cancel_button").performClick()
        assertEquals(0, done)
        rule.onNodeWithTag("confirm_discard").performClick()
        assertEquals(1, done)
    }

    private fun pdfUri(name: String): android.net.Uri {
        val f = File(context.cacheDir, name).apply { writeBytes("%PDF-1.4 test".toByteArray()) }
        return android.net.Uri.fromFile(f)
    }

    // P2-15: attachments only on medical / abnormal records
    @Test fun attachmentsOnlyForMedicalAndAbnormal() {
        open(RecordType.FEEDING)
        rule.onNodeWithTag("attachments_section").assertDoesNotExist()
    }

    @Test fun abnormalHasAttachments() {
        open(RecordType.ABNORMAL)
        rule.onNodeWithTag("attachments_section").performScrollTo().assertIsDisplayed()
    }

    // P2-16, P2-19
    @Test fun multipleAttachmentsSavedAndOneRemovedLater() = runBlocking {
        val vm = RecordEditorViewModel(listOf(cat), RecordType.VET, today, null, pets, records, clock, files, AttachmentImporter(context.contentResolver, files))
        rule.setContent { PetDiaryTheme { RecordEditorScreen(vm = vm, onDone = { done++ }) } }
        rule.onNodeWithTag("field_title").performTextInput("嘔吐")
        vm.importAttachments(listOf(pdfUri("血檢.pdf"), pdfUri("處方.pdf")))
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("attachment_處方.pdf").assertExists() }.isSuccess }
        rule.onNodeWithTag("attachment_血檢.pdf").assertExists()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(5_000) { done == 1 }

        val id = day(cat).single().id
        val saved = records.attachmentsOf(id)
        assertEquals(setOf("血檢.pdf", "處方.pdf"), saved.map { it.displayName }.toSet())

        // Edit: remove one, keep the other.
        val vm2 = RecordEditorViewModel(listOf(cat), RecordType.VET, today, id, pets, records, clock, files, AttachmentImporter(context.contentResolver, files))
        val removed = saved.single { it.displayName == "血檢.pdf" }
        val kept = saved.single { it.displayName == "處方.pdf" }
        rule.waitUntil(5_000) { vm2.state.value.existing.size == 2 }
        vm2.removeAttachment(removed.id)
        vm2.save { done++ }
        rule.waitUntil(5_000) { done == 2 }

        assertEquals(listOf(kept.id), records.attachmentsOf(id).map { it.id })
        assertTrue(files.exists(kept.fileName))
        assertTrue(!files.exists(removed.fileName))
    }

    @Test fun discardDeletesPickedFiles() = runBlocking {
        val vm = RecordEditorViewModel(listOf(cat), RecordType.VET, today, null, pets, records, clock, files, AttachmentImporter(context.contentResolver, files))
        vm.importAttachments(listOf(pdfUri("x.pdf")))
        rule.waitUntil(5_000) { vm.state.value.added.size == 1 }
        val name = vm.state.value.added.single().fileName
        vm.discard()
        assertTrue(!files.exists(name))
    }
}
