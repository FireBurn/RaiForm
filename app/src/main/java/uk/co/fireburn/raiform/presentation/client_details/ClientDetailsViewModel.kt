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
import javax.inject.Inject

data class ClientDetailsUiState(
    val client: Client? = null,
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null // Added error state
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
                // 1. Fetch Client Details (Safely)
                // If offline and not cached, this might throw. We catch it below.
                val client = repository.getClientById(clientId)

                if (client == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                    return@launch
                }

                _uiState.update { it.copy(client = client) }

                // 2. Observe Sessions (Real-time Flow)
                // Flows are usually safer, but we catch generic errors just in case
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
                // This catches the UnknownHostException (No Internet) crash
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not load data. Check internet connection.\n(${e.message})"
                    )
                }
            }
        }
    }
}