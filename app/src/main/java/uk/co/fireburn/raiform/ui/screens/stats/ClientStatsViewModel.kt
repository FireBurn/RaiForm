package uk.co.fireburn.raiform.ui.screens.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.BodyMeasurement
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import javax.inject.Inject

data class PersonalBest(
    val exerciseName: String,
    val weight: Double,
    val isBodyweight: Boolean,
    val date: Long
)

data class StatsUiState(
    val totalVolumeKg: Double = 0.0,
    val totalReps: Int = 0,
    val funFact: String = "",
    val personalBests: List<PersonalBest> = emptyList(),
    val graphData: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    val exerciseNames: List<String> = emptyList(),
    val selectedGraphExercise: String? = null,
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ClientStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RaiRepository
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Combine History, Active Sessions, AND Body Measurements
            combine(
                repository.getHistoryForClient(clientId),
                repository.getSessionsForClient(clientId),
                repository.getBodyMeasurements(clientId)
            ) { historyLogs, activeSessions, measurements ->

                // 1. Process Historical Data
                val historyExercises = historyLogs.flatMap { log ->
                    log.exercises.filter { it.isDone }.map { exercise ->
                        exercise to log.dateLogged
                    }
                }

                // 2. Process Active Data
                val activeExercises = activeSessions.flatMap { session ->
                    session.exercises.filter { it.isDone }.map { exercise ->
                        val date =
                            if (session.lastResetTimestamp > 0) session.lastResetTimestamp else System.currentTimeMillis()
                        exercise to date
                    }
                }

                val allExercisesWithDate = historyExercises + activeExercises

                // 3. Stats Calculations
                var volume = 0.0
                var reps = 0
                allExercisesWithDate.forEach { (ex, _) ->
                    reps += (ex.sets * ex.reps)
                    if (!ex.isBodyweight) {
                        volume += (ex.weight * ex.sets * ex.reps)
                    }
                }

                // 4. Personal Bests
                val pbs = allExercisesWithDate
                    .groupBy { it.first.name.trim().lowercase() }
                    .mapNotNull { (_, list) ->
                        val maxEntry = list.maxByOrNull { it.first.weight }
                        maxEntry?.let { (ex, date) ->
                            PersonalBest(ex.name, ex.weight, ex.isBodyweight, date)
                        }
                    }
                    .sortedByDescending { it.weight }

                // 5. Graph Data
                val graphMap = allExercisesWithDate
                    .groupBy { it.first.name }
                    .mapValues { (_, list) ->
                        list.sortedBy { it.second }
                            .map { (ex, date) -> date to ex.weight }
                    }

                val exerciseList = graphMap.keys.sorted()

                val currentSelection = _uiState.value.selectedGraphExercise
                val newSelection =
                    if (currentSelection in exerciseList) currentSelection else exerciseList.firstOrNull()

                StatsUiState(
                    totalVolumeKg = volume,
                    totalReps = reps,
                    funFact = generateFunFact(volume, reps),
                    personalBests = pbs,
                    graphData = graphMap,
                    exerciseNames = exerciseList,
                    selectedGraphExercise = newSelection,
                    bodyMeasurements = measurements, // Pass measurements to UI
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectGraphExercise(name: String) {
        _uiState.update { it.copy(selectedGraphExercise = name) }
    }

    private fun generateFunFact(kg: Double, reps: Int): String {
        return when {
            kg > 2000000 -> "You've lifted a Space Shuttle! 🚀"
            kg > 150000 -> "That's a Blue Whale! 🐋"
            kg > 6000 -> "You've lifted an African Elephant! 🐘"
            kg > 1500 -> "That's a Tesla Model S! 🚗"
            kg > 500 -> "You've lifted a Grand Piano! 🎹"
            reps > 10000 -> "Over 10,000 reps! Master level. 🥋"
            reps > 1000 -> "1,000 reps club! Keep grinding."
            else -> "Every rep counts towards the goal!"
        }
    }
}
