package com.zylinn.petdiary

import android.app.Application
import android.content.Context
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.PhotoImporter
import com.zylinn.petdiary.data.photo.AttachmentImporter
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.trash.PurgeTrashWorker
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock

/** Hand-written dependency container (kept simple on purpose; no DI framework). */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: PetDiaryDatabase by lazy { PetDiaryDatabase.build(context) }
    val photoStore: FileStore by lazy { FileStore(File(context.filesDir, "photos").apply { mkdirs() }) }
    val attachmentStore: FileStore by lazy { FileStore(File(context.filesDir, "attachments").apply { mkdirs() }) }
    val photoImporter: PhotoImporter by lazy { PhotoImporter(context.contentResolver, photoStore) }
    val attachmentImporter: AttachmentImporter by lazy { AttachmentImporter(context.contentResolver, attachmentStore) }
    val petRepository: PetRepository by lazy { PetRepository(database.petDao(), clock) }
    val recordRepository: RecordRepository by lazy {
        RecordRepository(database.recordDao(), database.attachmentDao(), database.supplementDao(), attachmentStore, clock)
    }
    val trashRepository: TrashRepository by lazy {
        TrashRepository(database.petDao(), database.recordDao(), database.attachmentDao(), photoStore, attachmentStore, clock)
    }
}

class PetDiaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Purge on every launch (covers long gaps without opening the app) plus a daily job.
        container.appScope.launch { container.trashRepository.purgeExpired() }
        PurgeTrashWorker.schedule(this)
    }
}
