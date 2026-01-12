package uk.co.fireburn.raiform.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.fireburn.raiform.data.source.local.dao.ClientDao
import uk.co.fireburn.raiform.data.source.local.dao.HistoryDao
import uk.co.fireburn.raiform.data.source.local.dao.SessionDao
import uk.co.fireburn.raiform.data.source.local.entity.ClientEntity
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.HistoryEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity

@Database(
    entities = [
        ClientEntity::class,
        SessionEntity::class,
        HistoryEntity::class,
        // New Entities:
        ExerciseTemplateEntity::class,
        SessionExerciseEntity::class
    ],
    version = 2, // Incremented version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RaiFormDatabase : RoomDatabase() {

    abstract fun clientDao(): ClientDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DATABASE_NAME = "raiform_db"
    }
}
