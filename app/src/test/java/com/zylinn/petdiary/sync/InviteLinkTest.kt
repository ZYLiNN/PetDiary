package com.zylinn.petdiary.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class InviteLinkTest {

    private val code = "abcdefghijklmnopqrstuvwxyz"
    private val gid = "5f1c2a9e-1111-4222-8333-444455556666"

    @Test fun linkRoundTrips() {
        val link = InviteLink(gid, code)
        assertEquals(link, InviteLink.parse(link.asText()))
    }

    @Test fun findsTheLinkInsideAShareMessage() {
        val text = "一起用 PetDiary 記錄毛孩！貼上這段連結：\n${InviteLink(gid, code).asText()}\n謝謝"
        assertEquals(InviteLink(gid, code), InviteLink.parse(text))
    }

    @Test fun rejectsAnythingElse() {
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("https://example.com/join?g=$gid&c=$code"))
        assertNull(InviteLink.parse("petdiary://join?g=$gid"))
        assertNull(InviteLink.parse("petdiary://join?c=$code"))
        assertNull(InviteLink.parse("petdiary://join?g=$gid&c=short"))
        assertNull(InviteLink.parse("petdiary://join?g=../../x&c=$code"))
        assertNull(InviteLink.parse("petdiary://join?g=$gid&c=${code.uppercase()}")) // codes are lower-case base32
    }

    @Test fun newCodesAreLongRandomAndAcceptedByTheParser() {
        val a = InviteLink.newCode(); val b = InviteLink.newCode()
        assertEquals(26, a.length)
        assertNotEquals(a, b)
        assertTrue(a.all { it in "abcdefghijklmnopqrstuvwxyz234567" })
        assertEquals(InviteLink(gid, a), InviteLink.parse(InviteLink(gid, a).asText()))
        assertEquals(26, InviteLink.newCode(SecureRandom()).length)
    }

    // P13 (D79): a normal https link chat apps can click; the code is after # so it never reaches the web server
    @Test fun shareLinkIsHttpsWithTheCodeAfterTheHash() {
        val text = InviteLink(gid, code).asText()
        assertEquals("https://petdiary-878a6.web.app/join?openExternalBrowser=1#g=$gid&c=$code", text)
        assertTrue(!text.substringBefore('#').contains(code))
    }

    @Test fun olderAppLinksStillWork() {
        assertEquals(InviteLink(gid, code), InviteLink.parse("petdiary://join?g=$gid&c=$code"))
    }

    @Test fun webLinkFromAnotherHostOrPathIsRefused() {
        assertNull(InviteLink.parse("https://evil.example/join#g=$gid&c=$code"))
        assertNull(InviteLink.parse("https://petdiary-878a6.web.app/joinx#g=$gid&c=$code"))
        assertNull(InviteLink.parse("https://petdiary-878a6.web.app/join?g=$gid&c=$code")) // must be after #
        assertEquals(InviteLink(gid, code), InviteLink.parse("https://petdiary-878a6.web.app/join#g=$gid&c=$code"))
    }
}
