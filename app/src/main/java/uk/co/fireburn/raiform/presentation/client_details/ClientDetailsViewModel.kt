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
    // Map of Day(1-7) -> List of Hours(0-23) that are busy globally
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
            // 1. Get Client
            val client = repository.getClientById(clientId)
            if (client == null) {
                _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                return@launch
            }
            _uiState.update { it.copy(client = client) }

            // 2. Get Sessions
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
                    // Important: We include ALL sessions here.
                    // If the user selects a session they are currently editing, we handle "ignoring self"
                    // in the UI or let them see it as occupied (which implies they are moving it).
                    // Ideally, we should filter out the *specific* session being edited, but we don't know
                    // which one is being edited in this generic load function.
                    // We will filter it in the Dialog or pass the ID to ignore.
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

        var updatesNeeded = false
        val updatedSessions = sessions.map { session ->
            if (session.lastResetTimestamp < lastResetMillis) {
                updatesNeeded = true
                val resetExercises = session.exercises.map { it.copy(isDone = false) }
                session.copy(
                    exercises = resetExercises,
                    isSkippedThisWeek = false,
                    tempRescheduleTimestamp = null,
                    lastResetTimestamp = System.currentTimeMillis()
                )
            } else {
                session
            }
        }

        if (updatesNeeded) {
            repository.updateClientSessionsOrder(client.id, updatedSessions)
        }
        return updatedSessions
    }

    // --- Scheduling & Reordering ---

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
            // Trigger global refresh to update occupied slots (in case we swapped times)
            loadGlobalSchedule()
        }
    }

    fun updateSchedule(session: Session, day: Int, hour: Int, minute: Int) {
        val updated =
            session.copy(scheduledDay = day, scheduledHour = hour, scheduledMinute = minute)
        updateSession(updated)
        // Refresh occupied slots
        loadGlobalSchedule()
    }

    fun toggleSkipSession(session: Session) {
        val updated = session.copy(isSkippedThisWeek = !session.isSkippedThisWeek)
        updateSession(updated)
        loadGlobalSchedule()
    }

    // --- Basic CRUD ---

    fun addSession(name: String) {
        val newSession = Session(name = name.toTitleCase())
        updateSession(newSession)
    }

    fun renameSession(session: Session, newName: String) {
        val updated = session.copy(name = newName.toTitleCase())
        updateSession(updated)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            try {
                repository.deleteSession(clientId, session.id)
                loadGlobalSchedule()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
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
