package uk.co.fireburn.raiform.ui.screens.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val isAllSchedulingComplete: Boolean = false,

    val detectedConflictName: String? = null
)

sealed class SchedulerEvent {
    data class ShowMessage(val message: String) : SchedulerEvent()
}

@HiltViewModel
class MainSchedulerViewModel @Inject constructor(
    private val repository: RaiRepository,
    private val manageSessionUseCase: ManageSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SchedulerState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SchedulerEvent>()
    val events: SharedFlow<SchedulerEvent> = _events.asSharedFlow()

    private var currentSessionsJob: Job? = null
    private var pendingAutoSelectSessionId: String? = null
    private var pendingConflictDay: Int? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            combine(
                repository.getActiveClients(),
                repository.getAllSessions()
            ) { activeClients, allSessions ->
                // Sort clients alphabetically for consistent order
                val sortedClients = activeClients.sortedBy { it.name }
                Pair(sortedClients, allSessions)
            }.collect { (clients, sessions) ->
                _state.update {
                    it.copy(
                        clients = clients,
                        allGlobalSessions = sessions
                    )
                }

                // AUTO-SELECT FIRST CLIENT if none selected AND we aren't finished
                // FIX: Added check for !isAllSchedulingComplete to prevent looping
                if (_state.value.selectedClient == null && clients.isNotEmpty() && !_state.value.isAllSchedulingComplete) {
                    selectClient(clients.first())
                }

                calculateOccupiedSlots()
                checkCurrentSelectionForConflict()
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

    private fun checkCurrentSelectionForConflict() {
        val s = _state.value
        val targetDay = s.selectedDay
        val targetHour = s.selectedHour
        val currentSessionId = s.selectedSession?.id

        if (targetHour == -1) {
            _state.update { it.copy(detectedConflictName = null) }
            return
        }

        val conflict = s.allGlobalSessions.find {
            it.scheduledDay == targetDay &&
                    it.scheduledHour == targetHour &&
                    it.id != currentSessionId &&
                    !it.isSkippedThisWeek &&
                    s.clients.any { c -> c.id == it.clientId }
        }

        if (conflict != null) {
            val clientName = s.clients.find { it.id == conflict.clientId }?.name ?: "Unknown"
            _state.update { it.copy(detectedConflictName = clientName) }
        } else {
            _state.update { it.copy(detectedConflictName = null) }
        }
    }

    fun selectClient(client: Client) {
        currentSessionsJob?.cancel()
        _state.update {
            it.copy(
                selectedClient = client,
                selectedSession = null,
                isAllSchedulingComplete = false,
                detectedConflictName = null
            )
        }

        currentSessionsJob = viewModelScope.launch {
            repository.getSessionsForClient(client.id).collect { sessions ->
                // Sort Logic:
                // 1. Scheduled Day (1-7). Unscheduled = 8.
                // 2. Scheduled Hour.
                // 3. Name (Alphabetical).
                val sortedSessions = sessions.sortedWith(
                    compareBy(
                        { it.scheduledDay ?: 8 },
                        { it.scheduledHour ?: 25 },
                        { it.name }
                    )
                )

                _state.update { it.copy(clientSessions = sortedSessions) }

                if (pendingAutoSelectSessionId != null) {
                    val target = sortedSessions.find { it.id == pendingAutoSelectSessionId }
                    if (target != null) {
                        selectSession(target)
                        if (pendingConflictDay != null) {
                            _state.update { it.copy(selectedDay = pendingConflictDay!!) }
                            pendingConflictDay = null
                        }
                        pendingAutoSelectSessionId = null
                    } else if (_state.value.selectedSession == null && sortedSessions.isNotEmpty()) {
                        selectSession(sortedSessions.first())
                    }
                } else if (_state.value.selectedSession == null && sortedSessions.isNotEmpty()) {
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
        checkCurrentSelectionForConflict()
    }

    fun selectDay(day: Int) {
        _state.update { it.copy(selectedDay = day) }
        calculateOccupiedSlots()
        checkCurrentSelectionForConflict()
    }

    fun selectHour(hour: Int) {
        _state.update { it.copy(selectedHour = hour) }
        checkCurrentSelectionForConflict()
    }

    fun confirmSchedule() {
        viewModelScope.launch {
            performScheduleSave()
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

    fun previousClient() {
        val s = _state.value
        if (s.clients.isEmpty()) return
        val currentIndex = s.clients.indexOfFirst { it.id == s.selectedClient?.id }
        if (currentIndex > 0) {
            selectClient(s.clients[currentIndex - 1])
        }
    }

    fun nextClient() {
        val s = _state.value
        if (s.clients.isEmpty()) return
        val currentIndex = s.clients.indexOfFirst { it.id == s.selectedClient?.id }
        if (currentIndex != -1 && currentIndex < s.clients.size - 1) {
            selectClient(s.clients[currentIndex + 1])
        }
    }

    private suspend fun performScheduleSave() {
        val s = _state.value
        val currentClient = s.selectedClient ?: return
        val currentSession = s.selectedSession ?: return
        val targetDay = s.selectedDay
        val targetHour = s.selectedHour

        if (targetHour == -1) return

        val conflictingSession = s.allGlobalSessions.find {
            it.scheduledDay == targetDay &&
                    it.scheduledHour == targetHour &&
                    it.id != currentSession.id &&
                    !it.isSkippedThisWeek &&
                    s.clients.any { c -> c.id == it.clientId }
        }

        if (conflictingSession != null) {
            val conflictingClient = s.clients.find { it.id == conflictingSession.clientId }
            val conflictName = conflictingClient?.name ?: "Unknown"

            _events.emit(SchedulerEvent.ShowMessage("$conflictName bumped. Rescheduling them now..."))

            manageSessionUseCase.updateSchedule(
                currentClient.id,
                currentSession,
                targetDay,
                targetHour,
                0
            )

            val bumpedSession = conflictingSession.copy(
                scheduledDay = null, scheduledHour = null, scheduledMinute = null
            )
            repository.saveSession(bumpedSession)

            // Optimistic Update
            val updatedAllSessions = s.allGlobalSessions.map {
                if (it.id == currentSession.id) it.copy(
                    scheduledDay = targetDay,
                    scheduledHour = targetHour
                )
                else if (it.id == conflictingSession.id) bumpedSession
                else it
            }
            _state.update { it.copy(allGlobalSessions = updatedAllSessions) }
            calculateOccupiedSlots()

            pendingAutoSelectSessionId = conflictingSession.id
            pendingConflictDay = targetDay

            if (conflictingClient != null && conflictingClient.id != currentClient.id) {
                selectClient(conflictingClient)
            } else {
                selectSession(bumpedSession)
                _state.update { it.copy(selectedDay = targetDay) }
                pendingConflictDay = null
                pendingAutoSelectSessionId = null
            }

        } else {
            manageSessionUseCase.updateSchedule(
                currentClient.id,
                currentSession,
                targetDay,
                targetHour,
                0
            )

            val updatedAllSessions = s.allGlobalSessions.map {
                if (it.id == currentSession.id) it.copy(
                    scheduledDay = targetDay,
                    scheduledHour = targetHour
                )
                else it
            }
            _state.update { it.copy(allGlobalSessions = updatedAllSessions) }
            calculateOccupiedSlots()

            selectNextSession(currentSession.id)
        }
    }

    private fun selectNextSession(currentId: String) {
        val currentState = _state.value
        val currentList = currentState.clientSessions
        val index = currentList.indexOfFirst { it.id == currentId }

        if (index != -1 && index < currentList.size - 1) {
            // Next session for same client
            selectSession(currentList[index + 1])
        } else {
            // Next Client
            val clientIndex =
                currentState.clients.indexOfFirst { it.id == currentState.selectedClient?.id }
            if (clientIndex != -1 && clientIndex < currentState.clients.size - 1) {
                selectClient(currentState.clients[clientIndex + 1])
            } else {
                // All Done
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
