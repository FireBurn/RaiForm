package uk.co.fireburn.raiform.ui.screens.import_flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.BodyMeasurement
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.ImportLegacyNoteUseCase
import java.util.UUID
import javax.inject.Inject

data class ImportUiState(
    val rawText: String = "",
    val parsedClientName: String? = null,
    val parsedSessions: List<Session> = emptyList(),
    val measurementCount: Int = 0, // Visual feedback for measurements
    val sessionFrequency: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importLegacyNoteUseCase: ImportLegacyNoteUseCase,
    private val repository: RaiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onRawTextChanged(text: String) {
        _uiState.update { it.copy(rawText = text, error = null) }
    }

    fun onParseClicked() {
        val text = _uiState.value.rawText
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Please paste text first.") }
            return
        }

        try {
            val result = importLegacyNoteUseCase.preview(text)
            _uiState.update {
                it.copy(
                    parsedClientName = result.clientName,
                    parsedSessions = result.sessions,
                    measurementCount = result.measurements.size,
                    sessionFrequency = 1,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Parse Failed: ${e.message}") }
        }
    }

    fun increaseFrequency() {
        if (_uiState.value.parsedSessions.size == 1) {
            _uiState.update { it.copy(sessionFrequency = (it.sessionFrequency + 1).coerceAtMost(7)) }
        }
    }

    fun decreaseFrequency() {
        if (_uiState.value.parsedSessions.size == 1) {
            _uiState.update { it.copy(sessionFrequency = (it.sessionFrequency - 1).coerceAtLeast(1)) }
        }
    }

    fun onSaveClicked() {
        val text = _uiState.value.rawText
        val frequency = _uiState.value.sessionFrequency

        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1. Get Parse Result
                val parseResult = importLegacyNoteUseCase.preview(text)

                // 2. Save Client
                val client = Client(name = parseResult.clientName)
                repository.saveClient(client)

                // 3. Save Body Parts (Global)
                parseResult.exerciseBodyParts.forEach { (name, bodyPart) ->
                    repository.saveExerciseDefinition(name, bodyPart)
                }

                // 4. Save Measurements (Flattened)
                if (parseResult.measurements.isNotEmpty()) {
                    var weight: Double? = null
                    var waist: Double? = null
                    var chest: Double? = null
                    var arms: Double? = null
                    var legs: Double? = null
                    var shoulders: Double? = null

                    parseResult.measurements.forEach { raw ->
                        when {
                            raw.type.contains("Weight", true) -> weight = raw.value
                            raw.type.contains("Waist", true) -> waist = raw.value
                            raw.type.contains("Chest", true) -> chest = raw.value
                            raw.type.contains("Arm", true) -> arms = raw.value
                            raw.type.contains("Leg", true) -> legs = raw.value
                            raw.type.contains("Shoulder", true) -> shoulders = raw.value
                        }
                    }

                    val measurement = BodyMeasurement(
                        clientId = client.id,
                        dateRecorded = System.currentTimeMillis(),
                        weightKg = weight,
                        waistCm = waist,
                        chestCm = chest,
                        armsCm = arms,
                        legsCm = legs,
                        shouldersCm = shoulders
                    )
                    repository.saveBodyMeasurement(measurement)
                }

                // 5. Handle Sessions (Replication)
                if (parseResult.sessions.size == 1 && frequency > 1) {
                    val baseSession = parseResult.sessions.first()
                    val groupId = UUID.randomUUID().toString()

                    for (i in 1..frequency) {
                        val sessionName = "Session $i"
                        val newSession = baseSession.copy(
                            id = UUID.randomUUID().toString(),
                            clientId = client.id,
                            name = sessionName,
                            groupId = groupId,
                            exercises = baseSession.exercises.map {
                                it.copy(id = UUID.randomUUID().toString())
                            }
                        )
                        repository.saveSession(newSession)
                    }
                } else {
                    parseResult.sessions.forEach { session ->
                        repository.saveSession(session.copy(clientId = client.id))
                    }
                }

                repository.sync()
                _uiState.update { it.copy(isLoading = false, isSaved = true) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Save Error: ${e.message}") }
            }
        }
    }
}
