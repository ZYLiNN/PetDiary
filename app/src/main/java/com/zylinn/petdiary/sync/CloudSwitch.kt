package com.zylinn.petdiary.sync

import kotlinx.coroutines.flow.StateFlow

/** The on / off switch for cloud backup & sync, as the settings screen sees it. */
interface CloudSwitch {
    val status: StateFlow<SyncStatus>
    fun enable()
    fun disable()
}
