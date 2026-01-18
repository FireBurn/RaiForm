package uk.co.fireburn.raiform.domain.usecase

import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class ManageSessionUseCase @Inject constructor(
    private val repository: RaiRepository
) {
    suspend fun createSession(clientId: String, name: String, addToFullRoutine: Boolean = false) {
        if (name.isBlank() && !addToFullRoutine) return

        var finalName = name.trim().toTitleCase()
        var groupId: String? = null

        if (addToFullRoutine) {
            // Check if a Full Routine group already exists for this client
            try {
                val sessions = repository.getSessionsForClient(clientId).first()
                val existingGroup = sessions.firstOrNull {
                    it.groupId != null && it.name.startsWith("Full Routine", ignoreCase = true)
                }

                if (existingGroup != null) {
                    groupId = existingGroup.groupId
                    // The UI will handle dynamic naming (e.g., Full Routine 1, Full Routine Monday)
                    // We store a generic base name
                    finalName = "Full Routine Session"
                } else {
                    groupId = UUID.randomUUID().toString()
                    finalName = "Full Routine Session"
                }
            } catch (e: Exception) {
                // Fallback if flow collection fails
                groupId = UUID.randomUUID().toString()
                finalName = "Full Routine Session"
            }
        } else if (finalName.isBlank()) {
            return
        }

        val newSession = Session(
            clientId = clientId,
            name = finalName,
            groupId = groupId
        )
        repository.saveSession(newSession)
    }

    suspend fun renameSession(clientId: String, session: Session, newName: String) {
        if (newName.isBlank()) return
        // If a linked session is renamed manually, break the link (set groupId to null)
        val updatedSession = session.copy(
            name = newName.toTitleCase(),
            groupId = null
        )
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
            isSkippedThisWeek = false
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
        reps: Int,
        bodyPart: String
    ) {
        val titleCaseName = name.trim().toTitleCase()

        // 1. Save/Update Global Definition (Body Part)
        repository.saveExerciseDefinition(titleCaseName, bodyPart)

        // 2. History Restoration Logic
        // If the user didn't specify values (0.0/0), check if we have history to restore
        val historyStats = repository.findExerciseStats(clientId, titleCaseName)
        val finalWeight = if (weight == 0.0 && historyStats != null) historyStats.first else weight
        val finalSets = if (sets == 0 && historyStats != null) historyStats.second else sets
        val finalReps = if (reps == 0 && historyStats != null) historyStats.third else reps

        // 3. Add to Current and Linked Sessions
        val sessionsToUpdate = mutableListOf<Session>()
        sessionsToUpdate.add(session)

        // If part of a group (Full Routine), add to all sessions in that group
        if (session.groupId != null) {
            val linkedSessions = repository.getSessionsByGroup(clientId, session.groupId)
            sessionsToUpdate.addAll(linkedSessions.filter { it.id != session.id })
        }

        sessionsToUpdate.forEach { targetSession ->
            // Generate a NEW UUID for each exercise instance
            val newExercise = Exercise(
                id = UUID.randomUUID().toString(),
                name = titleCaseName,
                weight = finalWeight,
                isBodyweight = isBodyweight,
                sets = finalSets,
                reps = finalReps
            )

            val updatedExercises = targetSession.exercises + newExercise
            val updatedSession = targetSession.copy(exercises = updatedExercises)
            repository.saveSession(updatedSession)
        }
    }

    suspend fun updateExercise(
        clientId: String,
        session: Session,
        exerciseId: String,
        name: String,
        weight: Double,
        isBodyweight: Boolean,
        sets: Int,
        reps: Int,
        bodyPart: String
    ) {
        val titleCaseName = name.trim().toTitleCase()
        val oldName = session.exercises.find { it.id == exerciseId }?.name

        // 1. Update Global Definition
        repository.saveExerciseDefinition(titleCaseName, bodyPart)

        // 2. Global Rename Logic
        // If the name has changed, update it everywhere in the app
        if (oldName != null && oldName != titleCaseName) {
            repository.renameExerciseGlobally(oldName, titleCaseName)
        }

        // 3. Update the specific exercise instance
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) {
                it.copy(
                    name = titleCaseName,
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
        val targetExercise = session.exercises.find { it.id == exerciseId } ?: return
        val newDoneState = !targetExercise.isDone

        // 1. Update current session
        val updatedExercises = session.exercises.map {
            if (it.id == exerciseId) it.copy(isDone = newDoneState) else it
        }
        repository.saveSession(session.copy(exercises = updatedExercises))

        // 2. Linked Completion Logic
        // If linked (Full Routine), find the same exercise (by Name) in other sessions and toggle it
        if (session.groupId != null) {
            val linkedSessions = repository.getSessionsByGroup(clientId, session.groupId)
            linkedSessions.filter { it.id != session.id }.forEach { linkedSession ->
                val linkedExercises = linkedSession.exercises.map { ex ->
                    if (ex.name == targetExercise.name) {
                        ex.copy(isDone = newDoneState)
                    } else ex
                }
                // Only save if changed
                if (linkedExercises != linkedSession.exercises) {
                    repository.saveSession(linkedSession.copy(exercises = linkedExercises))
                }
            }
        }
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

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
