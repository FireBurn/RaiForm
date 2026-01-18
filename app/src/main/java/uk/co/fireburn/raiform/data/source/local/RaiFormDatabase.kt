package uk.co.fireburn.raiform.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.co.fireburn.raiform.data.source.local.dao.ClientDao
import uk.co.fireburn.raiform.data.source.local.dao.ExerciseDefinitionDao
import uk.co.fireburn.raiform.data.source.local.dao.HistoryDao
import uk.co.fireburn.raiform.data.source.local.dao.MeasurementDao
import uk.co.fireburn.raiform.data.source.local.dao.SessionDao
import uk.co.fireburn.raiform.data.source.local.entity.BodyMeasurementEntity
import uk.co.fireburn.raiform.data.source.local.entity.ClientEntity
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseDefinitionEntity
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.HistoryEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity

@Database(
    entities = [
        ClientEntity::class,
        SessionEntity::class,
        HistoryEntity::class,
        ExerciseTemplateEntity::class,
        SessionExerciseEntity::class,
        ExerciseDefinitionEntity::class,
        BodyMeasurementEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RaiFormDatabase : RoomDatabase() {

    abstract fun clientDao(): ClientDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao
    abstract fun exerciseDefinitionDao(): ExerciseDefinitionDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val DATABASE_NAME = "raiform_db"

        // Helper to safely add columns
        private fun safeAddColumn(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            type: String
        ) {
            try {
                // Simplest check: Try to select the column. If it fails, add it.
                val cursor = db.query("SELECT $column FROM $table LIMIT 0")
                cursor.close()
            } catch (e: Exception) {
                // Column doesn't exist, add it
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAddColumn(db, "clients", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "clients", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "history_logs", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_templates` (`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, `name` TEXT NOT NULL, `weight` REAL NOT NULL, `isBodyweight` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `maintainWeight` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_templates_clientId` ON `exercise_templates` (`clientId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercises` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`templateId`) REFERENCES `exercise_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_templateId` ON `session_exercises` (`templateId`)")

                safeAddColumn(db, "clients", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "clients", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "history_logs", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN groupId TEXT DEFAULT NULL")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_groupId ON sessions(groupId)")
                } catch (e: Exception) {
                    // Ignore if already exists/fails
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create Exercise Definitions Table
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_definitions` (`name` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, PRIMARY KEY(`name`))")

                // 2. Create Body Measurements Table
                db.execSQL("CREATE TABLE IF NOT EXISTS `body_measurements` (`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, `dateRecorded` INTEGER NOT NULL, `weightKg` REAL, `shouldersCm` REAL, `armsCm` REAL, `waistCm` REAL, `chestCm` REAL, `legsCm` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")

                // 3. Create Index for Measurements
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurements_clientId` ON `body_measurements` (`clientId`)")
            }
        }
    }
}
