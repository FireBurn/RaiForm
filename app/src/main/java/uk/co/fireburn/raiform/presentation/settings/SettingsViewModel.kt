package uk.co.fireburn.raiform.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun updateSchedulingDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingDay(day)
            rescheduleAlarm(day, state.value.schedulingHour, state.value.schedulingMinute)
        }
    }

    fun updateSchedulingTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingTime(hour, minute)
            rescheduleAlarm(state.value.schedulingDay, hour, minute)
        }
    }

    private fun rescheduleAlarm(day: Int, hour: Int, minute: Int) {
        alarmScheduler.schedule(day, hour, minute)
    }
}
