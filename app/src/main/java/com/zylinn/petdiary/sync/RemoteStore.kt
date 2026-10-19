package com.zylinn.petdiary.sync

/** The cloud side as the engine sees it, so the engine can be tested without Firebase. */
interface RemoteStore {
    /** Writes (creates or replaces) the documents; their "id" field is the document id. */
    suspend fun push(table: SyncTable, docs: List<CloudDoc>)

    /** Calls [onDocs] with the initial contents and again with every later change; ignores removals. */
    fun listen(table: SyncTable, onDocs: (List<CloudDoc>) -> Unit, onError: (Throwable) -> Unit): AutoCloseable

    /** Deletes documents whose deletedAt is at or before [cutoff] (their trash time is up). */
    suspend fun purgeDeletedBefore(table: SyncTable, cutoff: Long)
}

/** Per-table "uploaded up to this updatedAt" marks. */
interface Watermarks {
    fun get(table: SyncTable): Long
    fun set(table: SyncTable, value: Long)
}
