package uk.co.fireburn.raiform.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Client(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val status: ClientStatus = ClientStatus.ACTIVE,
    val notes: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val weeklyResetDay: Int = 7
)

@Serializable
enum class ClientStatus {
    ACTIVE,
    PAUSED,
    REMOVED
}
