package uk.co.fireburn.raiform.presentation.active_session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import java.util.Locale
import javax.inject.Inject

data class ActiveSessionUiState(
    val session: Session? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: ClientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            repository.getSessionsForClient(clientId).collect { sessions ->
                val activeSession = sessions.find { it.id == sessionId }
                _uiState.update { it.copy(session = activeSession, isLoading = false) }
            }
        }
    }

    fun toggleExerciseDone(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        val updatedExercises = currentSession.exercises.map { exercise ->
            if (exercise.id == exerciseId) {
                exercise.copy(isDone = !exercise.isDone)
            } else {
                exercise
            }
        }
        val updatedSession = currentSession.copy(exercises = updatedExercises)
        _uiState.update { it.copy(session = updatedSession) }
        saveSession(updatedSession)
    }

    // NEW: Toggle Maintain Weight status
    fun toggleMaintainWeight(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        val updatedExercises = currentSession.exercises.map { exercise ->
            if (exercise.id == exerciseId) {
                exercise.copy(maintainWeight = !exercise.maintainWeight)
            } else {
                exercise
            }
        }
        val updatedSession = currentSession.copy(exercises = updatedExercises)
        _uiState.update { it.copy(session = updatedSession) }
        saveSession(updatedSession)
    }

    private fun saveSession(session: Session) {
        viewModelScope.launch {
            repository.updateSession(clientId, session)
        }
    }

    fun addExercise(name: String) {
        val currentSession = _uiState.value.session ?: return

        val newExercise = Exercise(
            name = name.toTitleCase(),
            weight = 0.0,
            sets = 3,
            reps = 10
        )

        val updatedList = currentSession.exercises + newExercise
        val updatedSession = currentSession.copy(exercises = updatedList)

        _uiState.update { it.copy(session = updatedSession) }
        saveSession(updatedSession)
    }

    fun updateExerciseValues(
        exerciseId: String,
        name: String,
        newWeight: Double,
        newSets: Int,
        newReps: Int
    ) {
        val currentSession = _uiState.value.session ?: return

        val updatedExercises = currentSession.exercises.map { exercise ->
            if (exercise.id == exerciseId) {
                exercise.copy(
                    name = name.toTitleCase(),
                    weight = newWeight,
                    sets = newSets,
                    reps = newReps
                )
            } else {
                exercise
            }
        }

        val updatedSession = currentSession.copy(exercises = updatedExercises)
        _uiState.update { it.copy(session = updatedSession) }
        saveSession(updatedSession)
    }

    fun deleteExercise(exerciseId: String) {
        val currentSession = _uiState.value.session ?: return
        val updatedList = currentSession.exercises.filter { it.id != exerciseId }
        val updatedSession = currentSession.copy(exercises = updatedList)
        _uiState.update { it.copy(session = updatedSession) }
        saveSession(updatedSession)
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}