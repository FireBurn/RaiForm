package uk.co.fireburn.raiform.domain.model

import java.util.UUID

/**
 * Domain representation of an Exercise within a Session.
 * Represents the "Target" for the current week.
 */
@kotlinx.serialization.Serializable
data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val weight: Double = 0.0,
    val isBodyweight: Boolean = false,
    val sets: Int = 0,
    val reps: Int = 0,
    val maintainWeight: Boolean = false,

    // Represents the state for the *current active week*
    // Historical completion is stored separately in HistoryLog
    val isDone: Boolean = false
) {
    // Calculated property for logic, not stored in DB directly usually
    val volume: Double
        get() = if (isBodyweight) 0.0 else weight * sets * reps
}
