package com.zylinn.petdiary

import android.app.Application
import android.content.Context
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.OrphanCleaner
import com.zylinn.petdiary.data.photo.PhotoImporter
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.data.photo.AttachmentImporter
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.reminder.ReminderRepository
import com.zylinn.petdiary.data.trash.PurgeTrashWorker
import com.zylinn.petdiary.reminder.ReminderNotifier
import com.zylinn.petdiary.sync.AccountService
import com.zylinn.petdiary.sync.CloudSync
import com.zylinn.petdiary.reminder.ReminderScheduler
import com.zylinn.petdiary.reminder.ReminderSyncer
import com.zylinn.petdiary.data.trash.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.zylinn.petdiary.sync.CloudDeleteQueue
import java.io.File
import java.time.Clock

/** Hand-written dependency container (kept simple on purpose; no DI framework). */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    /** Text of a `petdiary://join` link opened from outside (system camera, browser, chat); the UI consumes it. */
    val incomingInvite = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: PetDiaryDatabase by lazy { PetDiaryDatabase.build(context) }
    /** Files deleted here are also removed from the cloud on the next sync pass (D63). */
    val cloudDeletes: CloudDeleteQueue by lazy { CloudDeleteQueue(context.getSharedPreferences("petdiary_sync", Context.MODE_PRIVATE)) }
    val photoStore: FileStore by lazy { FileStore(File(context.filesDir, "photos").apply { mkdirs() }, cloudDeletes::add) }
    val attachmentStore: FileStore by lazy { FileStore(File(context.filesDir, "attachments").apply { mkdirs() }, cloudDeletes::add) }
    val settings: AppSettings by lazy { AppSettings(context) }
    val orphanCleaner: OrphanCleaner by lazy {
        OrphanCleaner(database.petDao(), database.attachmentDao(), File(context.filesDir, "photos"), File(context.filesDir, "attachments"))
    }
    val photoImporter: PhotoImporter by lazy { PhotoImporter(context.contentResolver, photoStore) }
    val attachmentImporter: AttachmentImporter by lazy { AttachmentImporter(context.contentResolver, attachmentStore) }
    val petRepository: PetRepository by lazy { PetRepository(database.petDao(), clock) }
    val recordRepository: RecordRepository by lazy {
        RecordRepository(
            database.recordDao(), database.attachmentDao(), database.supplementDao(), attachmentStore, clock,
            reminders = database.reminderDao(),
            author = { cloudSync.currentUid() },
        )
    }
    val trashRepository: TrashRepository by lazy {
        TrashRepository(database.petDao(), database.recordDao(), database.attachmentDao(), photoStore, attachmentStore, clock, database.reminderDao())
    }
    val cloudSync: CloudSync by lazy { CloudSync(context, appScope, settings, database, photoStore, attachmentStore, clock, cloudDeletes) }
    val accountService: AccountService by lazy { AccountService(context, cloudSync) }
    val notifier: ReminderNotifier by lazy { ReminderNotifier(context) }
    val reminderSyncer: ReminderSyncer by lazy {
        ReminderSyncer(appScope, reminderRepository, ReminderScheduler(context), notifier, clock)
    }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao(), recordRepository, clock) }
}

class PetDiaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Purge on every launch (covers long gaps without opening the app) plus a daily job.
        container.appScope.launch {
            container.trashRepository.purgeExpired()
            container.orphanCleaner.clean()
        }
        PurgeTrashWorker.schedule(this)
        container.reminderSyncer.start()
        container.cloudSync.start()
    }
}
