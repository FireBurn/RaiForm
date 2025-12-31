package uk.co.fireburn.raiform.presentation.client_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import java.util.Locale
import javax.inject.Inject

data class ClientDetailsUiState(
    val client: Client? = null,
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ClientDetailsViewModel @Inject constructor(
    private val repository: ClientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(ClientDetailsUiState())
    val uiState: StateFlow<ClientDetailsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val client = repository.getClientById(clientId)
                if (client == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                    return@launch
                }

                _uiState.update { it.copy(client = client) }

                repository.getSessionsForClient(clientId)
                    .catch { e ->
                        _uiState.update { it.copy(error = "Error loading sessions: ${e.message}") }
                    }
                    .collect { sessionList ->
                        _uiState.update {
                            it.copy(
                                sessions = sessionList,
                                isLoading = false
                            )
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not load data. Check internet connection.\n(${e.message})"
                    )
                }
            }
        }
    }

    fun addSession(name: String) {
        viewModelScope.launch {
            try {
                val newSession = Session(name = name.toTitleCase(), exercises = emptyList())
                repository.updateSession(clientId, newSession)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to add session: ${e.message}") }
            }
        }
    }

    fun renameSession(session: Session, newName: String) {
        viewModelScope.launch {
            try {
                val updatedSession = session.copy(name = newName.toTitleCase())
                repository.updateSession(clientId, updatedSession)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to rename: ${e.message}") }
            }
        }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            try {
                repository.deleteSession(clientId, session.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete session: ${e.message}") }
            }
        }
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
