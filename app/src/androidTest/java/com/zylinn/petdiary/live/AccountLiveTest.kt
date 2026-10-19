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
import com.zylinn.petdiary.sync.AccountResult
import com.zylinn.petdiary.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Firebase: upload data and a photo, delete the cloud account, and check nothing is left. Needs network. */
@RunWith(AndroidJUnit4::class)
class AccountLiveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PetDiaryApplication).container

    private fun waitUntil(what: String, ms: Long = 60_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(400) }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test fun deletingTheCloudAccountRemovesEverythingAndKeepsLocalData() = runBlocking {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val db = FirebaseFirestore.getInstance()
        val pet = container.petRepository.add(PetInput(name = "刪除測試貓", species = Species.CAT, birthEpochDay = 19000))
        val photo = "live-del-${System.nanoTime()}.jpg"
        container.photoStore.file(photo).writeBytes(ByteArray(512) { it.toByte() })
        container.petRepository.get(pet.id)!!.let { container.database.petDao().upsert(it.copy(photoFile = photo, updatedAt = it.updatedAt + 1)) }
        try {
            container.cloudSync.enable()
            waitUntil("synced with a family state") { container.cloudSync.status.value is SyncStatus.Active && container.cloudSync.family.value != null }
            val uid = auth.currentUser!!.uid
            val gid = container.cloudSync.family.value!!.groupId
            waitUntil("pet and photo are in the cloud") {
                db.document("groups/$gid/pets/${pet.id}").get().await().exists() &&
                    runCatching { FirebaseStorage.getInstance().reference.child("groups/$gid/files/$photo").metadata.await() }.isSuccess
            }

            val result = container.accountService.deleteCloudAccount(context)
            assertTrue("result=$result", result is AccountResult.Ok)

            // the cloud account is gone and signed out
            assertNull(auth.currentUser)
            assertEquals(SyncStatus.Off, container.cloudSync.status.value)
            // local data is untouched
            assertEquals("刪除測試貓", container.petRepository.get(pet.id)?.name)
            assertTrue(container.photoStore.exists(photo))
            // a fresh anonymous user (the old one no longer exists) cannot find the old data, and the old group is gone
            auth.signInAnonymously().await()
            assertTrue("old group must be gone or unreadable", runCatching { db.document("groups/$gid").get().await() }.map { !it.exists() }.getOrDefault(true))
            assertFalse(runCatching { FirebaseStorage.getInstance().reference.child("groups/$gid/files/$photo").metadata.await() }.isSuccess)
            assertTrue(uid.isNotEmpty())
        } finally {
            container.cloudSync.disable()
            container.petRepository.moveToTrash(pet.id)
            runCatching { container.database.petDao().deletePermanently(pet.id) }
            container.photoStore.file(photo).delete()
            runCatching { auth.currentUser?.delete()?.await() }
        }
    }
}
