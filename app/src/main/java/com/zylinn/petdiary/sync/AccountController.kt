package com.zylinn.petdiary.sync

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Who the cloud thinks I am. [googleEmail] is read from the sign-in itself and is never stored in our database. */
data class AccountState(val googleEmail: String?) {
    val isGoogle get() = googleEmail != null
}

sealed interface AccountResult {
    data class Ok(val message: String) : AccountResult
    data class Failed(val message: String) : AccountResult
    /** The chosen Google account already belongs to another cloud identity (e.g. used on an old phone): ask before switching. */
    data class NeedsSwitch(val idToken: String, val email: String?) : AccountResult
}

/** Google account linking and account deletion (P9); [CloudSync] / [AccountService] implement it, tests use a fake. */
interface AccountController {
    val account: StateFlow<AccountState?>
    /** [host] must be an Activity (the Google account picker is shown on top of it). */
    suspend fun linkGoogle(host: Context): AccountResult
    suspend fun switchToGoogle(idToken: String): AccountResult
    /** Deletes this identity's cloud data (its group, or just my membership), uploaded files and the cloud account itself. */
    suspend fun deleteCloudAccount(host: Context): AccountResult
}
