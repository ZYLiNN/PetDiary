package com.zylinn.petdiary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zylinn.petdiary.data.export.CsvExport
import com.zylinn.petdiary.data.pet.PetRepository
import com.zylinn.petdiary.data.record.RecordRepository
import com.zylinn.petdiary.data.settings.AppSettings
import com.zylinn.petdiary.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    /** The file is ready; the screen hands it to the share sheet and then calls [SettingsViewModel.exportHandled]. */
    data class Ready(val file: File, val count: Int) : ExportState
    data object Empty : ExportState
}

class SettingsViewModel(
    private val settings: AppSettings,
    private val pets: PetRepository,
    private val records: RecordRepository,
    private val exportDir: File,
    private val clock: Clock,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    fun setTheme(mode: ThemeMode) = settings.setThemeMode(mode)

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    /** P6-03: writes every record of every living pet to a UTF-8 CSV (with BOM so Excel shows Chinese correctly). */
    fun exportCsv() {
        if (_export.value == ExportState.Working) return
        _export.value = ExportState.Working
        viewModelScope.launch {
            val all = records.allForExport()
            if (all.isEmpty()) {
                _export.value = ExportState.Empty
                return@launch
            }
            val petNames = pets.activePets.first().associate { it.id to it.name }
            val suppNames = records.supplementNames.first()
            val file = withContext(Dispatchers.IO) {
                exportDir.mkdirs()
                File(exportDir, "petdiary-${LocalDate.now(clock)}.csv").also { it.writeText(CsvExport.build(all, petNames, suppNames), Charsets.UTF_8) }
            }
            _export.value = ExportState.Ready(file, all.size)
        }
    }

    fun exportHandled() {
        _export.value = ExportState.Idle
    }
}
