package uk.co.fireburn.raiform.presentation.smart_import

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
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import uk.co.fireburn.raiform.util.LegacyParser
import javax.inject.Inject

data class ImportUiState(
    val rawText: String = "",
    val parsedClientName: String? = null,
    val parsedSessions: List<Session> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false // Trigger navigation back on true
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ClientRepository
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
            val result = LegacyParser.parseLegacyNote(text)
            _uiState.update {
                it.copy(
                    parsedClientName = result.clientName,
                    parsedSessions = result.sessions,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Parse Failed: ${e.message}") }
        }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val clientName = state.parsedClientName
        val sessions = state.parsedSessions

        if (clientName == null || sessions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Create the Client Object
                val client = Client(name = clientName)

                // Save to Firestore (Batch write)
                repository.saveClientWithSessions(client, sessions)

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