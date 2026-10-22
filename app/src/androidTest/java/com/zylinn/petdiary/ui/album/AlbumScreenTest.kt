package com.zylinn.petdiary.ui.album

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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

/** P13 (D80): the album tab collects every photo / PDF from every record. */
@RunWith(AndroidJUnit4::class)
class AlbumScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneId.of("Asia/Taipei"))
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var files: FileStore
    private lateinit var remi: String
    private lateinit var lily: String
    private var opened: Pair<String, LocalDate>? = null

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        files = FileStore(File(context.cacheDir, "album-ui").apply { deleteRecursively(); mkdirs() })
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock)
        pets = PetRepository(db.petDao(), clock)
        remi = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        lily = pets.add(PetInput(name = "雷利", species = Species.CAT, birthEpochDay = 1)).id
    }

    @After fun tearDown() {
        rule.waitForIdle()
        db.close()
    }

    /** A record with one attachment; [onPhone] false = synced from the cloud but not downloaded yet. */
    private fun attach(pet: String, type: RecordType, day: String, name: String, pdf: Boolean = false, onPhone: Boolean = true): String = runBlocking {
        val fileName = "$name.${if (pdf) "pdf" else "jpg"}"
        if (onPhone) files.file(fileName).writeBytes(ByteArray(64) { it.toByte() })
        records.addForPets(
            listOf(pet), RecordInput(type, LocalDate.parse(day).toEpochDay(), 600, title = "t", durationMin = 10, portions = 1.0),
            listOf(NewAttachment(fileName, if (pdf) "application/pdf" else "image/jpeg", fileName, 64)),
        )
        fileName
    }

    private fun entryId(fileName: String) = runBlocking {
        db.attachmentDao().observeAlbum().first().single { it.fileName == fileName }.id
    }

    private fun show() {
        val vm = AlbumViewModel(pets, records, files)
        rule.setContent { PetDiaryTheme { AlbumScreen(vm, onOpenRecordDay = { p, d -> opened = p to d }, onOpenSettings = {}) } }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("album_tab_PHOTOS").assertExists() }.isSuccess }
    }

    private fun waitFor(tag: String) = rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag).assertExists() }.isSuccess }

    @Test fun emptyAlbumExplainsWherePhotosComeFrom() {
        show()
        waitFor("album_empty")
        rule.onNodeWithTag("album_empty").assertTextContains("拍照", substring = true)
    }

    @Test fun photosFromEveryTypeAndPetAreShownAndCanBeFilteredByPet() {
        val walk = attach(remi, RecordType.WALK, "2026-10-20", "walk")
        val vet = attach(lily, RecordType.VET, "2026-09-02", "vet")
        show()
        waitFor("album_photo_${entryId(walk)}")
        rule.onNodeWithTag("album_photo_${entryId(vet)}").assertExists()
        rule.onNodeWithText("2026 年 10 月").assertIsDisplayed()
        rule.onNodeWithTag("album_tab_PHOTOS").assertTextContains("照片 2", substring = true)
        rule.onNodeWithTag("album_pet_$remi").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("album_photo_${entryId(vet)}").assertDoesNotExist() }.isSuccess }
        rule.onNodeWithTag("album_photo_${entryId(walk)}").assertExists()
        rule.onNodeWithTag("album_pet_all").performClick().assertIsSelected()
    }

    @Test fun pdfsAreListedUnderFiles() {
        attach(remi, RecordType.WALK, "2026-10-20", "walk")
        val report = attach(remi, RecordType.VET, "2026-10-19", "report", pdf = true)
        show()
        rule.onNodeWithTag("album_tab_FILES").assertTextContains("檔案 1", substring = true).performClick()
        waitFor("album_file_${entryId(report)}")
        rule.onNodeWithTag("album_file_${entryId(report)}").assertTextContains("report.pdf", substring = true)
    }

    @Test fun tappingAPhotoShowsItWithAWayBackToTheRecord() {
        val walk = attach(remi, RecordType.WALK, "2026-10-20", "walk")
        show()
        waitFor("album_photo_${entryId(walk)}")
        rule.onNodeWithTag("album_photo_${entryId(walk)}").performClick()
        rule.onNodeWithTag("album_viewer").assertIsDisplayed()
        rule.onNodeWithTag("viewer_save").assertExists()
        rule.onNodeWithTag("viewer_open_record").performClick()
        assertEquals(remi to LocalDate.parse("2026-10-20"), opened)
    }

    // P14-02: swipe left / right through the photos of the current filter; "看紀錄" follows the photo on screen
    @Test fun viewerSwipesThroughPhotos() {
        val newer = attach(remi, RecordType.WALK, "2026-10-20", "newer")
        attach(lily, RecordType.VET, "2026-10-18", "older")
        attach(remi, RecordType.VET, "2026-10-19", "report", pdf = true)
        show()
        waitFor("album_photo_${entryId(newer)}")
        rule.onNodeWithTag("album_photo_${entryId(newer)}").performClick()
        rule.onNodeWithTag("viewer_counter").assertTextEquals("1 / 2")
        rule.onNodeWithTag("viewer_pager").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("viewer_counter").assertTextEquals("2 / 2") }.isSuccess }
        rule.onNodeWithText("雷利・就診", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("viewer_open_record").performClick()
        assertEquals(lily to LocalDate.parse("2026-10-18"), opened)
    }

    @Test fun recordsInTheTrashAreLeftOut() {
        val walk = attach(remi, RecordType.WALK, "2026-10-20", "walk")
        val id = entryId(walk)
        runBlocking { records.moveToTrash(db.attachmentDao().observeAlbum().first().single().recordId) }
        show()
        waitFor("album_empty")
        rule.onNodeWithTag("album_photo_$id").assertDoesNotExist()
    }

    @Test fun aPhotoStillDownloadingShowsAPlaceholder() {
        val synced = attach(lily, RecordType.FEEDING, "2026-10-20", "fromdad", onPhone = false)
        show()
        waitFor("album_photo_${entryId(synced)}")
        rule.onNodeWithTag("album_photo_${entryId(synced)}").assertExists()
        rule.onNodeWithContentDescription("下載中", useUnmergedTree = true).assertExists()
    }
}
