package com.zylinn.petdiary.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.Duration

data class Member(val uid: String, val name: String, val isOwner: Boolean, val isMe: Boolean)

/** Who is in the group I'm syncing with. */
data class FamilyState(val groupId: String, val members: List<Member>) {
    val iAmOwner get() = members.any { it.isMe && it.isOwner }
    /** More than one person: the UI starts showing who recorded what. */
    val isShared get() = members.size > 1
}

data class Invite(val link: InviteLink, val expiresAt: Long)

sealed interface FamilyResult {
    data object Ok : FamilyResult
    data class Failed(val message: String) : FamilyResult
}

/** Invitations and membership on Firestore (P8). All rules are enforced server-side by firestore.rules. */
class FamilyService(private val db: FirebaseFirestore, private val clock: Clock) {

    companion object {
        val INVITE_VALID_FOR: Duration = Duration.ofHours(24)
        const val NAME_MAX = 30
    }

    private fun group(gid: String) = db.collection("groups").document(gid)

    /** Watches the group document and its members; [onDenied] fires when I'm no longer allowed to read the group. */
    fun observe(groupId: String, myUid: String, onState: (FamilyState) -> Unit, onDenied: () -> Unit): AutoCloseable {
        var owner: Triple<String, String, Boolean>? = null // ownerUid, ownerName, loaded
        var others: List<Member> = emptyList()
        fun publish() {
            val o = owner ?: return
            val ownerMember = Member(o.first, o.second, isOwner = true, isMe = o.first == myUid)
            onState(FamilyState(groupId, listOf(ownerMember) + others.map { it.copy(isMe = it.uid == myUid) }))
        }
        fun denied(e: Exception) {
            if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) onDenied()
        }
        val r1 = group(groupId).addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener denied(e)
            val ownerUid = snap?.getString("ownerUid") ?: return@addSnapshotListener
            owner = Triple(ownerUid, snap.getString("ownerName").orEmpty(), true)
            publish()
        }
        val r2 = group(groupId).collection("members").addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener denied(e)
            others = snap?.documents.orEmpty().map { d ->
                Member(d.id, d.getString("name").orEmpty(), isOwner = false, isMe = d.id == myUid)
            }.sortedBy { it.uid }
            publish()
        }
        return AutoCloseable { r1.remove(); r2.remove() }
    }

    /** Owner only: a fresh one-time invitation valid for [INVITE_VALID_FOR]. */
    suspend fun createInvite(groupId: String): Invite {
        val link = InviteLink(groupId, InviteLink.newCode())
        val expires = clock.millis() + INVITE_VALID_FOR.toMillis()
        group(groupId).collection("invites").document(link.code).set(
            mapOf("createdAt" to FieldValue.serverTimestamp(), "expiresAt" to Timestamp(expires / 1000, 0), "used" to false),
        ).await()
        return Invite(link, expires)
    }

    /** Checks the invitation first (so the error can be specific), then marks it used and joins in one batch. */
    suspend fun join(link: InviteLink, myUid: String, name: String): FamilyResult {
        val ref = group(link.groupId).collection("invites").document(link.code)
        val invite = try {
            ref.get().await()
        } catch (e: FirebaseFirestoreException) {
            return FamilyResult.Failed("找不到這個邀請，請確認連結是否完整")
        }
        if (!invite.exists()) return FamilyResult.Failed("找不到這個邀請，可能已被收回")
        if (invite.getBoolean("used") == true) return FamilyResult.Failed("這個邀請已經被使用過了，請對方重新產生一個")
        val expires = invite.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        if (expires <= clock.millis()) return FamilyResult.Failed("這個邀請已經過期，請對方重新產生一個")
        return try {
            db.batch()
                .update(ref, mapOf("used" to true, "usedBy" to myUid))
                .set(
                    group(link.groupId).collection("members").document(myUid),
                    mapOf("name" to name.take(NAME_MAX), "role" to "member", "joinedAt" to FieldValue.serverTimestamp(), "inviteCode" to link.code),
                )
                .commit().await()
            FamilyResult.Ok
        } catch (e: FirebaseFirestoreException) {
            FamilyResult.Failed("加入失敗（邀請可能剛被別人使用，或已過期）")
        }
    }

    suspend fun removeMember(groupId: String, uid: String) {
        group(groupId).collection("members").document(uid).delete().await()
    }

    suspend fun renameMember(groupId: String, uid: String, name: String) {
        group(groupId).collection("members").document(uid).update("name", name.take(NAME_MAX)).await()
    }

    suspend fun renameOwner(groupId: String, name: String) {
        group(groupId).update("ownerName", name.take(NAME_MAX)).await()
    }
}
