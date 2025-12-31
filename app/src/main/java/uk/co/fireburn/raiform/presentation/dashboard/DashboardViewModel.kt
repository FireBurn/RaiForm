package uk.co.fireburn.raiform.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import javax.inject.Inject

data class DashboardUiState(
    val clients: List<Client> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        fetchClients()
    }

    private fun fetchClients() {
        viewModelScope.launch {
            repository.getClients()
                .onStart {
                    _uiState.update { it.copy(isLoading = true) }
                }
                .catch { exception ->
                    _uiState.update {
                        it.copy(isLoading = false, error = exception.message ?: "Unknown Error")
                    }
                }
                .collect { clientList ->
                    _uiState.update {
                        it.copy(
                            clients = clientList.sortedByDescending { c -> c.dateAdded },
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    // Add a client manually
    fun addClient(name: String) {
        viewModelScope.launch {
            try {
                val newClient = Client(name = name)
                repository.saveClient(newClient)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error adding client: ${e.message}") }
            }
        }
    }

    // Rename existing client
    fun updateClientName(client: Client, newName: String) {
        viewModelScope.launch {
            try {
                val updatedClient = client.copy(name = newName)
                repository.saveClient(updatedClient)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error updating client: ${e.message}") }
            }
        }
    }

    fun archiveClient(client: Client) {
        viewModelScope.launch {
            try {
                repository.archiveClient(client.id)
                // No need to manually refresh; the Flow will emit the new list automatically!
            } catch (e: Exception) {
                // Ideally show a Snackbar event here
                _uiState.update { it.copy(error = "Could not archive: ${e.message}") }
            }
        }
    }
}
