package com.zylinn.petdiary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.Supplement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupplementsViewModel(private val records: RecordRepository) : ViewModel() {

    val items: StateFlow<List<Supplement>> = records.supplementsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    /** One-line problem shown under the input ("已經有同名的了"). */
    val message: StateFlow<String?> = _message

    private val _justDeleted = MutableStateFlow<Supplement?>(null)
    /** For the 5-second "復原" snackbar (same pattern as deleting records). */
    val justDeleted: StateFlow<Supplement?> = _justDeleted

    fun clearMessage() {
        _message.value = null
    }

    fun add(name: String, onAdded: () -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // addSupplement returns the existing one when the name is taken.
            val before = items.value.any { it.name == trimmed }
            if (before) {
                _message.value = "已經有「$trimmed」了"
            } else {
                records.addSupplement(trimmed.take(50))
                _message.value = null
                onAdded()
            }
        }
    }

    fun rename(id: String, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (records.renameSupplement(id, name)) {
                _message.value = null
                onDone()
            } else {
                _message.value = if (name.isBlank()) "名稱不能是空的" else "已經有「${name.trim()}」了"
            }
        }
    }

    fun delete(item: Supplement) {
        viewModelScope.launch {
            records.deleteSupplement(item.id)
            _justDeleted.value = item
        }
    }

    fun undo(item: Supplement) {
        viewModelScope.launch { records.restoreSupplement(item.id) }
        _justDeleted.value = null
    }

    fun deleteAcknowledged() {
        _justDeleted.value = null
    }
}
