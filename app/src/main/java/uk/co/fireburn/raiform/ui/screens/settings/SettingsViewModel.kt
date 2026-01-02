package uk.co.fireburn.raiform.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import uk.co.fireburn.raiform.domain.usecase.ExportDataUseCase
import uk.co.fireburn.raiform.domain.usecase.ImportDataUseCase
import uk.co.fireburn.raiform.util.AlarmScheduler
import javax.inject.Inject

sealed class SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent()
    data class LaunchExportFilePicker(val fileName: String, val mimeType: String) : SettingsEvent()
    data class LaunchImportFilePicker(val mimeType: String) : SettingsEvent()
}

data class SettingsState(
    val schedulingDay: Int = 7,
    val schedulingHour: Int = 9,
    val schedulingMinute: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val exportDataUseCase: ExportDataUseCase, // Inject new use cases
    private val importDataUseCase: ImportDataUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        // Ensure alarm is synced on app launch/viewmodel creation
        viewModelScope.launch {
            combine(
                settingsRepository.schedulingDay,
                settingsRepository.schedulingHour,
                settingsRepository.schedulingMinute
            ) { day, hour, minute ->
                SettingsState(day, hour, minute)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())
                .collect { settingState ->
                    _state.update {
                        it.copy(
                            schedulingDay = settingState.schedulingDay,
                            schedulingHour = settingState.schedulingHour,
                            schedulingMinute = settingState.schedulingMinute
                        )
                    }
                    alarmScheduler.schedule(
                        settingState.schedulingDay,
                        settingState.schedulingHour,
                        settingState.schedulingMinute
                    )
                }
        }
    }

    fun updateSchedulingDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingDay(day)
            val currentState = state.value
            alarmScheduler.schedule(day, currentState.schedulingHour, currentState.schedulingMinute)
        }
    }

    fun updateSchedulingTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingTime(hour, minute)
            val currentState = state.value
            alarmScheduler.schedule(currentState.schedulingDay, hour, minute)
        }
    }

    fun onExportDataClicked() {
        viewModelScope.launch {
            _events.emit(
                SettingsEvent.LaunchExportFilePicker(
                    "raiform_backup_${System.currentTimeMillis()}.json",
                    "application/json"
                )
            )
        }
    }

    fun onImportDataClicked() {
        viewModelScope.launch {
            _events.emit(
                SettingsEvent.LaunchImportFilePicker(
                    "application/json"
                )
            )
        }
    }

    fun exportData(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch { _events.emit(SettingsEvent.ShowToast("Export cancelled")) }
            return
        }
        _state.update { it.copy(isLoading = true, message = "Exporting data...") }
        viewModelScope.launch {
            exportDataUseCase(uri)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, message = null) }
                    _events.emit(SettingsEvent.ShowToast("Data exported successfully!"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, message = null) }
                    _events.emit(SettingsEvent.ShowToast("Export failed: ${e.message}"))
                    e.printStackTrace()
                }
        }
    }

    fun importData(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch { _events.emit(SettingsEvent.ShowToast("Import cancelled")) }
            return
        }
        _state.update { it.copy(isLoading = true, message = "Importing data...") }
        viewModelScope.launch {
            importDataUseCase(uri)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, message = null) }
                    _events.emit(SettingsEvent.ShowToast("Data imported successfully!"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, message = null) }
                    _events.emit(SettingsEvent.ShowToast("Import failed: ${e.message}"))
                    e.printStackTrace()
                }
        }
    }
}
