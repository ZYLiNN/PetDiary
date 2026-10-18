package com.zylinn.petdiary

import android.app.Application
import android.content.Context
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.PhotoStore
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
    val photoStore: PhotoStore by lazy { PhotoStore(File(context.filesDir, "photos").apply { mkdirs() }) }
    val petRepository: PetRepository by lazy { PetRepository(database.petDao(), clock) }
    val trashRepository: TrashRepository by lazy { TrashRepository(database.petDao(), photoStore, clock) }
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
