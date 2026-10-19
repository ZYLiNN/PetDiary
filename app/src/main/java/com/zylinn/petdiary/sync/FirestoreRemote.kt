package com.zylinn.petdiary.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** [RemoteStore] on Cloud Firestore: everything under groups/{groupId}/{table}/{id}, as the security rules expect. */
class FirestoreRemote(private val db: FirebaseFirestore, private val groupId: String) : RemoteStore {

    private fun collection(table: SyncTable) = db.collection("groups").document(groupId).collection(table.path)

    override suspend fun push(table: SyncTable, docs: List<CloudDoc>) {
        val batch = db.batch()
        // The server fills in its own clock; the security rules insist the value equals the request time.
        docs.forEach { batch.set(collection(table).document(it["id"] as String), it + (SERVER_AT to FieldValue.serverTimestamp())) }
        batch.commit().await()
    }

    override fun listen(table: SyncTable, onDocs: (List<CloudDoc>) -> Unit, onError: (Throwable) -> Unit): AutoCloseable {
        val registration = collection(table).addSnapshotListener { snap, error ->
            if (error != null) return@addSnapshotListener onError(error)
            val docs = snap?.documentChanges.orEmpty()
                .filter { it.type != DocumentChange.Type.REMOVED }
                .mapNotNull { change ->
                    val ts = change.document.getTimestamp(SERVER_AT) ?: return@mapNotNull null // our own write, not confirmed yet
                    val data = change.document.data
                    data + (SERVER_AT to ts.seconds * 1_000_000L + ts.nanoseconds / 1_000L)
                }
            if (docs.isNotEmpty()) onDocs(docs)
        }
        return AutoCloseable { registration.remove() }
    }

    override suspend fun purgeDeletedBefore(table: SyncTable, cutoff: Long) {
        val expired = collection(table).whereLessThanOrEqualTo("deletedAt", cutoff).get().await()
        expired.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
