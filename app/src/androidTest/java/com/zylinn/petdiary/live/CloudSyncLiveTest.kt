package com.zylinn.petdiary.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Talks to the REAL Firebase project (anonymous sign-in + Firestore) and cleans up after itself.
 * Needs network and app/google-services.json; run on purpose with
 * -Pandroid.testInstrumentationRunnerArguments.class=com.zylinn.petdiary.live.CloudSyncLiveTest
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncLiveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PetDiaryApplication).container

    private fun waitUntil(what: String, ms: Long = 40_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(300) }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test fun phoneAndCloudStayInSync() = runBlocking {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        auth.signOut() // a brand-new anonymous user, like a first install
        val pet = container.petRepository.add(PetInput(name = "雲端測試貓", species = Species.CAT, birthEpochDay = 19000))
        var uid: String? = null
        var groupId: String? = null
        try {
            container.cloudSync.enable()
            waitUntil("signed in and syncing") { container.cloudSync.status.value is SyncStatus.Active }
            uid = auth.currentUser!!.uid
            groupId = db.collection("users").document(uid).get().await().getString("groupId")
            assertTrue("user pointer exists", groupId != null)
            val group = db.collection("groups").document(groupId!!)
            assertEquals(listOf(uid), group.get().await().get("memberUids"))

            // phone → cloud
            waitUntil("pet uploaded") { group.collection("pets").document(pet.id).get().await().exists() }
            assertEquals("雲端測試貓", group.collection("pets").document(pet.id).get().await().getString("name"))

            // cloud → phone (what a second device would do): newer updatedAt wins
            group.collection("pets").document(pet.id).update(mapOf("name" to "雲端改名", "updatedAt" to System.currentTimeMillis() + 60_000, "serverUpdatedAt" to FieldValue.serverTimestamp())).await()
            waitUntil("rename arrives on the phone") { container.petRepository.get(pet.id)?.name == "雲端改名" }

            // a record created in the cloud shows up locally
            val now = System.currentTimeMillis() + 120_000
            group.collection("records").document("live-r1").set(
                mapOf("id" to "live-r1", "petId" to pet.id, "type" to "WALK", "dateEpochDay" to 20000L, "minuteOfDay" to 480L,
                    "durationMin" to 30L, "createdAt" to now, "updatedAt" to now, "serverUpdatedAt" to FieldValue.serverTimestamp()),
            ).await()
            waitUntil("record arrives on the phone") { container.database.recordDao().getById("live-r1") != null }
            assertEquals(30, container.database.recordDao().getById("live-r1")!!.durationMin)
        } finally {
            container.cloudSync.disable()
            container.petRepository.moveToTrash(pet.id)
            runCatching { container.database.recordDao().deletePermanently("live-r1") }
            runCatching { container.database.petDao().deletePermanently(pet.id) }
            if (groupId != null && uid != null) {
                val group = db.collection("groups").document(groupId)
                for (t in listOf("pets", "records", "reminders", "supplements")) {
                    group.collection(t).get().await().documents.forEach { it.reference.delete().await() }
                }
                group.delete().await()
                db.collection("users").document(uid).delete().await()
            }
            auth.currentUser?.delete()?.await()
        }
    }
}
