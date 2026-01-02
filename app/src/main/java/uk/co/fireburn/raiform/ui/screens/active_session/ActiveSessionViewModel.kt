package uk.co.fireburn.raiform.ui.screens.active_session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.ManageSessionUseCase
import javax.inject.Inject

// Add Event Sealed Class
sealed class ActiveSessionEvent {
    object TimerFinished : ActiveSessionEvent()
}

data class ActiveSessionUiState(
    val session: Session? = null,
    val isLoading: Boolean = true,
    val timerValue: Int = 60,
    val isTimerRunning: Boolean = false
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RaiRepository,
    private val manageSessionUseCase: ManageSessionUseCase
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    // Add Channel for Events
    private val _events = Channel<ActiveSessionEvent>()
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            repository.getSession(sessionId).collect { session ->
                _uiState.update { it.copy(session = session, isLoading = false) }
            }
        }
    }

    // --- Timer Logic ---

    fun toggleRestTimer() {
        if (_uiState.value.isTimerRunning) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerRunning = true, timerValue = 60) }

            while (_uiState.value.timerValue > 0) {
                delay(1000L)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }

            // Timer finished!
            _events.send(ActiveSessionEvent.TimerFinished)
            stopTimer()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false, timerValue = 60) }
    }

    // --- Existing Logic ---

    fun toggleExerciseDone(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            manageSessionUseCase.toggleExerciseDone(clientId, currentSession, exerciseId)
        }
    }

    fun toggleMaintainWeight(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            manageSessionUseCase.toggleMaintainWeight(clientId, currentSession, exerciseId)
        }
    }

    fun reorderExercises(newOrder: List<Exercise>) {
        val currentSession = _uiState.value.session ?: return
        val updatedSession = currentSession.copy(exercises = newOrder)
        _uiState.update { it.copy(session = updatedSession) }

        viewModelScope.launch {
            manageSessionUseCase.reorderExercises(clientId, currentSession, newOrder)
        }
    }

    fun addExercise(name: String, weight: Double, isBodyweight: Boolean, sets: Int, reps: Int) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            manageSessionUseCase.addExercise(
                clientId,
                currentSession,
                name,
                weight,
                isBodyweight,
                sets,
                reps
            )
        }
    }

    fun updateExerciseValues(
        exerciseId: String,
        name: String,
        newWeight: Double,
        isBodyweight: Boolean,
        newSets: Int,
        newReps: Int
    ) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            manageSessionUseCase.updateExercise(
                clientId,
                currentSession,
                exerciseId,
                name,
                newWeight,
                isBodyweight,
                newSets,
                newReps
            )
        }
    }

    fun deleteExercise(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        viewModelScope.launch {
            manageSessionUseCase.deleteExercise(clientId, currentSession, exerciseId)
        }
    }
}
