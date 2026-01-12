package uk.co.fireburn.raiform.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        ExerciseTemplateEntity::class,
        SessionExerciseEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RaiFormDatabase : RoomDatabase() {

    abstract fun clientDao(): ClientDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao

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
                // 1. Add isDeleted
                safeAddColumn(db, "clients", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "isDeleted", "INTEGER NOT NULL DEFAULT 0")

                // 2. Add lastSyncTimestamp (In case version 2 didn't have it)
                safeAddColumn(db, "clients", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "history_logs", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        // If you need to support users coming from Version 1 directly to 3:
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new tables that were added in V2 (Templates/Links) if they don't exist
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_templates` (`id` TEXT NOT NULL, `clientId` TEXT NOT NULL, `name` TEXT NOT NULL, `weight` REAL NOT NULL, `isBodyweight` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `maintainWeight` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_templates_clientId` ON `exercise_templates` (`clientId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `session_exercises` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`templateId`) REFERENCES `exercise_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercises_templateId` ON `session_exercises` (`templateId`)")

                // Add the columns for V3
                safeAddColumn(db, "clients", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "clients", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "sessions", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "history_logs", "lastSyncTimestamp", "INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
