package uk.co.fireburn.raiform.domain.model

import java.util.UUID

/**
 * Represents a Personal Training Client.
 */
data class Client(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val status: ClientStatus = ClientStatus.ACTIVE,
    val notes: String = "", // Injuries, specific goals, etc.
    val dateAdded: Long = System.currentTimeMillis()
)

/**
 * ACTIVE: Shows on Dashboard
 * PAUSED: Hidden from immediate view but kept in database
 * REMOVED: Archived (soft delete)
 */
enum class ClientStatus {
    ACTIVE,
    PAUSED,
    REMOVED
}
