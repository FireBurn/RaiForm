package uk.co.fireburn.raiform.data.source.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity

data class SessionPopulated(
    @Embedded val session: SessionEntity,

    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val exercises: List<FullSessionExercise>
) {
    fun toDomain() = session.toDomain(
        exercises = exercises.sortedBy { it.link.orderIndex }.map { it.toDomain() }
    )
}
