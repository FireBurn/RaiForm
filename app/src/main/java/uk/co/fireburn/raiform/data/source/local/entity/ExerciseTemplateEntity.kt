package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_templates",
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
data class ExerciseTemplateEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val name: String,
    val weight: Double,
    val isBodyweight: Boolean,
    val sets: Int,
    val reps: Int,
    val maintainWeight: Boolean
)
