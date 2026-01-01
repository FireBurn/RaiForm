package uk.co.fireburn.raiform.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val schedulingDay: StateFlow<Int> = settingsRepository.schedulingDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    fun updateSchedulingDay(day: Int) {
        viewModelScope.launch {
            settingsRepository.setSchedulingDay(day)
        }
    }
}
