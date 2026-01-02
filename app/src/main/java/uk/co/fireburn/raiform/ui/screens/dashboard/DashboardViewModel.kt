package uk.co.fireburn.raiform.ui.screens.dashboard

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
import uk.co.fireburn.raiform.domain.usecase.GetDashboardDataUseCase
import uk.co.fireburn.raiform.domain.usecase.ManageClientUseCase
import javax.inject.Inject

data class DashboardUiState(
    val clients: List<Client> = emptyList(),
    val clientScheduleStatus: Map<String, String> = emptyMap(),
    val nextGlobalSessionClient: String? = null,
    val nextGlobalSessionTime: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase,
    private val manageClientUseCase: ManageClientUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        subscribeToDashboardData()
    }

    // Called on Resume to ensure relative time strings (e.g. "Today/Tomorrow") update
    // even if database data hasn't changed.
    fun refresh() {
        // Since we are using a Flow, simply re-collecting or notifying isn't straightforward 
        // without a trigger. However, for this architecture, usually data changes drive UI.
        // If the app was backgrounded for a day, the Flow might not emit new strings 
        // until the Repo emits new data. 
        // For simplicity in this architecture, we rely on the reactive stream. 
        // If strictly needed, we could have a 'ticker' flow combined in the UseCase.
        // Re-subscribing is a cheap way to force re-calculation of relative time strings.
        subscribeToDashboardData()
    }

    private fun subscribeToDashboardData() {
        viewModelScope.launch {
            getDashboardDataUseCase()
                .onStart {
                    _uiState.update { it.copy(isLoading = true) }
                }
                .catch { exception ->
                    _uiState.update {
                        it.copy(isLoading = false, error = exception.message ?: "Unknown Error")
                    }
                }
                .collect { data ->
                    _uiState.update {
                        it.copy(
                            clients = data.clients,
                            clientScheduleStatus = data.scheduleStatus,
                            nextGlobalSessionClient = data.nextSessionClientName,
                            nextGlobalSessionTime = data.nextSessionTimeFormatted,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun addClient(name: String) {
        viewModelScope.launch {
            try {
                manageClientUseCase.createClient(name)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun renameClient(client: Client, newName: String) {
        viewModelScope.launch {
            try {
                manageClientUseCase.renameClient(client, newName)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun archiveClient(client: Client) {
        viewModelScope.launch {
            try {
                manageClientUseCase.archiveClient(client)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
