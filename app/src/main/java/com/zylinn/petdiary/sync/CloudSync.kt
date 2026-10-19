package com.zylinn.petdiary.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zylinn.petdiary.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
 * the user's private group, then hands over to [SyncEngine]. Turning it off only stops syncing; nothing is deleted.
 */
class CloudSync(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val database: com.zylinn.petdiary.data.PetDiaryDatabase,
    private val clock: Clock,
) {
    private val _status = MutableStateFlow<SyncStatus>(if (settings.cloudSyncEnabled.value) SyncStatus.Connecting else SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var job: Job? = null

    /** Called once at app start. */
    fun start() {
        if (settings.cloudSyncEnabled.value) connect()
    }

    fun enable() {
        settings.setCloudSyncEnabled(true)
        connect()
    }

    fun disable() {
        settings.setCloudSyncEnabled(false)
        job?.cancel()
        job = null
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

    private suspend fun run() {
        val auth = FirebaseAuth.getInstance()
        val uid = (auth.currentUser ?: auth.signInAnonymously().await().user)!!.uid
        val db = FirebaseFirestore.getInstance()
        val groupId = ensureGroup(db, uid)
        lateinit var engine: SyncEngine
        engine = SyncEngine(
            database.syncDao(), FirestoreRemote(db, groupId), clock,
            onError = { e -> _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName) },
            inTransaction = { block -> database.withTransaction { block() } },
        )
        val engineJob = engine.start(scope)
        _status.value = SyncStatus.Active(null)
        launchStatusUpdates(engine)
        engineJob.join()
    }

    private fun launchStatusUpdates(engine: SyncEngine) = scope.launch {
        engine.lastSyncedAt.collect { t -> if (_status.value !is SyncStatus.Off) _status.value = SyncStatus.Active(t) }
    }

    /** Finds the user's group via users/{uid}; the very first time, creates a private one (the owner is the only member). */
    private suspend fun ensureGroup(db: FirebaseFirestore, uid: String): String {
        val pointer = db.collection("users").document(uid)
        pointer.get().await().getString("groupId")?.let { return it }
        val groupId = UUID.randomUUID().toString()
        val now = clock.millis()
        db.collection("groups").document(groupId)
            .set(mapOf("ownerUid" to uid, "memberUids" to listOf(uid), "createdAt" to now, "updatedAt" to now)).await()
        pointer.set(mapOf("groupId" to groupId, "updatedAt" to now)).await()
        return groupId
    }
}
