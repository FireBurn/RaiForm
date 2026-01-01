package uk.co.fireburn.raiform.presentation.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val allGlobalSessions: List<Session> = emptyList(),

    val selectedClient: Client? = null,
    val clientSessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,

    val selectedDay: Int = 1, // 1=Mon
    val selectedHour: Int = -1, // -1 = None

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
            if (session.scheduledDay != null && session.scheduledHour != null && !session.isSkippedThisWeek) {
                // If editing, don't show current session's OLD time as occupied
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
            repository.getSessionsForClient(client.id).collect { sessions ->
                // CHANGED: Sort sessions to ensure logical order in the Scheduler UI
                // 1. Scheduled (Day -> Hour -> Minute)
                // 2. Unscheduled (by Name)
                val sortedSessions = sessions.sortedWith(
                    compareBy(
                        { it.scheduledDay ?: 8 },     // Days 1-7 first, null (8) last
                        { it.scheduledHour ?: 25 },   // Hours 0-23 first, null (25) last
                        { it.scheduledMinute ?: 61 }, // Minutes 0-59 first
                        { it.name }
                    )
                )

                _state.update { it.copy(clientSessions = sortedSessions) }

                // Auto-select first unscheduled (or just first) session?
                // For now, let user pick, or auto-select first one.
                if (sortedSessions.isNotEmpty()) {
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
        // We do NOT auto-save on Day change, user must pick a time.
    }

    fun selectHour(hour: Int) {
        // 1. Visual Cue: Update UI immediately
        _state.update { it.copy(selectedHour = hour) }

        // 2. Trigger Auto-Save & Advance after short delay
        viewModelScope.launch {
            delay(300) // Visible delay for user to see selection
            saveSchedule()
        }
    }

    fun skipSession() {
        val s = _state.value
        val session = s.selectedSession ?: return

        val updated = session.copy(isSkippedThisWeek = true)

        viewModelScope.launch {
            repository.updateSession(s.selectedClient!!.id, updated)
            refreshGlobalSlots()
            selectNextSession(session.id)
        }
    }

    fun moveToNextWeek() {
        // Behaves like skip for now, but conceptually pushes it
        // We could implement specific "Next Week" logic here if model supports it.
        // For now, treating as Skip/Reschedule later.
        skipSession()
    }

    private fun saveSchedule() {
        val s = _state.value
        if (s.selectedClient != null && s.selectedSession != null && s.selectedHour != -1) {
            val updated = s.selectedSession.copy(
                scheduledDay = s.selectedDay,
                scheduledHour = s.selectedHour,
                scheduledMinute = 0,
                isSkippedThisWeek = false // Ensure it's active
            )

            viewModelScope.launch {
                repository.updateSession(s.selectedClient.id, updated)
                refreshGlobalSlots()
                selectNextSession(s.selectedSession.id)
            }
        }
    }

    private fun selectNextSession(currentId: String) {
        val currentList = _state.value.clientSessions
        val index = currentList.indexOfFirst { it.id == currentId }

        if (index != -1 && index < currentList.size - 1) {
            // Move to next
            val nextSession = currentList[index + 1]
            selectSession(nextSession)
        } else {
            // Done with this client
            _state.update { it.copy(selectedSession = null, selectedHour = -1) }
        }
    }
}
