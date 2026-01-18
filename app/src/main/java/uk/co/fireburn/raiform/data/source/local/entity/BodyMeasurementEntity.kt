package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "body_measurements",
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
data class BodyMeasurementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val dateRecorded: Long,
    val weightKg: Double?,
    val shouldersCm: Double?,
    val armsCm: Double?,
    val waistCm: Double?,
    val chestCm: Double?,
    val legsCm: Double?
)
