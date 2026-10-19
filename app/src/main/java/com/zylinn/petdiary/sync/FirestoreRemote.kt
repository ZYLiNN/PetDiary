package com.zylinn.petdiary.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** [RemoteStore] on Cloud Firestore: everything under groups/{groupId}/{table}/{id}, as the security rules expect. */
class FirestoreRemote(private val db: FirebaseFirestore, private val groupId: String) : RemoteStore {

    private fun collection(table: SyncTable) = db.collection("groups").document(groupId).collection(table.path)

    override suspend fun push(table: SyncTable, docs: List<CloudDoc>) {
        val batch = db.batch()
        docs.forEach { batch.set(collection(table).document(it["id"] as String), it) }
        batch.commit().await()
    }

    override fun listen(table: SyncTable, onDocs: (List<CloudDoc>) -> Unit, onError: (Throwable) -> Unit): AutoCloseable {
        val registration = collection(table).addSnapshotListener { snap, error ->
            if (error != null) return@addSnapshotListener onError(error)
            val changed = snap?.documentChanges.orEmpty().filter { it.type != DocumentChange.Type.REMOVED }
            if (changed.isNotEmpty()) onDocs(changed.map { it.document.data })
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
