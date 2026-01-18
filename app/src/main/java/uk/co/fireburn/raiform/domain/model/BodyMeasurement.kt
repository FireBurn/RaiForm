package uk.co.fireburn.raiform.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BodyMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val dateRecorded: Long,
    val weightKg: Double? = null,
    val shouldersCm: Double? = null,
    val armsCm: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val legsCm: Double? = null
)
