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

data class ClientScheduleInfo(
    val timeString: String,
    val nextSessionName: String? = null
)

data class DashboardUiState(
    val clients: List<Client> = emptyList(),
    val clientScheduleStatus: Map<String, ClientScheduleInfo> = emptyMap(),
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

    fun refresh() {
        val currentClients = _uiState.value.clients
        if (currentClients.isNotEmpty()) {
            viewModelScope.launch {
                calculateSchedules(currentClients)
            }
        }
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
                    val initialSort = clientList.sortedByDescending { c -> c.dateAdded }
                    _uiState.update {
                        it.copy(
                            clients = initialSort,
                            error = null
                        )
                    }
                    calculateSchedules(clientList)
                }
        }
    }

    private suspend fun calculateSchedules(clients: List<Client>) {
        val scheduleMap = mutableMapOf<String, ClientScheduleInfo>()
        val nextSessionDates = mutableMapOf<String, LocalDateTime>()

        var globalNextTime: LocalDateTime? = null
        var globalNextClientName: String? = null
        var globalFormattedTime: String? = null

        val now = LocalDateTime.now()

        clients.forEach { client ->
            try {
                val sessions = repository.getSessionsForClient(client.id).first()
                val nextSessionData = getNextSessionDate(sessions, now)

                if (nextSessionData != null) {
                    val (session, date) = nextSessionData
                    val formatted = formatSessionTime(date, now)

                    scheduleMap[client.id] = ClientScheduleInfo(formatted, session.name)
                    nextSessionDates[client.id] = date

                    if (globalNextTime == null || date.isBefore(globalNextTime)) {
                        globalNextTime = date
                        globalNextClientName = client.name
                        globalFormattedTime = formatted
                    }
                } else {
                    scheduleMap[client.id] = ClientScheduleInfo("No sessions scheduled")
                }
            } catch (e: Exception) {
                scheduleMap[client.id] = ClientScheduleInfo("Error loading schedule")
            }
        }

        val sortedClients = clients.sortedWith { a, b ->
            val dateA = nextSessionDates[a.id]
            val dateB = nextSessionDates[b.id]

            when {
                dateA != null && dateB != null -> dateA.compareTo(dateB)
                dateA != null -> -1
                dateB != null -> 1
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }

        _uiState.update {
            it.copy(
                clients = sortedClients,
                clientScheduleStatus = scheduleMap,
                nextGlobalSessionClient = globalNextClientName,
                nextGlobalSessionTime = globalFormattedTime,
                isLoading = false
            )
        }
    }

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

            var date = now.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)

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

        val hour = date.hour
        val minute = date.minute
        val amPm = if (hour >= 12) "pm" else "am"
        val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

        // CHANGED: Hide minutes if 0
        val timeStr = if (minute == 0) "$h$amPm" else String.format("%d:%02d%s", h, minute, amPm)

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
                repository.saveClient(Client(name = name.toTitleCase()))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateClientName(client: Client, newName: String) {
        viewModelScope.launch {
            try {
                repository.saveClient(client.copy(name = newName.toTitleCase()))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun archiveClient(client: Client) {
        viewModelScope.launch {
            try {
                repository.archiveClient(client.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
