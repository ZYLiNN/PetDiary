package com.zylinn.petdiary.sync

import kotlinx.coroutines.flow.StateFlow

/** What the family screen needs from the cloud; [CloudSync] is the real one, tests use a fake. */
interface FamilyController {
    val status: StateFlow<SyncStatus>
    val family: StateFlow<FamilyState?>
    suspend fun createInvite(): Invite?
    suspend fun joinFamily(text: String, nickname: String): FamilyResult
    suspend fun leaveFamily(): FamilyResult
    suspend fun removeMember(uid: String): FamilyResult
    suspend fun renameMe(name: String)
}
