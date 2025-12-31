package uk.co.fireburn.raiform.domain.model

import java.util.UUID

data class Client(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val status: ClientStatus = ClientStatus.ACTIVE,
    val notes: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    // NEW: Day of the week to reset the schedule (1=Monday... 7=Sunday). Default Sunday.
    val weeklyResetDay: Int = 7
)

enum class ClientStatus {
    ACTIVE,
    PAUSED,
    REMOVED
}