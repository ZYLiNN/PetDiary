package com.zylinn.petdiary.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.data.settings.ThemeMode
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Taipei"))
    private lateinit var db: PetDiaryDatabase
    private lateinit var pets: PetRepository
    private lateinit var records: RecordRepository
    private lateinit var settings: AppSettings
    private lateinit var exportDir: File
    private var trash = 0
    private var supp = 0
    private var about = 0

    @Before fun setUp() {
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        val files = FileStore(File(context.cacheDir, "settings-ui").apply { deleteRecursively(); mkdirs() })
        exportDir = File(context.cacheDir, "settings-exports").apply { deleteRecursively() }
        records = RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock, toys = db.toyDao())
        pets = PetRepository(db.petDao(), clock)
        settings = AppSettings(context)
    }

    @After fun tearDown() {
        db.close()
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun showSettings(): SettingsViewModel {
        val vm = SettingsViewModel(settings, pets, records, exportDir, clock)
        rule.setContent {
            PetDiaryTheme {
                SettingsScreen(vm, "9.9.9", onOpenTrash = { trash++ }, onOpenSupplements = { supp++ }, onOpenAbout = { about++ })
            }
        }
        return vm
    }

    private fun waitFor(tag: String) =
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists() }.isSuccess }

    // P7: without a Firebase configuration the switch is shown but cannot be turned on
    @Test fun cloudSwitchIsDisabledWhenThisBuildHasNoCloud() {
        showSettings()
        rule.onNodeWithTag("cloud_status", useUnmergedTree = true).assertTextContains("尚未設定", substring = true)
        rule.onNodeWithTag("cloud_switch").assertIsNotEnabled()
    }

    // P6-05, P6-06, P6-04
    @Test fun settingsListsEveryEntry() {
        showSettings()
        rule.onNodeWithTag("settings_account").assertIsDisplayed()
        rule.onNodeWithTag("settings_account").assertTextContains("家庭群組", substring = true)
        rule.onNodeWithTag("settings_supplements").assertIsDisplayed().performClick()
        rule.onNodeWithTag("settings_export").assertIsDisplayed()
        rule.onNodeWithTag("settings_trash").assertIsDisplayed().performClick()
        rule.onNodeWithTag("list_Settings").performScrollToNode(hasTestTag("settings_about"))
        rule.onNodeWithTag("settings_about").assertIsDisplayed().assertTextContains("版本 9.9.9", substring = true)
        rule.onNodeWithTag("settings_about").performClick()
        assertEquals(1, trash); assertEquals(1, supp); assertEquals(1, about)
    }

    // P6-01
    @Test fun themeChoiceAppliesAndIsRemembered() {
        val vm = showSettings()
        rule.onNodeWithTag("theme_SYSTEM").assertIsSelected()
        rule.onNodeWithTag("theme_DARK").performClick()
        rule.onNodeWithTag("theme_DARK").assertIsSelected()
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        // a fresh AppSettings (= app restart) reads it back
        assertEquals(ThemeMode.DARK, AppSettings(context).themeMode.value)
        rule.onNodeWithTag("theme_LIGHT").performClick()
        assertEquals(ThemeMode.LIGHT, AppSettings(context).themeMode.value)
    }

    @Test fun badStoredThemeFallsBackToSystem() {
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().putString("theme_mode", "NEON").commit()
        assertEquals(ThemeMode.SYSTEM, AppSettings(context).themeMode.value)
    }

    // P6-03
    @Test fun exportWithoutRecordsSaysSo() {
        showSettings()
        rule.onNodeWithTag("settings_export").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("還沒有任何紀錄可以匯出").assertExists() }.isSuccess }
    }

    @Test fun exportWritesUtf8CsvWithBomOfLivingPetsOnly() = runBlocking {
        val cat = pets.add(PetInput(name = "雷米", species = Species.CAT, birthEpochDay = 1)).id
        val gone = pets.add(PetInput(name = "已刪除", species = Species.DOG, birthEpochDay = 1)).id
        val fish = records.addSupplement("魚油")
        records.addForPets(listOf(cat), RecordInput(RecordType.FEEDING, 20000, 480, portions = 1.5, supplementIds = listOf(fish.id), note = "吃光,很乖"))
        val trashed = records.addForPets(listOf(cat), RecordInput(RecordType.VET, 20001, 600, title = "刪掉的")).single()
        records.moveToTrash(trashed)
        records.addForPets(listOf(gone), RecordInput(RecordType.VET, 20002, 600, title = "別隻寵物"))
        pets.moveToTrash(gone)

        val vm = SettingsViewModel(settings, pets, records, exportDir, clock)
        vm.exportCsv()
        val ready = withTimeoutOrNull5 { vm.export.first { it is ExportState.Ready } } as ExportState.Ready
        assertEquals(1, ready.count)
        val bytes = ready.file.readBytes()
        assertEquals(0xEF, bytes[0].toInt() and 0xFF) // UTF-8 BOM
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        val text = ready.file.readText(Charsets.UTF_8)
        assertTrue(text.contains("雷米,餵食"))
        assertTrue(text.contains("魚油"))
        assertTrue(text.contains("\"吃光,很乖\""))
        assertFalse(text.contains("刪掉的"))
        assertFalse(text.contains("別隻寵物"))
        assertEquals("petdiary-2026-10-19.csv", ready.file.name)
    }

    private suspend fun <T> withTimeoutOrNull5(block: suspend () -> T): T? = kotlinx.coroutines.withTimeoutOrNull(5_000) { block() }

    // ---- supplements (P6-02) ----

    private fun showSupplements(): SupplementsViewModel {
        val vm = SupplementsViewModel(records)
        rule.setContent { PetDiaryTheme { SupplementsScreen(vm, onBack = {}) } }
        return vm
    }

    @Test fun addRenameAndDeleteSupplements() {
        showSupplements()
        rule.onNodeWithTag("supp_empty").assertIsDisplayed()
        rule.onNodeWithTag("supp_add_button").assertIsNotEnabled()
        rule.onNodeWithTag("supp_add_field").performTextInput("魚油")
        rule.onNodeWithTag("supp_add_button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("魚油").assertExists() }.isSuccess }
        val id = runBlocking { records.supplementsList.first().single().id }

        rule.onNodeWithTag("supp_edit_$id").performClick()
        rule.onNodeWithTag("supp_rename_field").performTextClearance()
        rule.onNodeWithTag("supp_rename_field").performTextInput("深海魚油")
        rule.onNodeWithTag("supp_rename_confirm").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("深海魚油").assertExists() }.isSuccess }
        assertEquals("深海魚油", runBlocking { records.supplementsList.first().single().name })

        rule.onNodeWithTag("supp_delete_$id").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("復原").assertExists() }.isSuccess }
        assertTrue(runBlocking { records.supplementsList.first() }.isEmpty())
        rule.onNodeWithText("復原").performClick()
        rule.waitUntil(5_000) { runBlocking { records.supplementsList.first() }.size == 1 }
    }

    // D77: the toy list uses the same screen with its own wording
    @Test fun toyListAddsToys() {
        val vm = SupplementsViewModel(records.toyOps)
        rule.setContent { PetDiaryTheme { SupplementsScreen(vm, onBack = {}, texts = ToyTexts) } }
        rule.onNodeWithText("玩具清單").assertIsDisplayed()
        rule.onNodeWithTag("supp_add_field").performTextInput("逗貓棒")
        rule.onNodeWithTag("supp_add_button").performClick()
        rule.waitUntil(5_000) { runBlocking { records.toysList.first() }.map { it.name } == listOf("逗貓棒") }
        assertTrue(runBlocking { records.supplementsList.first() }.isEmpty())
    }

    @Test fun duplicateNameIsRefused() {
        runBlocking { records.addSupplement("魚油"); records.addSupplement("益生菌") }
        showSupplements()
        rule.onNodeWithTag("supp_add_field").performTextInput("魚油")
        rule.onNodeWithTag("supp_add_button").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("supp_message").assertTextContains("已經有", substring = true) }.isSuccess }
        assertEquals(2, runBlocking { records.supplementsList.first() }.size)
    }

    @Test fun renamingToAnExistingNameIsRefused() {
        val ids = runBlocking { listOf(records.addSupplement("魚油"), records.addSupplement("益生菌")).map { it.id } }
        showSupplements()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("supp_edit_${ids[1]}").assertExists() }.isSuccess }
        rule.onNodeWithTag("supp_edit_${ids[1]}").performClick()
        rule.onNodeWithTag("supp_rename_field").performTextClearance()
        rule.onNodeWithTag("supp_rename_field").performTextInput("魚油")
        rule.onNodeWithTag("supp_rename_confirm").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("supp_message").assertExists() }.isSuccess }
        assertEquals("益生菌", runBlocking { records.supplementsList.first() }.last().name)
    }

    // deleted supplements keep their name on old records
    @Test fun deletedSupplementStillNamedOnOldRecords() = runBlocking {
        val s = records.addSupplement("魚油")
        records.deleteSupplement(s.id)
        assertTrue(records.supplementsList.first().isEmpty())
        assertEquals("魚油", records.supplementNames.first()[s.id])
    }

    // ---- about (P6-04) ----

    @Test fun aboutShowsVersionPrivacyAndLicenses() {
        rule.setContent { PetDiaryTheme { AboutScreen("1.2.3", onBack = {}) } }
        rule.onNodeWithTag("about_version").assertTextContains("1.2.3", substring = true)
        rule.onNodeWithTag("about_privacy").assertTextContains("只存在這支手機", substring = true)
        rule.onNodeWithText("Lucide", substring = true).assertExists()
    }
}
