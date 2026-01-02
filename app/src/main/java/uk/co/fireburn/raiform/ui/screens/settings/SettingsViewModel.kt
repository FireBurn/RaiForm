package uk.co.fireburn.raiform.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import uk.co.fireburn.raiform.util.AlarmScheduler
import javax.inject.Inject

data class SettingsState(
    val schedulingDay: Int = 7,
    val schedulingHour: Int = 9,
    val schedulingMinute: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settingsRepository.schedulingDay,
        settingsRepository.schedulingHour,
        settingsRepository.schedulingMinute
    ) { day, hour, minute ->
        SettingsState(day, hour, minute)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    init {
        // Ensure alarm is synced on app launch/viewmodel creation
        viewModelScope.launch {
            val day = settingsRepository.schedulingDay.first()
            val hour = settingsRepository.schedulingHour.first()
            val minute = settingsRepository.schedulingMinute.first()
            alarmScheduler.schedule(day, hour, minute)
        }
    }

    fun updateSchedulingDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingDay(day)
            // State flow updates asynchronously, so use the new 'day' directly
            // Note: state.value might be stale for a split second, so we pass the new value explicitly
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
}
