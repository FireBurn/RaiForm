package uk.co.fireburn.raiform.ui.screens.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.ManageSessionUseCase
import javax.inject.Inject

data class SchedulerState(
    val clients: List<Client> = emptyList(),
    val allGlobalSessions: List<Session> = emptyList(),

    val selectedClient: Client? = null,
    val clientSessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,

    val selectedDay: Int = 1, // 1=Mon
    val selectedHour: Int = -1, // -1 = None

    val occupiedSlots: Map<Int, List<Int>> = emptyMap(),

    // New flag to indicate the entire process is finished
    val isAllSchedulingComplete: Boolean = false
)

@HiltViewModel
class MainSchedulerViewModel @Inject constructor(
    private val repository: RaiRepository,
    private val manageSessionUseCase: ManageSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SchedulerState())
    val state = _state.asStateFlow()

    // Keep track of the session collection job to cancel it when switching clients
    private var currentSessionsJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            combine(
                repository.getActiveClients(),
                repository.getAllSessions()
            ) { activeClients, allSessions ->
                Pair(activeClients, allSessions)
            }.collect { (clients, sessions) ->
                _state.update {
                    it.copy(
                        clients = clients,
                        allGlobalSessions = sessions
                    )
                }
                calculateOccupiedSlots()
            }
        }
    }

    private fun calculateOccupiedSlots() {
        val currentState = _state.value
        val allSessions = currentState.allGlobalSessions
        val activeClientIds = currentState.clients.map { it.id }.toSet()
        val map = mutableMapOf<Int, MutableList<Int>>()

        allSessions.forEach { session ->
            if (activeClientIds.contains(session.clientId)) {
                if (session.scheduledDay != null && session.scheduledHour != null && !session.isSkippedThisWeek) {
                    if (session.id != currentState.selectedSession?.id) {
                        val list = map.getOrPut(session.scheduledDay) { mutableListOf() }
                        list.add(session.scheduledHour)
                    }
                }
            }
        }
        _state.update { it.copy(occupiedSlots = map) }
    }

    fun selectClient(client: Client) {
        // Cancel previous collection to avoid race conditions
        currentSessionsJob?.cancel()

        // Reset complete flag when manually selecting a client
        _state.update {
            it.copy(
                selectedClient = client,
                selectedSession = null,
                isAllSchedulingComplete = false
            )
        }

        currentSessionsJob = viewModelScope.launch {
            repository.getSessionsForClient(client.id).collect { sessions ->
                val sortedSessions = sessions.sortedWith(
                    compareBy(
                        { it.scheduledDay ?: 8 },
                        { it.scheduledHour ?: 25 },
                        { it.scheduledMinute ?: 61 },
                        { it.name }
                    )
                )

                _state.update { it.copy(clientSessions = sortedSessions) }

                // Auto-select first session if none selected
                if (_state.value.selectedSession == null && sortedSessions.isNotEmpty()) {
                    selectSession(sortedSessions.first())
                }
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
        calculateOccupiedSlots()
    }

    fun selectDay(day: Int) {
        _state.update { it.copy(selectedDay = day) }
    }

    fun selectHour(hour: Int) {
        _state.update { it.copy(selectedHour = hour) }
        viewModelScope.launch {
            delay(300)
            saveSchedule()
        }
    }

    fun skipSession() {
        val s = _state.value
        val client = s.selectedClient ?: return
        val session = s.selectedSession ?: return

        viewModelScope.launch {
            manageSessionUseCase.toggleSkipSession(client.id, session)
            selectNextSession(session.id)
        }
    }

    fun moveToNextWeek() {
        skipSession()
    }

    private fun saveSchedule() {
        val s = _state.value
        if (s.selectedClient != null && s.selectedSession != null && s.selectedHour != -1) {
            viewModelScope.launch {
                manageSessionUseCase.updateSchedule(
                    s.selectedClient.id,
                    s.selectedSession,
                    s.selectedDay,
                    s.selectedHour,
                    0
                )
                selectNextSession(s.selectedSession.id)
            }
        }
    }

    private fun selectNextSession(currentId: String) {
        val currentState = _state.value
        val currentList = currentState.clientSessions
        val index = currentList.indexOfFirst { it.id == currentId }

        if (index != -1 && index < currentList.size - 1) {
            // 1. More sessions for THIS client
            val nextSession = currentList[index + 1]
            selectSession(nextSession)
        } else {
            // 2. Client finished, try to move to NEXT client
            val currentClient = currentState.selectedClient ?: return
            val clientList = currentState.clients
            val clientIndex = clientList.indexOfFirst { it.id == currentClient.id }

            if (clientIndex != -1 && clientIndex < clientList.size - 1) {
                // Select next client (This will trigger loading sessions and auto-selecting the first one)
                val nextClient = clientList[clientIndex + 1]
                selectClient(nextClient)
            } else {
                // 3. All clients finished!
                _state.update {
                    it.copy(
                        selectedClient = null,
                        selectedSession = null,
                        selectedHour = -1,
                        isAllSchedulingComplete = true
                    )
                }
            }
        }
    }
}
