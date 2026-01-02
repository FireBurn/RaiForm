package uk.co.fireburn.raiform.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a snapshot of a session state at a specific point in time.
 * This is created typically when a weekly reset occurs, archiving the previous week's performance.
 */
@Serializable
data class HistoryLog(
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val originalSessionId: String,
    val sessionName: String,
    val dateLogged: Long,
    // We reuse the Exercise model here as it acts as a snapshot of the state at that time
    val exercises: List<Exercise>
)
