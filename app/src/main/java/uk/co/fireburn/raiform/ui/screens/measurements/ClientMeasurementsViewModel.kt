package uk.co.fireburn.raiform.ui.screens.measurements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.BodyMeasurement
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import javax.inject.Inject

data class MeasurementsUiState(
    val history: List<BodyMeasurement> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ClientMeasurementsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RaiRepository
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(MeasurementsUiState())
    val uiState: StateFlow<MeasurementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getBodyMeasurements(clientId).collectLatest { list ->
                _uiState.update { it.copy(history = list, isLoading = false) }
            }
        }
    }

    fun addMeasurement(
        weight: String,
        shoulders: String,
        arms: String,
        waist: String,
        chest: String,
        legs: String
    ) {
        // Only save if at least one field is filled
        if (weight.isBlank() && shoulders.isBlank() && arms.isBlank() &&
            waist.isBlank() && chest.isBlank() && legs.isBlank()
        ) {
            return
        }

        val measurement = BodyMeasurement(
            clientId = clientId,
            dateRecorded = System.currentTimeMillis(),
            weightKg = weight.toDoubleOrNull(),
            shouldersCm = shoulders.toDoubleOrNull(),
            armsCm = arms.toDoubleOrNull(),
            waistCm = waist.toDoubleOrNull(),
            chestCm = chest.toDoubleOrNull(),
            legsCm = legs.toDoubleOrNull()
        )
        viewModelScope.launch {
            repository.saveBodyMeasurement(measurement)
        }
    }

    fun deleteMeasurement(id: String) {
        viewModelScope.launch {
            repository.deleteBodyMeasurement(id)
        }
    }
}
