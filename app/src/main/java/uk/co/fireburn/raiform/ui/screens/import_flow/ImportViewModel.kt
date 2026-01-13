package uk.co.fireburn.raiform.ui.screens.import_flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val sessionFrequency: Int = 1,
    val isSingleSessionMode: Boolean = false,
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

    // Toggle handler
    fun toggleSingleSessionMode(isEnabled: Boolean) {
        _uiState.update { it.copy(isSingleSessionMode = isEnabled) }
    }

    fun onParseClicked() {
        val text = _uiState.value.rawText
        val combine = _uiState.value.isSingleSessionMode

        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Please paste text first.") }
            return
        }

        try {
            // Pass the combine flag to preview
            val result = importLegacyNoteUseCase.preview(text, combine)
            _uiState.update {
                it.copy(
                    parsedClientName = result.clientName,
                    parsedSessions = result.sessions,
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
        val combine = _uiState.value.isSingleSessionMode

        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1. Get parsed result (re-parsing ensures state consistency)
                val parseResult = importLegacyNoteUseCase.preview(text, combine)

                // 2. Create and Save Client
                val client = Client(name = parseResult.clientName)
                repository.saveClient(client)

                // 3. Handle Sessions logic
                if (parseResult.sessions.size == 1 && frequency > 1) {
                    // Logic: Replicate the single session X times
                    val baseSession = parseResult.sessions.first()
                    val groupId = UUID.randomUUID().toString()

                    for (i in 1..frequency) {
                        val sessionName = if (combine) "Full Routine $i" else "Session $i"
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

    fun resetState() {
        _uiState.value = ImportUiState()
    }
}
