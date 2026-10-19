package com.zylinn.petdiary.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.sync.FamilyController
import com.zylinn.petdiary.sync.FamilyResult
import com.zylinn.petdiary.sync.FamilyState
import com.zylinn.petdiary.sync.Invite
import com.zylinn.petdiary.sync.InviteLink
import com.zylinn.petdiary.sync.Member
import com.zylinn.petdiary.sync.SyncStatus
import com.zylinn.petdiary.ui.theme.PetDiaryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakeFamily(status: SyncStatus, family: FamilyState?) : FamilyController {
    override val status = MutableStateFlow(status)
    override val family: StateFlow<FamilyState?> = MutableStateFlow(family)
    var joined: Pair<String, String>? = null
    var left = 0
    var removed: String? = null
    var invites = 0
    override suspend fun createInvite(): Invite {
        invites++
        return Invite(InviteLink("5f1c2a9e-1111-4222-8333-444455556666", "abcdefghijklmnopqrstuvwxyz"), System.currentTimeMillis() + 3_600_000)
    }
    override suspend fun joinFamily(text: String, nickname: String): FamilyResult { joined = text to nickname; return FamilyResult.Ok }
    override suspend fun leaveFamily(): FamilyResult { left++; return FamilyResult.Ok }
    override suspend fun removeMember(uid: String): FamilyResult { removed = uid; return FamilyResult.Ok }
    override suspend fun renameMe(name: String) {}
}

@RunWith(AndroidJUnit4::class)
class FamilyScreenTest {

    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var settings: AppSettings

    @Before fun setUp() {
        context.getSharedPreferences("petdiary_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        settings = AppSettings(context)
    }

    private fun show(fake: FakeFamily) {
        rule.setContent { PetDiaryTheme { FamilyScreen(FamilyViewModel(fake, settings), onBack = {}) } }
    }

    private val alone = FamilyState("g1", listOf(Member("me", "我", isOwner = true, isMe = true)))
    private val owner = FamilyState("g1", listOf(Member("me", "媽媽", true, true), Member("bob", "爸爸", false, false)))
    private val member = FamilyState("g1", listOf(Member("mom", "媽媽", true, false), Member("me", "爸爸", false, true)))

    @Test fun linkOpenedFromOutsideAsksToJoinAndConsumesIt() {
        val fake = FakeFamily(SyncStatus.Active(null), alone)
        val link = "petdiary://join?g=abcdef123456&c=abcdefghijklmnopqrstuvwxyz"
        var consumed = 0
        rule.setContent { PetDiaryTheme { FamilyScreen(FamilyViewModel(fake, settings), onBack = {}, incoming = link, onIncomingConsumed = { consumed++ }) } }
        rule.onNodeWithText("加入家庭群組？").assertIsDisplayed()
        rule.onNodeWithTag("join_ok").performClick()
        rule.waitUntil(5_000) { fake.joined != null }
        assertEquals(link, fake.joined?.first)
        assertEquals(1, consumed)
    }

    /** The system camera opens the QR's `petdiary://join` link; without an intent filter Android says "no app can open this". */
    @Test fun inviteLinkResolvesToThisApp() {
        val link = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("petdiary://join?g=abcdef123456&c=abcdefghijklmnopqrstuvwxyz"))
            .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val resolved = context.packageManager.resolveActivity(link, 0)
        assertEquals(context.packageName, resolved?.activityInfo?.packageName)
    }

    @Test fun needsCloudFirst() {
        show(FakeFamily(SyncStatus.Off, null))
        rule.onNodeWithTag("family_need_cloud").assertIsDisplayed().assertTextContains("雲端備份與同步", substring = true)
    }

    @Test fun ownerCreatesAnInviteAndSeesQrAndExpiry() {
        val fake = FakeFamily(SyncStatus.Active(null), alone)
        show(fake)
        rule.onNodeWithTag("create_invite").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("invite_qr").assertExists() }.isSuccess }
        rule.onNodeWithTag("invite_expiry").assertTextContains("只能使用一次", substring = true)
        assertEquals(1, fake.invites)
        rule.onNodeWithTag("hide_invite").performClick()
        rule.onNodeWithTag("create_invite").assertIsDisplayed()
    }

    @Test fun pastingAnInviteAsksForANameThenJoins() {
        val fake = FakeFamily(SyncStatus.Active(null), alone)
        show(fake)
        rule.onNodeWithTag("paste_invite").performClick()
        val text = InviteLink("5f1c2a9e-1111-4222-8333-444455556666", "abcdefghijklmnopqrstuvwxyz").asText()
        rule.onNodeWithTag("paste_field").performTextInput(text)
        rule.onNodeWithTag("paste_ok").performClick()
        rule.onNodeWithTag("join_name").performTextInput("爸爸")
        rule.onNodeWithTag("join_ok").performClick()
        rule.waitUntil(5_000) { fake.joined != null }
        assertEquals(text to "爸爸", fake.joined)
    }

    @Test fun ownerWithFamilyCanRemoveAMemberAfterConfirming() {
        val fake = FakeFamily(SyncStatus.Active(null), owner)
        show(fake)
        rule.onNodeWithTag("member_bob").assertTextContains("爸爸", substring = true)
        rule.onNodeWithTag("remove_bob").performClick()
        rule.onNodeWithText("移除「爸爸」？").assertIsDisplayed()
        rule.onNodeWithTag("remove_ok").performClick()
        rule.waitUntil(5_000) { fake.removed != null }
        assertEquals("bob", fake.removed)
        // a shared group has no "join another group" section
        rule.onNodeWithTag("scan_invite").assertDoesNotExist()
    }

    @Test fun memberCanLeaveAfterConfirming_andCannotInviteOrRemove() {
        val fake = FakeFamily(SyncStatus.Active(null), member)
        show(fake)
        rule.onNodeWithTag("create_invite").assertDoesNotExist()
        rule.onNodeWithTag("remove_mom").assertDoesNotExist()
        rule.onNodeWithTag("leave_group").performClick()
        rule.onNodeWithTag("leave_ok").performClick()
        rule.waitUntil(5_000) { fake.left == 1 }
    }
}

