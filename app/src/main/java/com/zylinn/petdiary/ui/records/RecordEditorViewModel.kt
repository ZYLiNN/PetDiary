package com.zylinn.petdiary.ui.records

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.photo.AttachmentImporter
import com.zylinn.petdiary.data.photo.FileStore
import com.zylinn.petdiary.data.record.Attachment
import com.zylinn.petdiary.data.record.BuiltInCategory
import com.zylinn.petdiary.data.record.BuiltInItem
import com.zylinn.petdiary.data.record.FAMILY_PET_ID
import com.zylinn.petdiary.data.record.NewAttachment
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.record.MAX_ATTACHMENTS
import com.zylinn.petdiary.data.record.NamedItem
import com.zylinn.petdiary.data.record.RecordType
import com.zylinn.petdiary.data.record.Supplement
import com.zylinn.petdiary.domain.RecordFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** One row in the attachment list: either already saved or picked in this session. */
data class AttachmentItem(
    val key: String,
    val displayName: String,
    val mimeType: String,
    val file: File,
    val sizeBytes: Long,
) {
    val isImage get() = mimeType.startsWith("image/")
}

data class RecordEditorState(
    val isEdit: Boolean,
    val loading: Boolean,
    val petNames: String = "",
    val form: RecordForm,
    val initialForm: RecordForm = form,
    val saving: Boolean = false,
    val existing: List<Attachment> = emptyList(),
    val added: List<NewAttachment> = emptyList(),
    val removedIds: Set<String> = emptySet(),
    val importing: Boolean = false,
    val attachmentError: String? = null,
) {
    val dirty get() = form != initialForm || added.isNotEmpty() || removedIds.isNotEmpty()
}

/**
 * New record(s) for [petIds] (one independent record per pet), or edit [recordId].
 * Date defaults to the day chosen on the detail page; time defaults to now (S1 / P2-05b).
 */
class RecordEditorViewModel(
    private val petIds: List<String>,
    type: RecordType,
    date: LocalDate,
    private val recordId: String?,
    pets: PetRepository,
    private val records: RecordRepository,
    private val clock: Clock,
    private val attachmentFiles: FileStore,
    private val importer: AttachmentImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecordEditorState(
            isEdit = recordId != null,
            loading = true,
            form = RecordForm(type = type, date = date, minuteOfDay = nowMinute()),
        ),
    )
    val state: StateFlow<RecordEditorState> = _state.asStateFlow()

    val supplements: StateFlow<List<Supplement>> =
        records.supplementsList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val toys: StateFlow<List<NamedItem>> =
        records.toysList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Purchase categories: the built-in ones first, then the user's own (P14-03). */
    val categories: StateFlow<List<NamedItem>> = records.categoriesList
        .map { own -> BuiltInCategory.entries.map { BuiltInItem(it.id, it.label) } + own }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuiltInCategory.entries.map { BuiltInItem(it.id, it.label) })

    /** A new purchase for several pets is stored once, for the whole family (D84). */
    private val sharedPurchase get() = recordId == null && _state.value.form.type == RecordType.PURCHASE && petIds.size > 1

    init {
        viewModelScope.launch {
            val existing = recordId?.let { records.get(it) }
            val names = if (existing?.isShared == true || (existing == null && type == RecordType.PURCHASE && petIds.size > 1)) RecordFormat.FAMILY_NAME
            else petIds.mapNotNull { pets.get(it)?.name }.joinToString("、")
            val files = recordId?.let { records.attachmentsOf(it) }.orEmpty()
            _state.update { s ->
                if (existing == null) s.copy(loading = false, petNames = names)
                else RecordForm.from(existing).let { f ->
                    s.copy(loading = false, petNames = names, form = f, initialForm = f, existing = files)
                }
            }
        }
    }

    private fun nowMinute() = LocalTime.now(clock).let { it.hour * 60 + it.minute }

    fun today(): LocalDate = LocalDate.now(clock)

    fun edit(transform: (RecordForm) -> RecordForm) = _state.update { it.copy(form = transform(it.form)) }

    /** S7: create a supplement right from the feeding form and select it. */
    fun addSupplement(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val s = records.addSupplement(name.take(TEXT_MAX))
            edit { it.copy(supplementIds = it.supplementIds + s.id) }
        }
    }

    /** D77: create a toy right from the play form and select it. */
    fun addToy(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val t = records.addToy(name.take(TEXT_MAX))
            edit { it.copy(toyIds = it.toyIds + t.id) }
        }
    }

    /** P14-03: create a purchase category right from the form and select it. */
    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val c = records.addCategory(name.take(TEXT_MAX))
            edit { it.copy(category = c.id) }
        }
    }

    /** Saved attachments not removed, followed by ones picked in this session. */
    fun attachmentItems(state: RecordEditorState): List<AttachmentItem> =
        state.existing.filter { it.id !in state.removedIds }.map {
            AttachmentItem(it.id, it.displayName, it.mimeType, attachmentFiles.file(it.fileName), it.sizeBytes)
        } + state.added.map {
            AttachmentItem(it.fileName, it.displayName, it.mimeType, attachmentFiles.file(it.fileName), it.sizeBytes)
        }

    fun importAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(importing = true, attachmentError = null) }
        viewModelScope.launch {
            var error: String? = null
            uris.forEach { uri ->
                // D78: at most MAX_ATTACHMENTS per record; the rest of a multi-pick is left out.
                if (attachmentItems(_state.value).size >= MAX_ATTACHMENTS) {
                    error = "每筆紀錄最多 $MAX_ATTACHMENTS 個附件"
                    return@forEach
                }
                when (val r = importer.import(uri)) {
                    is AttachmentImporter.Result.Ok -> _state.update { it.copy(added = it.added + r.attachment) }
                    AttachmentImporter.Result.TooLarge -> error = "檔案超過 10MB，無法加入"
                    AttachmentImporter.Result.Unsupported -> error = "只支援照片和 PDF"
                    AttachmentImporter.Result.Failed -> error = "無法讀取這個檔案"
                }
            }
            _state.update { it.copy(importing = false, attachmentError = error) }
        }
    }

    /** P2-19: removing one attachment never touches the others. Files are deleted on save. */
    fun removeAttachment(key: String) = _state.update { s ->
        val added = s.added.firstOrNull { it.fileName == key }
        if (added != null) {
            attachmentFiles.delete(added.fileName)
            s.copy(added = s.added - added)
        } else {
            s.copy(removedIds = s.removedIds + key)
        }
    }

    /** Leaving without saving: delete files picked in this session. */
    fun discard() {
        _state.value.added.forEach { attachmentFiles.delete(it.fileName) }
        _state.update { it.copy(added = emptyList()) }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving || !current.form.canSave(today())) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val input = current.form.toInput(today())
            val added = current.added
            if (recordId == null) {
                records.addForPets(if (sharedPurchase) listOf(FAMILY_PET_ID) else petIds, input, added)
            } else {
                records.update(recordId, input, added, current.removedIds.toList())
            }
            _state.update { it.copy(saving = false, added = emptyList(), removedIds = emptySet()) }
            onSaved()
        }
    }
}
