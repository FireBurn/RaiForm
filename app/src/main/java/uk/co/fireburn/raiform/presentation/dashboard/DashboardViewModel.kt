package uk.co.fireburn.raiform.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.ClientRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class DashboardUiState(
    val clients: List<Client> = emptyList(),
    // Map of ClientID -> Formatted String (e.g., "Monday @ 4pm")
    val clientScheduleStatus: Map<String, String> = emptyMap(),
    // Data for the top card
    val nextGlobalSessionClient: String? = null,
    val nextGlobalSessionTime: String? = null,
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
                    // 1. Initial State: Sort by date added temporarily while we calculate schedules
                    val initialSort = clientList.sortedByDescending { c -> c.dateAdded }
                    _uiState.update {
                        it.copy(
                            clients = initialSort,
                            error = null
                        )
                    }

                    // 2. Asynchronously calculate schedules AND re-sort based on time
                    calculateSchedules(clientList)
                }
        }
    }

    private suspend fun calculateSchedules(clients: List<Client>) {
        val scheduleMap = mutableMapOf<String, String>()
        val nextSessionDates = mutableMapOf<String, LocalDateTime>() // Store raw dates for sorting

        var globalNextTime: LocalDateTime? = null
        var globalNextClientName: String? = null
        var globalFormattedTime: String? = null

        val now = LocalDateTime.now()

        clients.forEach { client ->
            try {
                // Fetch sessions for this client (One-shot from Flow)
                val sessions = repository.getSessionsForClient(client.id).first()

                // Find the nearest upcoming session
                val nextSessionData = getNextSessionDate(sessions, now)

                if (nextSessionData != null) {
                    val (_, date) = nextSessionData
                    val formatted = formatSessionTime(date, now)

                    scheduleMap[client.id] = formatted
                    nextSessionDates[client.id] = date // Capture date for sorting

                    // Check if this is the "Most immediate" global session
                    if (globalNextTime == null || date.isBefore(globalNextTime)) {
                        globalNextTime = date
                        globalNextClientName = client.name
                        globalFormattedTime = formatted
                    }
                } else {
                    scheduleMap[client.id] = "No sessions scheduled"
                }
            } catch (e: Exception) {
                scheduleMap[client.id] = "Error loading schedule"
            }
        }

        // --- SORTING LOGIC ---
        // 1. Has Next Session (Sooner = Higher)
        // 2. No Next Session (Alphabetical)
        val sortedClients = clients.sortedWith { a, b ->
            val dateA = nextSessionDates[a.id]
            val dateB = nextSessionDates[b.id]

            when {
                dateA != null && dateB != null -> dateA.compareTo(dateB) // Both have dates: Ascending time
                dateA != null -> -1 // A has date, B doesn't -> A first
                dateB != null -> 1  // B has date, A doesn't -> B first
                else -> a.name.compareTo(
                    b.name,
                    ignoreCase = true
                ) // Neither has date -> Alphabetical
            }
        }

        _uiState.update {
            it.copy(
                clients = sortedClients, // Apply the new sort order
                clientScheduleStatus = scheduleMap,
                nextGlobalSessionClient = globalNextClientName,
                nextGlobalSessionTime = globalFormattedTime,
                isLoading = false
            )
        }
    }

    // Helper: Find the soonest session in the future (or later today)
    private fun getNextSessionDate(
        sessions: List<Session>,
        now: LocalDateTime
    ): Pair<Session, LocalDateTime>? {
        val validSessions = sessions.filter {
            it.scheduledDay != null && it.scheduledHour != null && !it.isSkippedThisWeek
        }

        if (validSessions.isEmpty()) return null

        var bestSession: Session? = null
        var bestDate: LocalDateTime? = null

        validSessions.forEach { session ->
            val dayOfWeek = DayOfWeek.of(session.scheduledDay!!)
            val hour = session.scheduledHour!!
            val minute = session.scheduledMinute ?: 0

            // Calculate date relative to NOW
            var date = now.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)

            // If the calculated time is in the past (e.g. it's Monday 5pm, session was Monday 10am), move to next week
            if (date.isBefore(now)) {
                date = date.plusWeeks(1)
            }

            if (bestDate == null || date.isBefore(bestDate)) {
                bestDate = date
                bestSession = session
            }
        }

        return if (bestSession != null && bestDate != null) {
            bestSession!! to bestDate!!
        } else null
    }

    private fun formatSessionTime(date: LocalDateTime, now: LocalDateTime): String {
        val dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), date.toLocalDate())

        val timeStr = String.format(
            "%d:%02d%s",
            if (date.hour > 12) date.hour - 12 else if (date.hour == 0) 12 else date.hour,
            date.minute,
            if (date.hour >= 12) "pm" else "am"
        )

        return when {
            dayDiff == 0L -> "Today @ $timeStr"
            dayDiff == 1L -> "Tomorrow @ $timeStr"
            dayDiff < 7 -> "${
                date.dayOfWeek.getDisplayName(
                    TextStyle.FULL,
                    Locale.getDefault()
                )
            } @ $timeStr"

            else -> "${
                date.dayOfWeek.getDisplayName(
                    TextStyle.FULL,
                    Locale.getDefault()
                )
            } @ $timeStr"
        }
    }

    fun addClient(name: String) {
        viewModelScope.launch {
            try {
                val newClient = Client(name = name.toTitleCase())
                repository.saveClient(newClient)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error adding client: ${e.message}") }
            }
        }
    }

    fun updateClientName(client: Client, newName: String) {
        viewModelScope.launch {
            try {
                val updatedClient = client.copy(name = newName.toTitleCase())
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
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not archive: ${e.message}") }
            }
        }
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
