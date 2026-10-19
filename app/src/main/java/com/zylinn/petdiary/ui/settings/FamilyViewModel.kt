package com.zylinn.petdiary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.sync.FamilyController
import com.zylinn.petdiary.sync.FamilyResult
import com.zylinn.petdiary.sync.FamilyState
import com.zylinn.petdiary.sync.Invite
import com.zylinn.petdiary.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FamilyViewModel(
    private val cloud: FamilyController,
    settings: AppSettings,
) : ViewModel() {

    val status: StateFlow<SyncStatus> = cloud.status
    val family: StateFlow<FamilyState?> = cloud.family
    val myName: StateFlow<String> = settings.displayName

    private val _invite = MutableStateFlow<Invite?>(null)
    val invite: StateFlow<Invite?> = _invite.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** One-shot text for the snackbar; the screen calls [messageShown] afterwards. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun messageShown() { _message.value = null }

    fun createInvite() = run {
        _invite.value = cloud.createInvite() ?: return@run "現在無法產生邀請，請確認網路與雲端同步狀態"
        null
    }

    fun reportScanProblem() { _message.value = "無法開啟掃描，請改用「貼上連結」" }

    fun hideInvite() { _invite.value = null }

    fun join(text: String, nickname: String) = run {
        when (val r = cloud.joinFamily(text, nickname)) {
            FamilyResult.Ok -> "已加入群組，你的資料正在上傳"
            is FamilyResult.Failed -> r.message
        }
    }

    fun leave() = run {
        when (val r = cloud.leaveFamily()) {
            FamilyResult.Ok -> "已離開群組，你手機上的資料都還在"
            is FamilyResult.Failed -> r.message
        }
    }

    fun remove(uid: String) = run {
        when (val r = cloud.removeMember(uid)) {
            FamilyResult.Ok -> "已移除成員"
            is FamilyResult.Failed -> r.message
        }
    }

    fun rename(name: String) = run {
        cloud.renameMe(name)
        "已更新名字"
    }

    private fun run(block: suspend () -> String?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try { block() } catch (e: Exception) { "操作失敗：${e.message ?: e.javaClass.simpleName}" }
            _busy.value = false
        }
    }
}
