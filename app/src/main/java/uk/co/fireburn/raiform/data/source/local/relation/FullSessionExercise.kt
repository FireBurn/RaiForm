package uk.co.fireburn.raiform.data.source.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity
import uk.co.fireburn.raiform.domain.model.Exercise

data class FullSessionExercise(
    @Embedded val link: SessionExerciseEntity,

    @Relation(
        parentColumn = "templateId",
        entityColumn = "id"
    )
    val template: ExerciseTemplateEntity
) {
    fun toDomain(): Exercise {
        return Exercise(
            id = link.id, // Use the unique link ID for UI operations
            name = template.name,
            weight = template.weight,
            isBodyweight = template.isBodyweight,
            sets = template.sets,
            reps = template.reps,
            maintainWeight = template.maintainWeight,
            isDone = link.isDone
        )
    }
}
