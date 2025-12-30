package uk.co.fireburn.raiform.domain.model

import java.util.UUID

/**
 * Represents a full workout session.
 * e.g., "PUSH DAY" containing Bench Press, Flyes, Triceps Pushdown.
 */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "", // Changed from no default to ""
    val exercises: List<Exercise> = emptyList(),
    val scheduledDayOfWeek: Int? = null
)