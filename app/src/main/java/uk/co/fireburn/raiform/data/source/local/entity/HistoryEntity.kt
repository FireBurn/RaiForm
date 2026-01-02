package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.HistoryLog

@Entity(
    tableName = "history_logs",
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
data class HistoryEntity(
    @PrimaryKey
    val id: String,
    val clientId: String,
    val originalSessionId: String,
    val sessionName: String,
    val dateLogged: Long,
    val exercises: List<Exercise>, // Handled by TypeConverter
    val lastSyncTimestamp: Long = 0L
) {
    fun toDomain() = HistoryLog(
        id = id,
        clientId = clientId,
        originalSessionId = originalSessionId,
        sessionName = sessionName,
        dateLogged = dateLogged,
        exercises = exercises
    )

    companion object {
        fun fromDomain(log: HistoryLog) = HistoryEntity(
            id = log.id,
            clientId = log.clientId,
            originalSessionId = log.originalSessionId,
            sessionName = log.sessionName,
            dateLogged = log.dateLogged,
            exercises = log.exercises,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }
}
