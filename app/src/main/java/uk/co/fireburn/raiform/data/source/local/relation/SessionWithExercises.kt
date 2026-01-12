package uk.co.fireburn.raiform.data.source.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity

data class SessionWithExercises(
    @Embedded val session: SessionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
        entity = SessionExerciseEntity::class
    )
    val sessionExercises: List<SessionExerciseEntity>,

    // We fetch templates separately or map them in the DAO manually,
    // but a common pattern for 3-table joins in Room is fetching the links
    // and then fetching the templates.
    // HOWEVER, to keep it simple, we will query the templates in the DAO transaction.
)
