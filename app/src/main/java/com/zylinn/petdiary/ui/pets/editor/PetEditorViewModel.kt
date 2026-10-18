package com.zylinn.petdiary.ui.pets.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.pet.Sex
import com.zylinn.petdiary.data.photo.PhotoImporter
import com.zylinn.petdiary.data.photo.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate

data class PetEditorState(
    val isEdit: Boolean,
    /** Seed for the placeholder avatar color; the pet id when editing so it matches the home tile. */
    val avatarSeed: String? = null,
    val loading: Boolean,
    val form: PetForm = PetForm(),
    val showMore: Boolean = false,
    val importingPhoto: Boolean = false,
    val photoError: String? = null,
    val saving: Boolean = false,
    /** Form content when the editor opened; used to ask before throwing away changes. */
    val initialForm: PetForm = PetForm(),
) {
    val dirty get() = form != initialForm
}

/** Add (petId == null) or edit a pet. Survives fold / unfold because it lives in a ViewModel. */
class PetEditorViewModel(
    private val petId: String?,
    private val repository: PetRepository,
    private val photos: PhotoStore,
    private val importer: PhotoImporter,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(PetEditorState(isEdit = petId != null, avatarSeed = petId, loading = petId != null))
    val state: StateFlow<PetEditorState> = _state.asStateFlow()

    /** Photo the pet had when the editor opened; deleted only after a successful save that replaced it. */
    private var originalPhoto: String? = null

    /** Photos imported during this session; any not kept are cleaned up. */
    private val importedPhotos = mutableSetOf<String>()

    init {
        if (petId != null) viewModelScope.launch {
            val pet = repository.get(petId)
            originalPhoto = pet?.photoFile
            _state.update {
                if (pet == null) it.copy(loading = false)
                else it.copy(
                    loading = false,
                    form = PetForm.from(pet),
                    initialForm = PetForm.from(pet),
                    // Open "更多資料" when the pet already has optional details filled in.
                    showMore = listOf(pet.breed, pet.chipNumber, pet.note).any { v -> v != null } ||
                        pet.neutered != null || pet.sex != Sex.UNKNOWN,
                )
            }
        }
    }

    fun today(): LocalDate = LocalDate.now(clock)

    fun edit(transform: (PetForm) -> PetForm) = _state.update { it.copy(form = transform(it.form)) }

    fun toggleMore() = _state.update { it.copy(showMore = !it.showMore) }

    fun photoFile(name: String): File = photos.file(name)

    fun importPhoto(uri: Uri) {
        _state.update { it.copy(importingPhoto = true, photoError = null) }
        viewModelScope.launch {
            val name = runCatching { importer.import(uri) }.getOrNull()
            if (name != null) importedPhotos += name
            _state.update {
                if (name == null) it.copy(importingPhoto = false, photoError = "無法讀取這張照片")
                else it.copy(importingPhoto = false, form = it.form.copy(photoFile = name))
            }
        }
    }

    fun removePhoto() = edit { it.copy(photoFile = null) }

    /** Saves and calls [onSaved] with the pet id; does nothing if the form is invalid. */
    fun save(onSaved: (String) -> Unit) {
        val current = _state.value
        if (current.saving || !current.form.canSave(today())) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val input = current.form.toInput(today())
            val saved = if (petId == null) repository.add(input) else repository.update(petId, input)
            val kept = current.form.photoFile
            if (originalPhoto != null && originalPhoto != kept) photos.delete(originalPhoto!!)
            (importedPhotos - setOfNotNull(kept)).forEach(photos::delete)
            importedPhotos.clear()
            _state.update { it.copy(saving = false) }
            onSaved(saved?.id ?: petId.orEmpty())
        }
    }

    /** Leaving without saving: throw away photos imported in this session. */
    fun discard() {
        importedPhotos.forEach(photos::delete)
        importedPhotos.clear()
    }
}
