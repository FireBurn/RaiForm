package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.ClientStatus

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val status: ClientStatus,
    val notes: String,
    val dateAdded: Long,
    val weeklyResetDay: Int,
    val lastSyncTimestamp: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toDomain() = Client(
        id = id,
        name = name,
        status = status,
        notes = notes,
        dateAdded = dateAdded,
        weeklyResetDay = weeklyResetDay
    )

    companion object {
        fun fromDomain(client: Client) = ClientEntity(
            id = client.id,
            name = client.name,
            status = client.status,
            notes = client.notes,
            dateAdded = client.dateAdded,
            weeklyResetDay = client.weeklyResetDay,
            lastSyncTimestamp = System.currentTimeMillis(),
            isDeleted = false // Default active when converting from domain
        )
    }
}
