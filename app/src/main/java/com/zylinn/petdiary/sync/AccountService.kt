package com.zylinn.petdiary.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Optional Google account on top of the anonymous cloud identity (backup + change of phone), and deleting everything
 * the cloud holds about me. Linking keeps the same cloud user id, so nothing has to move.
 */
class AccountService(private val context: Context, private val cloud: CloudSync) : AccountController {

    private val _account = MutableStateFlow<AccountState?>(null)
    override val account: StateFlow<AccountState?> = _account.asStateFlow()

    init {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseAuth.getInstance().addAuthStateListener { auth -> publish(auth) }
        }
    }

    private fun publish(auth: FirebaseAuth) {
        val user = auth.currentUser
        _account.value = user?.let { u -> AccountState(u.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.email ?: u.email?.takeIf { it.isNotBlank() && !u.isAnonymous }) }
    }

    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    /** Shows the Google account picker; null when the user backs out. */
    private suspend fun pickGoogleIdToken(host: Context): String? {
        val clientId = webClientId() ?: error("這個版本沒有設定 Google 登入")
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).build()
        return try {
            val result = CredentialManager.create(host).getCredential(host, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(cred.data).idToken
            } else null
        } catch (e: GetCredentialCancellationException) {
            null
        }
    }

    override suspend fun linkGoogle(host: Context): AccountResult {
        if (FirebaseApp.getApps(context).isEmpty()) return AccountResult.Failed("這個版本沒有設定雲端")
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return AccountResult.Failed("請先開啟雲端備份與同步")
        val idToken = try {
            pickGoogleIdToken(host) ?: return AccountResult.Failed("已取消")
        } catch (e: GetCredentialException) {
            return AccountResult.Failed("無法開啟 Google 登入（${e.message ?: e.javaClass.simpleName}）")
        } catch (e: IllegalStateException) {
            return AccountResult.Failed(e.message ?: "無法開啟 Google 登入")
        }
        return try {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            publish(auth)
            AccountResult.Ok("已綁定 Google 帳號")
        } catch (e: FirebaseAuthUserCollisionException) {
            AccountResult.NeedsSwitch(idToken, e.email)
        } catch (e: Exception) {
            AccountResult.Failed("綁定失敗：${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun switchToGoogle(idToken: String): AccountResult = try {
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        cloud.userChanged()
        AccountResult.Ok("已改用這個 Google 帳號，正在合併資料")
    } catch (e: Exception) {
        AccountResult.Failed("登入失敗：${e.message ?: e.javaClass.simpleName}")
    }

    override suspend fun deleteCloudAccount(host: Context): AccountResult {
        if (FirebaseApp.getApps(context).isEmpty()) return AccountResult.Failed("這個版本沒有設定雲端")
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return AccountResult.Failed("目前沒有雲端帳號")
        val fam = cloud.family.value ?: return AccountResult.Failed("尚未連線，請稍後再試")
        val uid = user.uid
        cloud.disable() // stop uploading while we delete
        try {
            val db = FirebaseFirestore.getInstance()
            val group = db.collection("groups").document(fam.groupId)
            if (fam.iAmOwner) {
                // my group: its data, files and the group itself (other members, if any, fall back to their own private group)
                for (t in listOf("pets", "records", "reminders", "supplements", "toys", "categories", "invites", "members")) deleteCollection(group.collection(t))
                val files = FirebaseStorage.getInstance().reference.child("groups").child(fam.groupId).child("files").listAll().await()
                files.items.forEach { it.delete().await() }
                group.delete().await()
            } else {
                group.collection("members").document(uid).delete().await() // only leave; the family's data stays with them
            }
            db.collection("users").document(uid).delete().await()
            try {
                user.delete().await()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                val idToken = pickGoogleIdToken(host) ?: return AccountResult.Failed("需要重新驗證 Google 帳號才能完成刪除，已取消")
                user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
                user.delete().await()
            }
            auth.signOut()
            cloud.afterAccountDeleted()
            return AccountResult.Ok("已刪除雲端資料與帳號。你手機上的資料都還在。")
        } catch (e: Exception) {
            return AccountResult.Failed("刪除沒有完成：${e.message ?: e.javaClass.simpleName}（可以再試一次）")
        }
    }

    private suspend fun deleteCollection(col: com.google.firebase.firestore.CollectionReference) {
        val db = col.firestore
        while (true) {
            val docs = col.limit(300).get().await().documents
            if (docs.isEmpty()) return
            val batch = db.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
