package uk.co.fireburn.raiform.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.fireburn.raiform.data.source.local.entity.ExerciseTemplateEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionEntity
import uk.co.fireburn.raiform.data.source.local.entity.SessionExerciseEntity
import uk.co.fireburn.raiform.data.source.local.relation.SessionPopulated

@Dao
interface SessionDao {

    // --- Reads (Return Populated Objects) ---

    @Transaction
    @Query("SELECT * FROM sessions WHERE clientId = :clientId ORDER BY scheduledDay ASC, scheduledHour ASC")
    fun getSessionsForClient(clientId: String): Flow<List<SessionPopulated>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: String): Flow<SessionPopulated?>

    /**
     * Gets all sessions for the Global Scheduler.
     */
    @Transaction
    @Query("SELECT * FROM sessions")
    fun getAllSessions(): Flow<List<SessionPopulated>>

    // --- Session Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM sessions WHERE clientId = :clientId")
    suspend fun deleteSessionsForClient(clientId: String)

    // --- Template Management (The Data Source) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplateEntity)

    @Update
    suspend fun updateTemplate(template: ExerciseTemplateEntity)

    @Query("SELECT * FROM exercise_templates WHERE clientId = :clientId AND name = :name LIMIT 1")
    suspend fun findTemplateByName(clientId: String, name: String): ExerciseTemplateEntity?

    @Query("SELECT * FROM exercise_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: String): ExerciseTemplateEntity?

    // --- Link Management (The Connection) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SessionExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<SessionExerciseEntity>)

    @Query("SELECT * FROM session_exercises WHERE id = :linkId")
    suspend fun getLinkById(linkId: String): SessionExerciseEntity?

    @Query("UPDATE session_exercises SET isDone = :isDone WHERE id = :linkId")
    suspend fun updateLinkStatus(linkId: String, isDone: Boolean)

    @Query("DELETE FROM session_exercises WHERE id = :linkId")
    suspend fun deleteLink(linkId: String)

    @Query("DELETE FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun clearLinksForSession(sessionId: String)
}
