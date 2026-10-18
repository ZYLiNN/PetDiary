package com.zylinn.petdiary.data.trash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zylinn.petdiary.PetDiaryApplication
import java.util.concurrent.TimeUnit

/** Daily background purge, so expired items go away even if the app is rarely opened. */
class PurgeTrashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PetDiaryApplication
        app.container.trashRepository.purgeExpired()
        return Result.success()
    }

    companion object {
        private const val NAME = "purge-trash"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PurgeTrashWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
