package com.zylinn.petdiary.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyAssemblerTest {
    private val dad = Member("dad", "爸爸", isOwner = false, isMe = false)

    // The group document usually answers before the member list. Publishing the owner alone made the owner's phone
    // forget everyone and announce them all again on the next restart of sync.
    @Test fun waitsForBothTheGroupAndTheMemberList() {
        val a = FamilyAssembler("g", myUid = "me")
        assertNull(a.group("me", "媽媽"))
        val state = a.members(listOf(dad))!!
        assertEquals(listOf("me", "dad"), state.members.map { it.uid })
        assertEquals(true, state.iAmOwner)
    }

    @Test fun membersFirstThenGroupAlsoWorks() {
        val a = FamilyAssembler("g", myUid = "dad")
        assertNull(a.members(listOf(dad)))
        val state = a.group("me", "媽媽")!!
        assertEquals(listOf(false, true), state.members.map { it.isMe })
    }

    @Test fun laterChangesPublishRightAway() {
        val a = FamilyAssembler("g", myUid = "me")
        a.group("me", "媽媽"); a.members(listOf(dad))
        assertEquals(1, a.members(emptyList())!!.members.size)
        assertEquals("新名字", a.group("me", "新名字")!!.members.first().name)
    }
}
