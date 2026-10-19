package com.zylinn.petdiary.data.photo

import com.zylinn.petdiary.data.pet.PetDao
import com.zylinn.petdiary.data.record.AttachmentDao
import java.io.File

/**
 * Deletes avatar / attachment files no database row points to (e.g. the app was killed while a record
 * with freshly picked photos was still being edited). Files younger than [graceMillis] are kept, because
 * an editor that is open right now may have just imported them.
 */
class OrphanCleaner(
    private val petDao: PetDao,
    private val attachmentDao: AttachmentDao,
    private val photoDir: File,
    private val attachmentDir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val graceMillis: Long = 24 * 60 * 60 * 1000L,
) {
    /** Returns how many files were removed. */
    suspend fun clean(): Int {
        val photos = petDao.allPhotoFiles().toSet()
        val attachments = attachmentDao.allFileNames().toSet()
        return sweep(photoDir, photos) + sweep(attachmentDir, attachments)
    }

    private fun sweep(dir: File, referenced: Set<String>): Int {
        val cutoff = now() - graceMillis
        var removed = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in referenced && f.lastModified() < cutoff && f.delete()) removed++
        }
        return removed
    }
}
