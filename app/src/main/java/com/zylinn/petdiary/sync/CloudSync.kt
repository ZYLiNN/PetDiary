package com.zylinn.petdiary.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.util.UUID

sealed interface SyncStatus {
    /** The user has not turned cloud sync on. */
    data object Off : SyncStatus
    /** This build has no Firebase configuration (google-services.json missing). */
    data object Unavailable : SyncStatus
    data object Connecting : SyncStatus
    data class Active(val lastSyncedAt: Long?) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/**
 * Opt-in cloud backup and sync. Turning it on signs in anonymously (no account, no email), finds or creates
 * the group this user belongs to, then runs [SyncEngine] (data) and [FileSync] (photos / PDFs) for it.
 * Turning it off only stops syncing; nothing is deleted. The family features (P8) live here too because joining or
 * leaving a group means restarting the sync against another group.
 */
@OptIn(FlowPreview::class)
class CloudSync(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val database: PetDiaryDatabase,
    private val photos: FileStore,
    private val attachments: FileStore,
    private val clock: Clock,
    private val deletes: DeleteQueue? = null,
) : FamilyController, CloudSwitch {
    private val _status = MutableStateFlow<SyncStatus>(if (settings.cloudSyncEnabled.value) SyncStatus.Connecting else SyncStatus.Off)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _family = MutableStateFlow<FamilyState?>(null)
    /** Members of the group I'm syncing with; null while off / connecting. */
    override val family: StateFlow<FamilyState?> = _family.asStateFlow()

    private val _uid = MutableStateFlow<String?>(null)
    /** Names to show next to records, only when the group has more than one person. */
    val authorNames: StateFlow<Map<String, String>> get() = _authorNames
    private val _authorNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private var job: Job? = null

    private val familyService by lazy { FamilyService(FirebaseFirestore.getInstance(), clock) }

    /** The signed-in cloud user, or null when sync is off. Used to stamp who recorded something. */
    fun currentUid(): String? = if (settings.cloudSyncEnabled.value) _uid.value ?: authOrNull()?.currentUser?.uid else null

    private fun authOrNull(): FirebaseAuth? = if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()

    /** Called once at app start. */
    fun start() {
        if (settings.cloudSyncEnabled.value) connect()
    }

    override fun enable() {
        settings.setCloudSyncEnabled(true)
        connect()
    }

    override fun disable() {
        settings.setCloudSyncEnabled(false)
        job?.cancel()
        job = null
        _family.value = null
        _authorNames.value = emptyMap()
        _status.value = SyncStatus.Off
    }

    private fun connect() {
        job?.cancel()
        if (FirebaseApp.getApps(context).isEmpty()) {
            _status.value = SyncStatus.Unavailable
            return
        }
        _status.value = SyncStatus.Connecting
        job = scope.launch {
            var wait = 5_000L
            while (true) {
                try {
                    run()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName)
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(5 * 60_000L)
                }
            }
        }
    }

    private suspend fun run() = coroutineScope {
        val auth = FirebaseAuth.getInstance()
        val uid = (auth.currentUser ?: auth.signInAnonymously().await().user)!!.uid
        _uid.value = uid
        val db = FirebaseFirestore.getInstance()
        val groupId = ensureGroup(db, uid)

        val engine = SyncEngine(
            database.syncDao(), FirestoreRemote(db, groupId), clock,
            onError = { e -> _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName) },
            inTransaction = { block -> database.withTransaction { block() } },
        )
        val sharedPrefs = context.getSharedPreferences("petdiary_sync", Context.MODE_PRIVATE)
        val log = object : UploadLog {
            private val key = "uploaded_$groupId"
            override fun has(name: String) = sharedPrefs.getStringSet(key, emptySet())!!.contains(name)
            override fun add(name: String) {
                sharedPrefs.edit().putStringSet(key, sharedPrefs.getStringSet(key, emptySet())!! + name).apply()
            }
        }
        val files = FileSync(FirebaseStorage.getInstance(), groupId, database.syncDao(), photos, attachments, log, deletes)

        val observer = familyService.observe(groupId, uid, onState = ::publishFamily, onDenied = { scope.launch { dropGroup(uid) } })
        try {
            val engineJob = engine.start(this)
            launch { // keep photos / PDFs in step: after data changes, and every minute to retry what was not ready
                merge(
                    database.syncDao().changeSignal().debounce(800).map { },
                    engine.lastSyncedAt.map { },
                    flow { while (true) { emit(Unit); delay(60_000) } },
                ).conflate().collect {
                    files.syncOnce()?.let { e -> _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName) }
                }
            }
            launch { engine.lastSyncedAt.collect { t -> if (_status.value !is SyncStatus.Off) _status.value = SyncStatus.Active(t) } }
            _status.value = SyncStatus.Active(null)
            engineJob.join()
        } finally {
            observer.close()
        }
    }

    private fun publishFamily(state: FamilyState) {
        val mine = settings.displayName.value
        val named = state.copy(members = state.members.map { m ->
            if (m.name.isNotBlank()) m else m.copy(name = if (m.isMe) mine.ifBlank { "我" } else if (m.isOwner) "擁有者" else "家人")
        })
        _family.value = named
        _authorNames.value = if (named.isShared) named.members.associate { it.uid to if (it.isMe) "我" else it.name } else emptyMap()
    }

    /** Finds the user's group via users/{uid}; the very first time, creates a private one (the owner is the only member). */
    private suspend fun ensureGroup(db: FirebaseFirestore, uid: String): String {
        val pointer = db.collection("users").document(uid)
        pointer.get().await().getString("groupId")?.let { return it }
        val groupId = UUID.randomUUID().toString()
        val now = clock.millis()
        val doc = mutableMapOf<String, Any>("ownerUid" to uid, "memberUids" to listOf(uid), "createdAt" to now, "updatedAt" to now)
        settings.displayName.value.takeIf { it.isNotBlank() }?.let { doc["ownerName"] = it }
        db.collection("groups").document(groupId).set(doc).await()
        pointer.set(mapOf("groupId" to groupId, "updatedAt" to now)).await()
        return groupId
    }

    // ---- family (P8) ------------------------------------------------------------------------------------------

    private fun requireReady(): Pair<String, FamilyState>? {
        val uid = _uid.value ?: return null
        val fam = _family.value ?: return null
        return uid to fam
    }

    /** Owner only. */
    override suspend fun createInvite(): Invite? {
        val (_, fam) = requireReady() ?: return null
        if (!fam.iAmOwner) return null
        return familyService.createInvite(fam.groupId)
    }

    /**
     * Joins the group in [text] (a scanned or pasted invitation). Everything on this phone is uploaded into that group.
     * Only possible while I'm alone in my own group — otherwise people would be left behind.
     */
    override suspend fun joinFamily(text: String, nickname: String): FamilyResult {
        val link = InviteLink.parse(text) ?: return FamilyResult.Failed("這不是有效的邀請連結或 QR Code")
        val (uid, fam) = requireReady() ?: return FamilyResult.Failed("請先開啟雲端備份與同步")
        if (fam.groupId == link.groupId) return FamilyResult.Failed("你已經在這個群組裡了")
        if (fam.isShared) return FamilyResult.Failed("你目前已在一個多人群組裡，請先離開才能加入另一個")
        val name = nickname.trim().ifEmpty { "家人" }
        settings.setDisplayName(name)
        val result = familyService.join(link, uid, name)
        if (result is FamilyResult.Ok) switchGroup(uid, link.groupId)
        return result
    }

    /** A member leaves; the owner cannot leave while others remain (remove them first). */
    override suspend fun leaveFamily(): FamilyResult {
        val (uid, fam) = requireReady() ?: return FamilyResult.Failed("尚未連線")
        if (fam.iAmOwner) return FamilyResult.Failed("你是這個群組的擁有者，請先移除其他成員")
        familyService.removeMember(fam.groupId, uid)
        dropGroup(uid)
        return FamilyResult.Ok
    }

    override suspend fun removeMember(uid: String): FamilyResult {
        val (_, fam) = requireReady() ?: return FamilyResult.Failed("尚未連線")
        if (!fam.iAmOwner) return FamilyResult.Failed("只有擁有者可以移除成員")
        familyService.removeMember(fam.groupId, uid)
        return FamilyResult.Ok
    }

    override suspend fun renameMe(name: String) {
        val (uid, fam) = requireReady() ?: return
        val clean = name.trim().ifEmpty { return }
        settings.setDisplayName(clean)
        if (fam.iAmOwner) familyService.renameOwner(fam.groupId, clean) else familyService.renameMember(fam.groupId, uid, clean)
    }

    /** A different cloud user signed in (Google account switch): re-sync everything against that user's group. */
    suspend fun userChanged() {
        database.syncDao().clearMeta()
        connect()
    }

    /** After the cloud account was deleted: forget the cloud's marks and uploaded-file lists; local data stays. */
    suspend fun afterAccountDeleted() {
        database.syncDao().clearMeta()
        context.getSharedPreferences("petdiary_sync", Context.MODE_PRIVATE).edit().clear().apply()
        _uid.value = null
    }

    /** Repoint this user at [groupId] and restart: everything local counts as unsent for the new group. */
    private suspend fun switchGroup(uid: String, groupId: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("groupId" to groupId, "updatedAt" to clock.millis())).await()
        database.syncDao().clearMeta()
        connect()
    }

    /** I'm not (or no longer) in my group: forget it and start over with a fresh private group. My local data stays. */
    private suspend fun dropGroup(uid: String) {
        runCatching { FirebaseFirestore.getInstance().collection("users").document(uid).delete().await() }
        database.syncDao().clearMeta()
        connect()
    }
}
