package uk.co.fireburn.raiform.domain.model

import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),

    // --- NEW SCHEDULING FIELDS ---
    // 1=Mon, 2=Tue, ..., 7=Sun
    val scheduledDay: Int? = null,
    // Hour of day (0-23)
    val scheduledHour: Int? = null,
    // Minute of hour (0-59)
    val scheduledMinute: Int? = null,

    // Timestamp of when this session was last reset/cleared
    val lastResetTimestamp: Long = 0L,

    // Logic for skipping/rescheduling
    // If true, this session is hidden/disabled for the current week
    val isSkippedThisWeek: Boolean = false,
    // If set, this overrides the standard day for the current week only
    val tempRescheduleTimestamp: Long? = null
)