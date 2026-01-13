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
    indices = [Index("clientId"), Index("groupId")]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val clientId: String,
    val name: String,
    val groupId: String?,
    val scheduledDay: Int?,
    val scheduledHour: Int?,
    val scheduledMinute: Int?,
    val lastResetTimestamp: Long,
    val isSkippedThisWeek: Boolean,
    val tempRescheduleTimestamp: Long?,
    val lastSyncTimestamp: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toDomain(exercises: List<Exercise>) = Session(
        id = id,
        clientId = clientId,
        name = name,
        groupId = groupId,
        exercises = exercises,
        scheduledDay = scheduledDay,
        scheduledHour = scheduledHour,
        scheduledMinute = scheduledMinute,
        lastResetTimestamp = lastResetTimestamp,
        isSkippedThisWeek = isSkippedThisWeek,
        tempRescheduleTimestamp = tempRescheduleTimestamp
    )

    companion object {
        fun fromDomain(session: Session) =
            SessionEntity(
                id = session.id,
                clientId = session.clientId,
                name = session.name,
                groupId = session.groupId,
                scheduledDay = session.scheduledDay,
                scheduledHour = session.scheduledHour,
                scheduledMinute = session.scheduledMinute,
                lastResetTimestamp = session.lastResetTimestamp,
                isSkippedThisWeek = session.isSkippedThisWeek,
                tempRescheduleTimestamp = session.tempRescheduleTimestamp,
                lastSyncTimestamp = System.currentTimeMillis(),
                isDeleted = false
            )
    }
}
