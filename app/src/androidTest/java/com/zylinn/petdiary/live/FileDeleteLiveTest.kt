package com.zylinn.petdiary.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D63 against real Firebase: a photo uploaded by this phone disappears from Cloud Storage once its pet is purged
 * from the trash. Run on purpose with -Pandroid.testInstrumentationRunnerArguments.class=com.zylinn.petdiary.live.FileDeleteLiveTest
 */
@RunWith(AndroidJUnit4::class)
class FileDeleteLiveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PetDiaryApplication).container

    private fun waitUntil(what: String, ms: Long = 90_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(500) }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test fun purgedPhotoIsRemovedFromTheCloud() = runBlocking {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        auth.signOut()
        val pet = container.petRepository.add(PetInput(name = "刪檔測試貓", species = Species.CAT, birthEpochDay = 19000))
        val photoName = "live-del-${System.nanoTime()}.jpg"
        container.photoStore.file(photoName).writeBytes(ByteArray(1024) { it.toByte() })
        container.petRepository.get(pet.id)!!.let { container.database.petDao().upsert(it.copy(photoFile = photoName, updatedAt = it.updatedAt + 1)) }
        var uid: String? = null
        var groupId: String? = null
        try {
            container.cloudSync.enable()
            waitUntil("syncing") { container.cloudSync.status.value is SyncStatus.Active && container.cloudSync.family.value != null }
            uid = auth.currentUser!!.uid
            groupId = container.cloudSync.family.value!!.groupId
            val ref = FirebaseStorage.getInstance().reference.child("groups/$groupId/files/$photoName")
            waitUntil("photo uploaded") { runCatching { ref.metadata.await() }.isSuccess }

            container.petRepository.moveToTrash(pet.id)
            container.trashRepository.deletePermanently(pet.id)
            waitUntil("photo deleted from the cloud") { runCatching { ref.metadata.await() }.isFailure }
            check(photoName !in container.cloudDeletes.pending()) { "queue entry should be cleared" }
        } finally {
            container.cloudSync.disable()
            runCatching { container.database.petDao().deletePermanently(pet.id) }
            container.photoStore.file(photoName).delete()
            if (groupId != null) {
                runCatching { FirebaseStorage.getInstance().reference.child("groups/$groupId/files/$photoName").delete().await() }
                for (t in listOf("pets", "records", "reminders", "supplements", "members")) {
                    runCatching { db.collection("groups/$groupId/$t").get().await().documents.forEach { it.reference.delete().await() } }
                }
                runCatching { db.document("groups/$groupId").delete().await() }
            }
            uid?.let { runCatching { db.document("users/$it").delete().await() } }
            runCatching { auth.currentUser?.delete()?.await() }
        }
    }
}
