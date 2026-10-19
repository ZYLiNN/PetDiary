package com.zylinn.petdiary.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.sync.AccountController
import com.zylinn.petdiary.sync.AccountResult
import com.zylinn.petdiary.sync.AccountState
import com.zylinn.petdiary.sync.CloudSwitch
import com.zylinn.petdiary.sync.SyncStatus
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

private class FakeCloudSwitch(status: SyncStatus) : CloudSwitch {
    override val status = MutableStateFlow(status)
    override fun enable() {}
    override fun disable() {}
}

private class FakeAccounts(account: AccountState?, var linkResult: AccountResult = AccountResult.Ok("已綁定 Google 帳號")) : AccountController {
    override val account: StateFlow<AccountState?> = MutableStateFlow(account)
    var linked = 0
    var switchedTo: String? = null
    var deleted = 0
    override suspend fun linkGoogle(host: Context): AccountResult { linked++; return linkResult }
    override suspend fun switchToGoogle(idToken: String): AccountResult { switchedTo = idToken; return AccountResult.Ok("已改用") }
    override suspend fun deleteCloudAccount(host: Context): AccountResult { deleted++; return AccountResult.Ok("已刪除雲端資料與帳號") }
}

@RunWith(AndroidJUnit4::class)
class AccountSettingsTest {

    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T02:30:00Z"), ZoneId.of("Asia/Taipei"))
    private lateinit var db: PetDiaryDatabase
    private lateinit var settings: AppSettings

    @Before fun setUp() {
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, PetDiaryDatabase::class.java).build()
        settings = AppSettings(context)
    }

    @After fun tearDown() {
        rule.waitForIdle()
        db.close()
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun show(cloud: CloudSwitch, accounts: AccountController) {
        val files = FileStore(File(context.cacheDir, "acct-ui").apply { mkdirs() })
        val vm = SettingsViewModel(
            settings, PetRepository(db.petDao(), clock), RecordRepository(db.recordDao(), db.attachmentDao(), db.supplementDao(), files, clock),
            File(context.cacheDir, "acct-exports"), clock, cloud, accounts,
        )
        rule.setContent { PetDiaryTheme { SettingsScreen(vm, "9.9.9", onOpenTrash = {}, onOpenSupplements = {}, onOpenAbout = {}) } }
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("list_Settings").performScrollToNode(hasTestTag(tag))

    @Test fun googleRowAsksToTurnOnCloudFirst() {
        show(FakeCloudSwitch(SyncStatus.Off), FakeAccounts(null))
        rule.onNodeWithTag("google_status", useUnmergedTree = true).assertTextContains("先開啟雲端備份與同步", substring = true)
        rule.onNodeWithTag("link_google").assertDoesNotExist()
        rule.onNodeWithTag("settings_delete_cloud").assertDoesNotExist()
    }

    @Test fun anonymousUserCanLinkGoogle() {
        val accounts = FakeAccounts(AccountState(null))
        show(FakeCloudSwitch(SyncStatus.Active(null)), accounts)
        rule.onNodeWithTag("link_google").performClick()
        rule.waitUntil(5_000) { accounts.linked == 1 }
        rule.onNodeWithText("已綁定 Google 帳號").assertIsDisplayed() // the snackbar
    }

    @Test fun linkedAccountShowsTheEmailAndNoLinkButton() {
        show(FakeCloudSwitch(SyncStatus.Active(null)), FakeAccounts(AccountState("mom@example.com")))
        rule.onNodeWithTag("google_status", useUnmergedTree = true).assertTextContains("mom@example.com", substring = true)
        rule.onNodeWithTag("link_google").assertDoesNotExist()
    }

    @Test fun anAlreadyUsedGoogleAccountOffersToSwitchBeforeDoingAnything() {
        val accounts = FakeAccounts(AccountState(null), AccountResult.NeedsSwitch("token-123", "mom@example.com"))
        show(FakeCloudSwitch(SyncStatus.Active(null)), accounts)
        rule.onNodeWithTag("link_google").performClick()
        rule.onNodeWithText("這個 Google 帳號已經在使用中").assertIsDisplayed()
        assertEquals(null, accounts.switchedTo)
        rule.onNodeWithTag("switch_cancel").performClick()
        assertEquals(null, accounts.switchedTo)

        rule.onNodeWithTag("link_google").performClick()
        rule.onNodeWithTag("switch_ok").performClick()
        rule.waitUntil(5_000) { accounts.switchedTo != null }
        assertEquals("token-123", accounts.switchedTo)
    }

    @Test fun deletingTheCloudAccountNeedsConfirmationAndKeepsLocalDataPromise() {
        val accounts = FakeAccounts(AccountState(null))
        show(FakeCloudSwitch(SyncStatus.Active(null)), accounts)
        scrollTo("settings_delete_cloud")
        rule.onNodeWithTag("settings_delete_cloud").performClick()
        rule.onNodeWithText("刪除雲端資料與帳號？").assertIsDisplayed()
        rule.onNodeWithText("你手機上的資料不會被刪除", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("delete_cloud_cancel").performClick()
        assertEquals(0, accounts.deleted)

        rule.onNodeWithTag("settings_delete_cloud").performClick()
        rule.onNodeWithTag("delete_cloud_ok").performClick()
        rule.waitUntil(5_000) { accounts.deleted == 1 }
    }
}
