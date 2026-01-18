package uk.co.fireburn.raiform.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_definitions")
data class ExerciseDefinitionEntity(
    @PrimaryKey val name: String, // The exercise name (stored in Title Case)
    val bodyPart: String
)
