package uk.co.fireburn.raiform.ui.screens.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val graphData: Map<String, List<Pair<Long, Double>>> = emptyMap(), // Exercise -> List of (Date, Weight)
    val exerciseNames: List<String> = emptyList(),
    val selectedGraphExercise: String? = null,
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
            repository.getHistoryForClient(clientId).collect { historyLogs ->

                // Flatten exercises from all history logs
                // HistoryLog contains a snapshot of exercises at that specific time
                val allExercisesWithDate = historyLogs.flatMap { log ->
                    log.exercises.filter { it.isDone }.map { exercise ->
                        exercise to log.dateLogged
                    }
                }

                // 1. Total Volume & Reps
                var volume = 0.0
                var reps = 0
                allExercisesWithDate.forEach { (ex, _) ->
                    reps += (ex.sets * ex.reps)
                    if (!ex.isBodyweight) {
                        volume += (ex.weight * ex.sets * ex.reps)
                    }
                }

                // 2. Personal Bests
                val pbs = allExercisesWithDate
                    .groupBy { it.first.name.trim().lowercase() }
                    .mapNotNull { (_, list) ->
                        // Find max weight
                        val maxEntry = list.maxByOrNull { it.first.weight }
                        maxEntry?.let { (ex, date) ->
                            PersonalBest(ex.name, ex.weight, ex.isBodyweight, date)
                        }
                    }
                    .sortedByDescending { it.weight }

                // 3. Graph Data
                val graphMap = allExercisesWithDate
                    .groupBy { it.first.name }
                    .mapValues { (_, list) ->
                        list.sortedBy { it.second }
                            .map { (ex, date) -> date to ex.weight }
                    }

                val exerciseList = graphMap.keys.sorted()

                // Preserve selected exercise if it still exists, otherwise pick first
                val currentSelection = _uiState.value.selectedGraphExercise
                val newSelection =
                    if (currentSelection in exerciseList) currentSelection else exerciseList.firstOrNull()

                _uiState.update {
                    it.copy(
                        totalVolumeKg = volume,
                        totalReps = reps,
                        funFact = generateFunFact(volume, reps),
                        personalBests = pbs,
                        graphData = graphMap,
                        exerciseNames = exerciseList,
                        selectedGraphExercise = newSelection,
                        isLoading = false
                    )
                }
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
