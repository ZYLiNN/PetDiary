package com.zylinn.petdiary.sync

/** Key under which the cloud's own write time (microseconds, set by the server) travels in a document. */
const val SERVER_AT = "serverUpdatedAt"

/** The cloud side as the engine sees it, so the engine can be tested without Firebase. */
interface RemoteStore {
    /** Writes (creates or replaces) the documents; their "id" field is the document id. The cloud stamps [SERVER_AT]. */
    suspend fun push(table: SyncTable, docs: List<CloudDoc>)

    /**
     * Calls [onDocs] with the initial contents and again with every later change; ignores removals.
     * Every delivered document carries [SERVER_AT]; our own not-yet-confirmed writes are not delivered.
     */
    fun listen(table: SyncTable, onDocs: (List<CloudDoc>) -> Unit, onError: (Throwable) -> Unit): AutoCloseable

    /** Deletes documents whose deletedAt is at or before [cutoff] (their trash time is up). */
    suspend fun purgeDeletedBefore(table: SyncTable, cutoff: Long)
}
