package com.zylinn.petdiary.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinWatchTest {
    private val me = Member("me", "媽媽", isOwner = true, isMe = true)
    private val dad = Member("dad", "爸爸", isOwner = false, isMe = false)
    private val kid = Member("kid", "小美", isOwner = false, isMe = false)

    // D79: the owner hears about a new member, once
    @Test fun ownerIsToldAboutNewMembersOnly() {
        assertEquals(listOf(kid), JoinWatch.newcomers(setOf("me", "dad"), FamilyState("g", listOf(me, dad, kid))))
        assertTrue(JoinWatch.newcomers(setOf("me", "dad", "kid"), FamilyState("g", listOf(me, dad, kid))).isEmpty())
    }

    @Test fun firstLookAndNonOwnersStayQuiet() {
        assertTrue(JoinWatch.newcomers(null, FamilyState("g", listOf(me, dad))).isEmpty())
        val asMember = FamilyState("g", listOf(me.copy(isOwner = false), dad.copy(isOwner = true), kid))
        assertTrue(JoinWatch.newcomers(setOf("me", "dad"), asMember).isEmpty())
    }
}
