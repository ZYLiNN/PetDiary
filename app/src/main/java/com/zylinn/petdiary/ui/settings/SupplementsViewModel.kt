package com.zylinn.petdiary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.record.NameListOps
import com.zylinn.petdiary.data.record.NamedItem
import com.zylinn.petdiary.data.record.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages one user pick list: supplements (feeding) or, with [RecordRepository.toyOps], toys (play, D77). */
class SupplementsViewModel(private val ops: NameListOps) : ViewModel() {

    constructor(records: RecordRepository) : this(records.supplementOps)

    val items: StateFlow<List<NamedItem>> = ops.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    /** One-line problem shown under the input ("已經有同名的了"). */
    val message: StateFlow<String?> = _message

    private val _justDeleted = MutableStateFlow<NamedItem?>(null)
    /** For the 5-second "復原" snackbar (same pattern as deleting records). */
    val justDeleted: StateFlow<NamedItem?> = _justDeleted

    fun clearMessage() {
        _message.value = null
    }

    fun add(name: String, onAdded: () -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // add() returns the existing one when the name is taken.
            val before = items.value.any { it.name == trimmed }
            if (before) {
                _message.value = "已經有「$trimmed」了"
            } else {
                ops.add(trimmed.take(50))
                _message.value = null
                onAdded()
            }
        }
    }

    fun rename(id: String, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (ops.rename(id, name)) {
                _message.value = null
                onDone()
            } else {
                _message.value = if (name.isBlank()) "名稱不能是空的" else "已經有「${name.trim()}」了"
            }
        }
    }

    fun delete(item: NamedItem) {
        viewModelScope.launch {
            ops.delete(item.id)
            _justDeleted.value = item
        }
    }

    fun undo(item: NamedItem) {
        viewModelScope.launch { ops.restore(item.id) }
        _justDeleted.value = null
    }

    fun deleteAcknowledged() {
        _justDeleted.value = null
    }
}
