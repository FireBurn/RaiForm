package uk.co.fireburn.raiform.domain.model

import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),

    // Scheduling
    val scheduledDay: Int? = null, // 1=Mon ... 7=Sun
    val scheduledHour: Int? = null,
    val scheduledMinute: Int? = null,

    val lastResetTimestamp: Long = 0L,

    // Fix for Firestore Warning: Explicitly map property name
    @get:PropertyName("skippedThisWeek")
    val isSkippedThisWeek: Boolean = false,

    val tempRescheduleTimestamp: Long? = null
)