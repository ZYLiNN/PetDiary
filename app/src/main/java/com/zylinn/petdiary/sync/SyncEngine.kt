package com.zylinn.petdiary.sync

import com.zylinn.petdiary.data.trash.TrashPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Two-way sync between Room and a [RemoteStore]. Local-first: the app always reads and writes Room;
 * this engine uploads whatever changed (per-table watermark on updatedAt) and applies cloud changes.
 * Conflicts: the newest updatedAt wins, per document. Ties are ignored, which also stops our own uploads
 * from bouncing back and forth.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SyncEngine(
    private val dao: SyncDao,
    private val remote: RemoteStore,
    private val watermarks: Watermarks,
    private val clock: Clock,
    private val pushDelayMs: Long = 400,
    private val onError: (Throwable) -> Unit = {},
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
            combine(dao.petsStamp(), dao.recordsStamp(), dao.remindersStamp(), dao.supplementsStamp()) { _, _, _, _ -> }
                .debounce(pushDelayMs)
                .collectLatest { runCatching { pushPending() }.onFailure(onError) }
        } finally {
            listeners.forEach { runCatching { it.close() } }
            incoming.close()
        }
    }

    /** Uploads everything newer than each table's watermark. Public for tests. */
    suspend fun pushPending() {
        push(SyncTable.PETS, dao.petsAfter(watermarks.get(SyncTable.PETS)).map { it.updatedAt to CloudMapper.pet(it) })
        push(SyncTable.RECORDS, dao.recordsAfter(watermarks.get(SyncTable.RECORDS)).map { it.updatedAt to CloudMapper.record(it) })
        push(SyncTable.REMINDERS, dao.remindersAfter(watermarks.get(SyncTable.REMINDERS)).map { it.updatedAt to CloudMapper.reminder(it) })
        push(SyncTable.SUPPLEMENTS, dao.supplementsAfter(watermarks.get(SyncTable.SUPPLEMENTS)).map { it.updatedAt to CloudMapper.supplement(it) })
    }

    private suspend fun push(table: SyncTable, rows: List<Pair<Long, CloudDoc>>) {
        if (rows.isEmpty()) return
        rows.chunked(BATCH).forEach { chunk ->
            remote.push(table, chunk.map { it.second })
            watermarks.set(table, chunk.maxOf { it.first })
        }
        touched()
    }

    /** Applies cloud documents that are newer than ours. Public for tests. */
    suspend fun apply(table: SyncTable, docs: List<CloudDoc>) {
        val expiredBefore = TrashPolicy.cutoff(clock.millis())
        var changed = false
        for (d in docs) {
            val id = d["id"] as? String ?: continue
            val remoteStamp = (d["updatedAt"] as? Number)?.toLong() ?: continue
            val deletedAt = (d["deletedAt"] as? Number)?.toLong()
            val localStamp = when (table) {
                SyncTable.PETS -> dao.petUpdatedAt(id)
                SyncTable.RECORDS -> dao.recordUpdatedAt(id)
                SyncTable.REMINDERS -> dao.reminderUpdatedAt(id)
                SyncTable.SUPPLEMENTS -> dao.supplementUpdatedAt(id)
            }
            if (localStamp != null && remoteStamp <= localStamp) continue
            // A long-trashed copy we no longer have must not come back to life.
            if (localStamp == null && deletedAt != null && deletedAt <= expiredBefore) continue
            when (table) {
                SyncTable.PETS -> CloudMapper.pet(d)?.let { dao.upsertPet(it) }
                SyncTable.RECORDS -> CloudMapper.record(d)?.let { dao.upsertRecord(it) }
                SyncTable.REMINDERS -> CloudMapper.reminder(d, dao.reminderNotifiedFor(id))?.let { dao.upsertReminder(it) }
                SyncTable.SUPPLEMENTS -> CloudMapper.supplement(d)?.let { dao.upsertSupplement(it) }
            } ?: continue
            changed = true
        }
        if (changed) touched()
    }

    private companion object {
        const val BATCH = 400 // Firestore allows 500 writes per batch
    }
}
