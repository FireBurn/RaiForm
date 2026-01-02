package uk.co.fireburn.raiform.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class DashboardDisplayData(
    val clients: List<Client>,
    val scheduleStatus: Map<String, String>,
    val nextSessionClientName: String?,
    val nextSessionTimeFormatted: String?
)

class GetDashboardDataUseCase @Inject constructor(
    private val repository: RaiRepository
) {
    operator fun invoke(): Flow<DashboardDisplayData> {
        return repository.getActiveClients().flatMapLatest { clients ->
            if (clients.isEmpty()) {
                flowOf(
                    DashboardDisplayData(
                        clients = emptyList(),
                        scheduleStatus = emptyMap(),
                        nextSessionClientName = null,
                        nextSessionTimeFormatted = null
                    )
                )
            } else {
                // Create a flow for each client's sessions
                val sessionFlows = clients.map { client ->
                    repository.getSessionsForClient(client.id).map { sessions ->
                        client to sessions
                    }
                }

                // Combine all session flows into one update
                combine(sessionFlows) { clientSessionsArray ->
                    val clientSessionMap = clientSessionsArray.associate { it }
                    calculateDashboardData(clients, clientSessionMap)
                }
            }
        }
    }

    private fun calculateDashboardData(
        clients: List<Client>,
        clientSessions: Map<Client, List<Session>>
    ): DashboardDisplayData {
        val scheduleMap = mutableMapOf<String, String>()
        var globalNextTime: LocalDateTime? = null
        var globalNextClientName: String? = null
        var globalFormattedTime: String? = null

        val now = LocalDateTime.now()

        // 1. Calculate schedule for each client
        clients.forEach { client ->
            val sessions = clientSessions[client] ?: emptyList()
            val nextSessionData = getNextSessionDate(sessions, now)

            if (nextSessionData != null) {
                val (session, date) = nextSessionData
                val formatted = formatSessionTime(date, now)

                // Store status: "Wednesday @ 2:00pm" or "Legs - Today @ 9:00am"
                // Using just time here, UI can append session name if needed,
                // but let's stick to the previous ViewModel logic: Time string + Session Name logic
                val displayString = if (sessions.isNotEmpty()) formatted else "Active"
                scheduleMap[client.id] = displayString

                // 2. Check Global Best
                if (globalNextTime == null || date.isBefore(globalNextTime)) {
                    globalNextTime = date
                    globalNextClientName = client.name
                    globalFormattedTime = formatted
                }
            } else {
                scheduleMap[client.id] = if (sessions.isEmpty()) "No sessions" else "Unscheduled"
            }
        }

        // 3. Sort clients: Upcoming sessions first, then by name
        val sortedClients = clients.sortedWith { a, b ->
            val sessionsA = clientSessions[a] ?: emptyList()
            val sessionsB = clientSessions[b] ?: emptyList()
            val dateA = getNextSessionDate(sessionsA, now)?.second
            val dateB = getNextSessionDate(sessionsB, now)?.second

            when {
                dateA != null && dateB != null -> dateA.compareTo(dateB)
                dateA != null -> -1
                dateB != null -> 1
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }

        return DashboardDisplayData(
            clients = sortedClients,
            scheduleStatus = scheduleMap,
            nextSessionClientName = globalNextClientName,
            nextSessionTimeFormatted = globalFormattedTime
        )
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

            // If the calculated time is in the past (e.g. earlier today), move to next week
            // Unless it's today and we want to show it?
            // Logic: if it's "Today 9am" and now is "Today 10am", strictly speaking it's gone.
            // But for a dashboard, maybe we show it until end of day?
            // Let's keep strict "future" logic or "current week" logic.
            // Previous logic: if date.isBefore(now) -> plusWeeks(1)
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
}
