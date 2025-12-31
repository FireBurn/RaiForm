package uk.co.fireburn.raiform.presentation.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import javax.inject.Inject

data class SchedulerState(
    val clients: List<Client> = emptyList(),
    val allGlobalSessions: List<Session> = emptyList(), // Used for collision check

    val selectedClient: Client? = null,
    val clientSessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,

    val selectedDay: Int = 1, // 1=Mon
    val selectedHour: Int = -1, // -1 = None

    // Map of Day -> List of Taken Hours
    val occupiedSlots: Map<Int, List<Int>> = emptyMap()
)

@HiltViewModel
class MainSchedulerViewModel @Inject constructor(
    private val repository: ClientRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchedulerState())
    val state = _state.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // 1. Load Clients
            repository.getClients().collect { clients ->
                _state.update { it.copy(clients = clients) }
            }
        }
        refreshGlobalSlots()
    }

    private fun refreshGlobalSlots() {
        viewModelScope.launch {
            val allSessions = repository.getAllSessionsFromAllClients()
            _state.update { it.copy(allGlobalSessions = allSessions) }
            calculateOccupiedSlots()
        }
    }

    private fun calculateOccupiedSlots() {
        val all = _state.value.allGlobalSessions
        val map = mutableMapOf<Int, MutableList<Int>>()

        all.forEach { session ->
            // If session is scheduled and NOT skipped
            if (session.scheduledDay != null && session.scheduledHour != null && !session.isSkippedThisWeek) {
                // If we are editing a session, exclude it from collision check (don't block yourself)
                if (session.id != _state.value.selectedSession?.id) {
                    val list = map.getOrPut(session.scheduledDay) { mutableListOf() }
                    list.add(session.scheduledHour)
                }
            }
        }
        _state.update { it.copy(occupiedSlots = map) }
    }

    fun selectClient(client: Client) {
        viewModelScope.launch {
            _state.update { it.copy(selectedClient = client, selectedSession = null) }
            // Fetch sessions for this client specifically to list them
            repository.getSessionsForClient(client.id).collect { sessions ->
                _state.update { it.copy(clientSessions = sessions) }
            }
        }
    }

    fun selectSession(session: Session) {
        _state.update {
            it.copy(
                selectedSession = session,
                selectedDay = session.scheduledDay ?: 1,
                selectedHour = session.scheduledHour ?: -1
            )
        }
        // Recalculate slots so we don't show the current session as a conflict
        calculateOccupiedSlots()
    }

    fun selectDay(day: Int) {
        _state.update { it.copy(selectedDay = day) }
    }

    fun selectHour(hour: Int) {
        _state.update { it.copy(selectedHour = hour) }
    }

    fun saveSchedule() {
        val s = _state.value
        if (s.selectedClient != null && s.selectedSession != null && s.selectedHour != -1) {
            val updated = s.selectedSession.copy(
                scheduledDay = s.selectedDay,
                scheduledHour = s.selectedHour,
                scheduledMinute = 0 // Default to top of the hour
            )

            viewModelScope.launch {
                repository.updateSession(s.selectedClient.id, updated)
                // Reset selection or give feedback
                refreshGlobalSlots() // Refresh red/green logic
            }
        }
    }
}