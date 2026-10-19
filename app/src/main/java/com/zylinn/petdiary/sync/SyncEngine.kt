package com.zylinn.petdiary.sync

import com.zylinn.petdiary.data.trash.TrashPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Two-way sync between Room and a [RemoteStore]. Local-first: the app always reads and writes Room.
 *
 * Ordering is decided by the cloud's own clock (D55), never by phone clocks:
 *  - a row is "dirty" when its updatedAt differs from the version recorded at the last sync ([SyncMeta]);
 *    dirty rows are uploaded, and the cloud stamps them with its server time;
 *  - a cloud document replaces our row only when it is a newer server version AND our row has no unsent edits.
 *    If both sides changed, whichever upload reaches the cloud last wins — ours is uploaded right after.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SyncEngine(
    private val dao: SyncDao,
    private val remote: RemoteStore,
    private val clock: Clock,
    private val pushDelayMs: Long = 400,
    private val onError: (Throwable) -> Unit = {},
    /** Runs a block as one database transaction, so "no unsent edit?" and "write the cloud copy" can't be split by an edit. */
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    /** Time of the last successful upload or cloud change applied. */
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private fun touched() { _lastSyncedAt.value = clock.millis() }

    /** Starts listening and uploading inside [scope]; cancel the returned job to stop. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        val incoming = Channel<Pair<SyncTable, List<CloudDoc>>>(Channel.UNLIMITED)
        val listeners = SyncTable.entries.map { table ->
            remote.listen(table, { docs -> incoming.trySend(table to docs) }, onError)
        }
        try {
            launch { // apply cloud changes one batch at a time
                for ((table, docs) in incoming) {
                    runCatching { apply(table, docs) }.onFailure(onError)
                }
            }
            launch {
                runCatching { SyncTable.entries.forEach { remote.purgeDeletedBefore(it, TrashPolicy.cutoff(clock.millis())) } }
                    .onFailure(onError)
            }
            // Never cancel an upload half-way (collectLatest would): the "sent" mark must be written, otherwise the same
            // old version gets sent again later and can overwrite a newer one from another device.
            dao.changeSignal()
                .debounce(pushDelayMs)
                .conflate()
                .collect { runCatching { pushPending() }.onFailure(onError) }
        } finally {
            listeners.forEach { runCatching { it.close() } }
            incoming.close()
        }
    }

    /** Uploads every row with unsent edits. Public for tests. */
    suspend fun pushPending() {
        push(SyncTable.PETS, dao.dirtyPets().map { Triple(it.id, it.updatedAt, CloudMapper.pet(it)) })
        push(SyncTable.RECORDS, dao.dirtyRecords().map { Triple(it.id, it.updatedAt, CloudMapper.record(it)) })
        push(SyncTable.REMINDERS, dao.dirtyReminders().map { Triple(it.id, it.updatedAt, CloudMapper.reminder(it)) })
        push(SyncTable.SUPPLEMENTS, dao.dirtySupplements().map { Triple(it.id, it.updatedAt, CloudMapper.supplement(it)) })
    }

    private suspend fun push(table: SyncTable, rows: List<Triple<String, Long, CloudDoc>>) {
        if (rows.isEmpty()) return
        rows.chunked(BATCH).forEach { chunk ->
            remote.push(table, chunk.map { it.third })
            // Remember the exact version we sent: an edit made meanwhile has a different updatedAt and stays dirty.
            dao.upsertMetas(chunk.map { (id, stamp, _) ->
                SyncMeta(table.path, id, stamp, dao.meta(table.path, id)?.serverAt ?: 0L)
            })
        }
        touched()
    }

    /** Applies cloud documents that are new to us and don't collide with unsent edits. Public for tests. */
    suspend fun apply(table: SyncTable, docs: List<CloudDoc>) {
        val expiredBefore = TrashPolicy.cutoff(clock.millis())
        var changed = false
        for (d in docs) inTransaction {
            val id = d["id"] as? String ?: return@inTransaction
            val serverAt = (d[SERVER_AT] as? Number)?.toLong() ?: return@inTransaction // not confirmed by the cloud yet
            val remoteStamp = (d["updatedAt"] as? Number)?.toLong() ?: return@inTransaction
            val deletedAt = (d["deletedAt"] as? Number)?.toLong()
            val meta = dao.meta(table.path, id)
            if (meta != null && serverAt <= meta.serverAt) return@inTransaction // already seen this version
            val localStamp = when (table) {
                SyncTable.PETS -> dao.petUpdatedAt(id)
                SyncTable.RECORDS -> dao.recordUpdatedAt(id)
                SyncTable.REMINDERS -> dao.reminderUpdatedAt(id)
                SyncTable.SUPPLEMENTS -> dao.supplementUpdatedAt(id)
            }
            if (localStamp == null) {
                // A long-trashed copy we no longer have must not come back to life.
                if (deletedAt != null && deletedAt <= expiredBefore) return@inTransaction
            } else {
                val dirty = meta == null || meta.localStamp != localStamp
                if (dirty) { // our unsent edit will be uploaded next and wins (it reaches the cloud later)
                    if (meta != null) dao.upsertMeta(meta.copy(serverAt = serverAt))
                    return@inTransaction
                }
                if (localStamp == remoteStamp) { // our own upload echoing back
                    dao.upsertMeta(SyncMeta(table.path, id, localStamp, serverAt))
                    return@inTransaction
                }
            }
            val stored = when (table) {
                SyncTable.PETS -> CloudMapper.pet(d)?.also { dao.upsertPet(it) }
                SyncTable.RECORDS -> CloudMapper.record(d)?.also { dao.upsertRecord(it) }
                SyncTable.REMINDERS -> CloudMapper.reminder(d, dao.reminderNotifiedFor(id))?.also { dao.upsertReminder(it) }
                SyncTable.SUPPLEMENTS -> CloudMapper.supplement(d)?.also { dao.upsertSupplement(it) }
            }
            if (stored != null) {
                dao.upsertMeta(SyncMeta(table.path, id, remoteStamp, serverAt))
                changed = true
            }
        }
        if (changed) touched()
    }

    private companion object {
        const val BATCH = 400 // Firestore allows 500 writes per batch
    }
}
