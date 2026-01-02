package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.HistoryLog
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Encapsulates the logic for rolling over the weekly schedule.
 * Checks if the current date has passed the client's specific reset day.
 * If so, logs the previous week's history and resets 'Done' flags.
 */
class WeeklyResetUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    /**
     * Checks the list of sessions against the client's reset schedule.
     * Performs side effects (DB updates) if a reset is needed.
     * Returns the updated list of sessions.
     */
    suspend operator fun invoke(client: Client, sessions: List<Session>): List<Session> {
        val now = LocalDateTime.now()

        // 1. Calculate the cutoff point (The most recent occurrence of the reset day at 00:00)
        // e.g., if ResetDay is Sunday (7), and today is Monday, cutoff is yesterday at 00:00.
        val targetDayOfWeek =
            DayOfWeek.of(if (client.weeklyResetDay == 0) 7 else client.weeklyResetDay)

        val lastResetDate = now.with(TemporalAdjusters.previousOrSame(targetDayOfWeek))
            .withHour(0).withMinute(0).withSecond(0).withNano(0)

        val lastResetMillis =
            lastResetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 2. Identify sessions that haven't been reset since the cutoff
        val needsReset = sessions.any { it.lastResetTimestamp < lastResetMillis }

        if (!needsReset) {
            return sessions
        }

        // 3. Perform Reset Logic
        val currentTimestamp = System.currentTimeMillis()
        val updatedSessions = mutableListOf<Session>()

        sessions.forEach { session ->
            if (session.lastResetTimestamp < lastResetMillis) {
                // A. Log History (Snapshot of what was done)
                // We only log if at least one exercise was done to avoid empty spam
                if (session.exercises.any { it.isDone }) {
                    val log = HistoryLog(
                        clientId = client.id,
                        originalSessionId = session.id,
                        sessionName = session.name,
                        dateLogged = currentTimestamp, // Logged now
                        exercises = session.exercises // Snapshot current state
                    )
                    repository.logHistory(log)
                }

                // B. Reset Session State
                val resetExercises = session.exercises.map { exercise ->
                    exercise.copy(isDone = false)
                }

                val resetSession = session.copy(
                    exercises = resetExercises,
                    isSkippedThisWeek = false, // Un-skip for the new week
                    tempRescheduleTimestamp = null,
                    lastResetTimestamp = currentTimestamp
                )
                updatedSessions.add(resetSession)
            } else {
                updatedSessions.add(session)
            }
        }

        // 4. Persist changes
        // We use updateSessionOrder as a batch update for the client's sessions
        // Fixed: removed client.id argument as sessions now contain their own clientId
        repository.updateSessionOrder(updatedSessions)

        return updatedSessions
    }
}
