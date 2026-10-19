package com.zylinn.petdiary.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.zylinn.petdiary.PetDiaryApplication
import com.zylinn.petdiary.data.pet.PetInput
import com.zylinn.petdiary.data.pet.Species
import com.zylinn.petdiary.sync.FamilyResult
import com.zylinn.petdiary.sync.FamilyService
import com.zylinn.petdiary.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * Real Firebase, two independent clients: this app's own cloud sync (phone A) and a second Firebase app instance
 * (phone B, a different anonymous user). Cleans up after itself. Run on purpose with
 * -Pandroid.testInstrumentationRunnerArguments.class=com.zylinn.petdiary.live.FamilyLiveTest
 */
@RunWith(AndroidJUnit4::class)
class FamilyLiveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context.applicationContext as PetDiaryApplication).container

    private fun waitUntil(what: String, ms: Long = 60_000, check: suspend () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (check()) return@runBlocking; Thread.sleep(400) }
        throw AssertionError("timed out waiting for: $what")
    }

    /** Created once and kept for the whole run (deleting and re-creating it trips over Firebase's own DataStore files). */
    private fun secondPhone(): FirebaseApp =
        FirebaseApp.getApps(context).firstOrNull { it.name == "phoneB" }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "phoneB")

    @Test fun ownerInvitesAndAnotherPhoneJoinsReadsFilesAndIsRemoved() = runBlocking {
        val authA = FirebaseAuth.getInstance()
        authA.signOut()
        val appB = secondPhone()
        val authB = FirebaseAuth.getInstance(appB)
        val dbB = FirebaseFirestore.getInstance(appB)
        val storageB = FirebaseStorage.getInstance(appB)
        val pet = container.petRepository.add(PetInput(name = "家庭測試貓", species = Species.CAT, birthEpochDay = 19000))
        val photoName = "live-${System.nanoTime()}.jpg"
        val photoBytes = ByteArray(2048) { (it * 7).toByte() }
        container.photoStore.file(photoName).writeBytes(photoBytes)
        container.petRepository.get(pet.id)!!.let { container.database.petDao().upsert(it.copy(photoFile = photoName, updatedAt = it.updatedAt + 1)) }
        var groupA: String? = null
        var uidA: String? = null
        try {
            // phone A: sync on, wait until the group is known
            container.cloudSync.enable()
            waitUntil("A active with family state") { container.cloudSync.status.value is SyncStatus.Active && container.cloudSync.family.value != null }
            uidA = authA.currentUser!!.uid
            groupA = container.cloudSync.family.value!!.groupId
            assertTrue(container.cloudSync.family.value!!.iAmOwner)

            // phone B: separate anonymous user, cannot see A's group yet
            val uidB = authB.signInAnonymously().await().user!!.uid
            waitUntil("pet reaches the cloud") { FirebaseFirestore.getInstance().document("groups/$groupA/pets/${pet.id}").get().await().exists() }
            val deniedBefore = runCatching { dbB.document("groups/$groupA/pets/${pet.id}").get().await() }.isFailure
            assertTrue("a stranger must not read the group", deniedBefore)

            // A invites, B joins with the invitation text
            val invite = container.cloudSync.createInvite()!!
            val service = FamilyService(dbB, Clock.systemDefaultZone())
            assertEquals(FamilyResult.Ok, service.join(invite.link, uidB, "爸爸"))
            val again = service.join(invite.link, uidB, "爸爸")
            assertTrue("one-time invite", again is FamilyResult.Failed)

            // B can now read the pet, and download the photo A uploaded
            assertEquals("家庭測試貓", dbB.document("groups/$groupA/pets/${pet.id}").get().await().getString("name"))
            var lastError: Throwable? = null
            try {
                waitUntil("photo uploaded by A") {
                    val r = runCatching { storageB.reference.child("groups/$groupA/files/$photoName").getBytes(10_000_000).await() }
                    lastError = r.exceptionOrNull()
                    r.getOrNull()?.let { assertArrayEquals(photoBytes, it); true } ?: false
                }
            } catch (e: AssertionError) {
                // is it there at all? ask as the owner
                val asOwner = runCatching { FirebaseStorage.getInstance().reference.child("groups/$groupA/files/$photoName").getBytes(10_000_000).await().size }
                throw AssertionError("photo: B error=$lastError; owner read=$asOwner; A status=${container.cloudSync.status.value}", e)
            }
            waitUntil("A sees B as a member") { container.cloudSync.family.value!!.members.size == 2 }
            assertEquals("爸爸", container.cloudSync.family.value!!.members.first { !it.isOwner }.name)

            // B writes a record; A receives it with the author
            val now = System.currentTimeMillis()
            dbB.document("groups/$groupA/records/live-b1").set(
                mapOf("id" to "live-b1", "petId" to pet.id, "type" to "WALK", "dateEpochDay" to 20000L, "minuteOfDay" to 480L, "durationMin" to 20L,
                    "authorUid" to uidB, "createdAt" to now, "updatedAt" to now, "serverUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
            ).await()
            waitUntil("A receives B's record") { container.database.recordDao().getById("live-b1")?.authorUid == uidB }

            // owner removes B → B loses access to data and files
            assertEquals(FamilyResult.Ok, container.cloudSync.removeMember(uidB))
            waitUntil("B locked out of data") { runCatching { dbB.document("groups/$groupA/pets/${pet.id}").get().await() }.isFailure }
            assertTrue(runCatching { storageB.reference.child("groups/$groupA/files/$photoName").getBytes(1_000_000).await() }.isFailure)
        } finally {
            container.cloudSync.disable()
            runCatching { container.database.recordDao().deletePermanently("live-b1") }
            container.petRepository.moveToTrash(pet.id)
            runCatching { container.database.petDao().deletePermanently(pet.id) }
            container.photoStore.file(photoName).delete()
            val db = FirebaseFirestore.getInstance()
            if (groupA != null) {
                runCatching { FirebaseStorage.getInstance().reference.child("groups/$groupA/files/$photoName").delete().await() }
                for (t in listOf("pets", "records", "reminders", "supplements", "invites", "members")) {
                    runCatching { db.collection("groups/$groupA/$t").get().await().documents.forEach { it.reference.delete().await() } }
                }
                runCatching { db.document("groups/$groupA").delete().await() }
            }
            uidA?.let { runCatching { db.document("users/$it").delete().await() } }
            runCatching { authA.currentUser?.delete()?.await() }
            runCatching { authB.currentUser?.delete()?.await() }
            authB.signOut()
        }
    }

    @Test fun thisPhoneJoinsAnotherGroupThenLeavesAndKeepsItsData() = runBlocking {
        val authA = FirebaseAuth.getInstance()
        authA.signOut()
        val appB = secondPhone()
        val authB = FirebaseAuth.getInstance(appB)
        val dbB = FirebaseFirestore.getInstance(appB)
        val myPet = container.petRepository.add(PetInput(name = "我的貓", species = Species.CAT, birthEpochDay = 19000))
        val groupB = java.util.UUID.randomUUID().toString()
        var uidB: String? = null
        var uidA: String? = null
        var privateGroupA: String? = null
        try {
            // phone B owns a group that already has a pet and creates an invitation
            uidB = authB.signInAnonymously().await().user!!.uid
            val now = System.currentTimeMillis()
            dbB.document("groups/$groupB").set(mapOf("ownerUid" to uidB, "ownerName" to "媽媽", "memberUids" to listOf(uidB), "createdAt" to now, "updatedAt" to now)).await()
            dbB.document("users/$uidB").set(mapOf("groupId" to groupB, "updatedAt" to now)).await()
            dbB.document("groups/$groupB/pets/pet-of-b").set(
                mapOf("id" to "pet-of-b", "name" to "媽媽的狗", "species" to "DOG", "birthEpochDay" to 19000L, "createdAt" to now, "updatedAt" to now,
                    "serverUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
            ).await()
            val invite = FamilyService(dbB, Clock.systemDefaultZone()).createInvite(groupB)

            // this phone: sync on (private group), then join B's group with the invitation text
            container.cloudSync.enable()
            waitUntil("A active") { container.cloudSync.status.value is SyncStatus.Active && container.cloudSync.family.value != null }
            uidA = authA.currentUser!!.uid
            privateGroupA = container.cloudSync.family.value!!.groupId
            assertEquals(FamilyResult.Ok, container.cloudSync.joinFamily("來加入我們：${invite.link.asText()}", "爸爸"))

            waitUntil("A is now in B's group with two people") { container.cloudSync.family.value?.let { it.groupId == groupB && it.members.size == 2 } == true }
            waitUntil("B's pet arrives on this phone") { container.petRepository.get("pet-of-b")?.name == "媽媽的狗" }
            waitUntil("my pet is uploaded into B's group") { dbB.document("groups/$groupB/pets/${myPet.id}").get().await().exists() }
            assertEquals(mapOf(uidB!! to "媽媽", uidA to "爸爸"), container.cloudSync.family.value!!.members.associate { it.uid to it.name })

            // a second invitation cannot be used while already in a group with people
            assertTrue(container.cloudSync.joinFamily(invite.link.asText(), "x") is FamilyResult.Failed)

            // leaving: back to a private group; everything stays on the phone
            assertEquals(FamilyResult.Ok, container.cloudSync.leaveFamily())
            waitUntil("back in a private group") { container.cloudSync.family.value?.let { it.groupId != groupB && it.members.size == 1 } == true }
            assertEquals("媽媽的狗", container.petRepository.get("pet-of-b")?.name)
            assertEquals("我的貓", container.petRepository.get(myPet.id)?.name)
            waitUntil("my pet is uploaded to the new private group") {
                FirebaseFirestore.getInstance().document("groups/${container.cloudSync.family.value!!.groupId}/pets/${myPet.id}").get().await().exists()
            }
            assertTrue("no access to B's group any more", runCatching { FirebaseFirestore.getInstance().document("groups/$groupB/pets/pet-of-b").get().await() }.isFailure)
        } finally {
            val newPrivate = container.cloudSync.family.value?.groupId
            container.cloudSync.disable()
            container.petRepository.moveToTrash(myPet.id)
            for (id in listOf(myPet.id, "pet-of-b")) runCatching { container.database.petDao().deletePermanently(id) }
            val db = FirebaseFirestore.getInstance()
            for (g in listOfNotNull(privateGroupA, newPrivate).distinct()) {
                for (t in listOf("pets", "records", "reminders", "supplements", "invites", "members")) {
                    runCatching { db.collection("groups/$g/$t").get().await().documents.forEach { it.reference.delete().await() } }
                }
                runCatching { db.document("groups/$g").delete().await() }
            }
            uidA?.let { runCatching { db.document("users/$it").delete().await() } }
            for (t in listOf("pets", "records", "reminders", "supplements", "invites", "members")) {
                runCatching { dbB.collection("groups/$groupB/$t").get().await().documents.forEach { it.reference.delete().await() } }
            }
            runCatching { dbB.document("groups/$groupB").delete().await() }
            uidB?.let { runCatching { dbB.document("users/$it").delete().await() } }
            runCatching { authA.currentUser?.delete()?.await() }
            runCatching { authB.currentUser?.delete()?.await() }
            authB.signOut()
        }
    }
}
