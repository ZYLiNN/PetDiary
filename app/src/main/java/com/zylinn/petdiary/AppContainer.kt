package com.zylinn.petdiary

import android.app.Application
import android.content.Context
import com.zylinn.petdiary.data.PetDiaryDatabase
import com.zylinn.petdiary.data.pet.PetRepository
import java.time.Clock

/** Hand-written dependency container (kept simple on purpose; no DI framework). */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val database: PetDiaryDatabase by lazy { PetDiaryDatabase.build(context) }
    val petRepository: PetRepository by lazy { PetRepository(database.petDao(), clock) }
}

class PetDiaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
