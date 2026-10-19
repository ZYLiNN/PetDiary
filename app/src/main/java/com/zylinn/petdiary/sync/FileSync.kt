package com.zylinn.petdiary.sync

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.zylinn.petdiary.data.photo.FileStore
import kotlinx.coroutines.tasks.await
import java.io.File

/** Remembers which files this phone already uploaded for the current group. */
interface UploadLog {
    fun has(name: String): Boolean
    fun add(name: String)
}

/**
 * Photos and attachments live in Cloud Storage at groups/{groupId}/files/{fileName}. Whatever the database refers
 * to is uploaded when we have it and downloaded when we don't. Names are random UUIDs, so one name = one file forever.
 * Deleting files from the cloud is not done here (account / data deletion is P9).
 */
class FileSync(
    storage: FirebaseStorage,
    private val groupId: String,
    private val dao: SyncDao,
    private val photos: FileStore,
    private val attachments: FileStore,
    private val log: UploadLog,
) {
    private val root = storage.reference.child("groups").child(groupId).child("files")

    /** One pass. Returns the first problem met (after trying every file), or null. */
    suspend fun syncOnce(): Throwable? {
        var problem: Throwable? = null
        val wanted = dao.petPhotoFiles().map { photos to it } + dao.attachmentFiles().map { attachments to it }
        for ((store, name) in wanted.distinct()) {
            try {
                if (store.exists(name)) {
                    if (!log.has(name)) upload(store.file(name), name)
                } else {
                    download(store, name)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                problem = problem ?: e
            }
        }
        return problem
    }

    private suspend fun upload(file: File, name: String) {
        val type = if (name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
        root.child(name).putFile(android.net.Uri.fromFile(file), StorageMetadata.Builder().setContentType(type).build()).await()
        log.add(name)
    }

    private suspend fun download(store: FileStore, name: String) {
        val target = store.file(name)
        val partial = File(target.parentFile, "$name.part")
        try {
            root.child(name).getFile(partial).await()
            if (!partial.renameTo(target)) partial.delete()
            else log.add(name) // it is in the cloud for sure
        } catch (e: StorageException) {
            partial.delete()
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e // not uploaded yet by the other phone: try later
        }
    }
}
