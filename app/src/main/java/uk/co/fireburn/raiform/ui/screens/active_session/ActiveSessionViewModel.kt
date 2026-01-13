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
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.ManageSessionUseCase
import javax.inject.Inject

sealed class ActiveSessionEvent {
    object TimerFinished : ActiveSessionEvent()
}

data class ActiveSessionUiState(
    val session: Session? = null,
    val isLoading: Boolean = true,
    val timerValue: Int = 60,
    val timerTotalTime: Int = 60,
    val isTimerRunning: Boolean = false,
    val allExerciseNames: List<String> = emptyList()
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

    private val _events = Channel<ActiveSessionEvent>()
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        loadSession()
        loadExerciseNames()
    }

    private fun loadSession() {
        viewModelScope.launch {
            repository.getSession(sessionId).collect { session ->
                _uiState.update { it.copy(session = session, isLoading = false) }
            }
        }
    }

    private fun loadExerciseNames() {
        viewModelScope.launch {
            repository.getAllExerciseNames().collect { names ->
                _uiState.update { it.copy(allExerciseNames = names) }
            }
        }
    }

    // NEW: Fetch existing stats to pre-fill the dialog
    fun getExistingStatsForExercise(name: String, callback: (Double, Int, Int, Boolean) -> Unit) {
        viewModelScope.launch {
            val stats = repository.findExerciseStats(clientId, name)
            if (stats != null) {
                val (weight, sets, reps) = stats
                val isBw = (weight == 0.0)
                callback(weight, sets, reps, isBw)
            }
        }
    }

    // --- Timer Logic ---

    fun startTimer(seconds: Int) {
        timerJob?.cancel()

        _uiState.update {
            it.copy(
                isTimerRunning = true,
                timerValue = seconds,
                timerTotalTime = seconds
            )
        }

        timerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + (seconds * 1000L)

            while (true) {
                val currentTime = System.currentTimeMillis()
                val remainingMillis = endTime - currentTime
                val remainingSeconds = (remainingMillis / 1000).toInt()

                if (remainingSeconds <= 0) {
                    _uiState.update { it.copy(timerValue = 0, isTimerRunning = false) }
                    _events.send(ActiveSessionEvent.TimerFinished)
                    break
                }

                _uiState.update { it.copy(timerValue = remainingSeconds) }
                delay(200)
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false) }
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
