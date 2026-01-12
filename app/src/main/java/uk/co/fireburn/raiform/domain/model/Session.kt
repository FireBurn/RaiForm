package uk.co.fireburn.raiform.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val clientId: String = "", // Default empty
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),

    // Scheduling (Nullable in JSON = null in Kotlin, missing in JSON = null)
    val scheduledDay: Int? = null,
    val scheduledHour: Int? = null,
    val scheduledMinute: Int? = null,

    val lastResetTimestamp: Long = 0L,
    val isSkippedThisWeek: Boolean = false,
    val tempRescheduleTimestamp: Long? = null
)
