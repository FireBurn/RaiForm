package uk.co.fireburn.raiform.presentation.client_details

import androidx.lifecycle.SavedStateHandle
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
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class ClientDetailsUiState(
    val client: Client? = null,
    val sessions: List<Session> = emptyList(),
    val globalOccupiedSlots: Map<Int, List<Int>> = emptyMap(),
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
        loadGlobalSchedule()
    }

    private fun loadData() {
        viewModelScope.launch {
            val client = repository.getClientById(clientId)
            if (client == null) {
                _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                return@launch
            }
            _uiState.update { it.copy(client = client) }

            repository.getSessionsForClient(clientId).collect { sessions ->
                val sortedSessions = sessions.sortedWith(
                    compareBy(
                        { it.scheduledDay ?: 8 },
                        { it.scheduledHour ?: 25 },
                        { it.scheduledMinute ?: 61 },
                        { it.name }
                    )
                )

                val processedSessions = checkAndPerformWeeklyReset(client, sortedSessions)
                _uiState.update { it.copy(sessions = processedSessions, isLoading = false) }
            }
        }
    }

    private fun loadGlobalSchedule() {
        viewModelScope.launch {
            val allSessions = repository.getAllSessionsFromAllClients()
            val map = mutableMapOf<Int, MutableList<Int>>()
            allSessions.forEach { session ->
                if (session.scheduledDay != null && session.scheduledHour != null && !session.isSkippedThisWeek) {
                    val list = map.getOrPut(session.scheduledDay) { mutableListOf() }
                    list.add(session.scheduledHour)
                }
            }
            _uiState.update { it.copy(globalOccupiedSlots = map) }
        }
    }

    private suspend fun checkAndPerformWeeklyReset(
        client: Client,
        sessions: List<Session>
    ): List<Session> {
        val now = LocalDateTime.now()
        val targetDayOfWeek =
            DayOfWeek.of(if (client.weeklyResetDay == 0) 7 else client.weeklyResetDay)
        val lastResetDate = now.with(TemporalAdjusters.previousOrSame(targetDayOfWeek))
            .withHour(0).withMinute(0).withSecond(0)

        val lastResetMillis =
            lastResetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Check if reset is needed
        val needsReset = sessions.any { it.lastResetTimestamp < lastResetMillis }

        if (needsReset) {
            // 1. LOG HISTORY: Save the *current* state (which is the "completed" state of previous week)
            // We use System.currentTimeMillis() as the log date
            repository.logSessionHistory(client.id, sessions, System.currentTimeMillis())

            // 2. RESET SESSIONS
            val updatedSessions = sessions.map { session ->
                if (session.lastResetTimestamp < lastResetMillis) {
                    val resetExercises = session.exercises.map { it.copy(isDone = false) }
                    session.copy(
                        exercises = resetExercises,
                        isSkippedThisWeek = false,
                        tempRescheduleTimestamp = null,
                        lastResetTimestamp = System.currentTimeMillis() // Mark as reset
                    )
                } else {
                    session
                }
            }

            repository.updateClientSessionsOrder(client.id, updatedSessions)
            return updatedSessions
        }

        return sessions
    }

    // ... (Scheduling/CRUD methods remain the same) ...
    fun onReorderSessions(newOrder: List<Session>) {
        val oldOrder = _uiState.value.sessions
        val schedules =
            oldOrder.map { Triple(it.scheduledDay, it.scheduledHour, it.scheduledMinute) }
        val reorderedWithSwappedSchedules = newOrder.mapIndexed { index, session ->
            if (index < schedules.size) {
                val (day, hour, min) = schedules[index]
                session.copy(scheduledDay = day, scheduledHour = hour, scheduledMinute = min)
            } else {
                session
            }
        }
        _uiState.update { it.copy(sessions = reorderedWithSwappedSchedules) }
        viewModelScope.launch {
            repository.updateClientSessionsOrder(clientId, reorderedWithSwappedSchedules)
            loadGlobalSchedule()
        }
    }

    fun updateSchedule(session: Session, day: Int, hour: Int, minute: Int) {
        val updated =
            session.copy(scheduledDay = day, scheduledHour = hour, scheduledMinute = minute)
        updateSession(updated)
        loadGlobalSchedule()
    }

    fun toggleSkipSession(session: Session) {
        val updated = session.copy(isSkippedThisWeek = !session.isSkippedThisWeek)
        updateSession(updated)
        loadGlobalSchedule()
    }

    fun addSession(name: String) {
        updateSession(Session(name = name.toTitleCase()))
    }

    fun renameSession(session: Session, newName: String) {
        updateSession(session.copy(name = newName.toTitleCase()))
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(clientId, session.id)
            loadGlobalSchedule()
        }
    }

    private fun updateSession(session: Session) {
        viewModelScope.launch { repository.updateSession(clientId, session) }
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
