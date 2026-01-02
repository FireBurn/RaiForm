package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.util.Locale
import javax.inject.Inject

class ManageSessionUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    // --- Session Management ---

    suspend fun createSession(clientId: String, name: String) {
        if (name.isBlank()) return
        val newSession =
            Session(clientId = clientId, name = name.toTitleCase())
        repository.saveSession(newSession)
    }

    suspend fun renameSession(clientId: String, session: Session, newName: String) {
        if (newName.isBlank()) return
        val updatedSession = session.copy(name = newName.toTitleCase())
        repository.saveSession(updatedSession)
    }

    suspend fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
    }

    suspend fun updateSessionOrder(clientId: String, sessions: List<Session>) {
        repository.updateSessionOrder(sessions)
    }

    suspend fun toggleSkipSession(clientId: String, session: Session) {
        val updated = session.copy(isSkippedThisWeek = !session.isSkippedThisWeek)
        repository.saveSession(updated)
    }

    suspend fun updateSchedule(
        clientId: String,
        session: Session,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        val updated = session.copy(
            scheduledDay = day,
            scheduledHour = hour,
            scheduledMinute = minute,
            isSkippedThisWeek = false // Re-enable if it was skipped
        )
        repository.saveSession(updated)
    }

    // --- Exercise Management ---

    suspend fun addExercise(
        clientId: String,
        session: Session,
        name: String,
        weight: Double,
        isBodyweight: Boolean,
        sets: Int,
        reps: Int
    ) {
        val newExercise = Exercise(
            name = name.toTitleCase(),
            weight = weight,
            isBodyweight = isBodyweight,
            sets = sets,
            reps = reps
        )
        val updatedExercises = session.exercises + newExercise
        val updatedSession = session.copy(exercises = updatedExercises)
        repository.saveSession(updatedSession)
    }

    suspend fun updateExercise(
        clientId: String,
        session: Session,
        exerciseId: String,
        name: String,
        weight: Double,
        isBodyweight: Boolean,
        sets: Int,
        reps: Int
    ) {
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) {
                it.copy(
                    name = name.toTitleCase(),
                    weight = weight,
                    isBodyweight = isBodyweight,
                    sets = sets,
                    reps = reps
                )
            } else it
        }
        val updatedSession = session.copy(exercises = updatedExercises)
        repository.saveSession(updatedSession)
    }

    suspend fun toggleExerciseDone(clientId: String, session: Session, exerciseId: String) {
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) it.copy(isDone = !it.isDone) else it
        }
        val updatedSession = session.copy(exercises = updatedExercises)
        repository.saveSession(updatedSession)
    }

    suspend fun toggleMaintainWeight(clientId: String, session: Session, exerciseId: String) {
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) it.copy(maintainWeight = !it.maintainWeight) else it
        }
        val updatedSession = session.copy(exercises = updatedExercises)
        repository.saveSession(updatedSession)
    }

    suspend fun deleteExercise(clientId: String, session: Session, exerciseId: String) {
        val updatedExercises = session.exercises.filter { it.id != exerciseId }
        val updatedSession = session.copy(exercises = updatedExercises)
        repository.saveSession(updatedSession)
    }

    suspend fun reorderExercises(clientId: String, session: Session, newOrder: List<Exercise>) {
        val updatedSession = session.copy(exercises = newOrder)
        repository.saveSession(updatedSession)
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
