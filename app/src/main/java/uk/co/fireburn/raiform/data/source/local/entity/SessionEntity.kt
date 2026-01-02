package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val clientId: String, // Foreign Key linking to Client
    val name: String,
    val exercises: List<Exercise>, // Handled by TypeConverter
    val scheduledDay: Int?,
    val scheduledHour: Int?,
    val scheduledMinute: Int?,
    val lastResetTimestamp: Long,
    val isSkippedThisWeek: Boolean,
    val tempRescheduleTimestamp: Long?,
    val lastSyncTimestamp: Long = 0L
) {
    fun toDomain() = Session(
        id = id,
        name = name,
        exercises = exercises,
        scheduledDay = scheduledDay,
        scheduledHour = scheduledHour,
        scheduledMinute = scheduledMinute,
        lastResetTimestamp = lastResetTimestamp,
        isSkippedThisWeek = isSkippedThisWeek,
        tempRescheduleTimestamp = tempRescheduleTimestamp
    )

    companion object {
        fun fromDomain(session: Session, clientId: String) = SessionEntity(
            id = session.id,
            clientId = clientId,
            name = session.name,
            exercises = session.exercises,
            scheduledDay = session.scheduledDay,
            scheduledHour = session.scheduledHour,
            scheduledMinute = session.scheduledMinute,
            lastResetTimestamp = session.lastResetTimestamp,
            isSkippedThisWeek = session.isSkippedThisWeek,
            tempRescheduleTimestamp = session.tempRescheduleTimestamp,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }
}
