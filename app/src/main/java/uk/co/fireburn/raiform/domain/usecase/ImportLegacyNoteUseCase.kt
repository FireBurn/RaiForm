package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.LegacyParser
import javax.inject.Inject

class ImportLegacyNoteUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    /**
     * Preview: Parses text without saving to Database.
     */
    fun preview(rawText: String, combineSessions: Boolean): LegacyParser.ParseResult {
        return LegacyParser.parseLegacyNote(rawText, combineSessions)
    }

    /**
     * Commit: Parses and saves to Database.
     */
    suspend operator fun invoke(rawText: String, combineSessions: Boolean): Result<String> {
        return try {
            // 1. Parse pure text into domain objects
            val parseResult = LegacyParser.parseLegacyNote(rawText, combineSessions)

            // 2. Create the Client Entity
            val newClient = Client(name = parseResult.clientName)

            // 3. Save Client first to ensure ID exists
            repository.saveClient(newClient)

            // 4. Save all sessions associated with this client
            parseResult.sessions.forEach { session ->
                repository.saveSession(session.copy(clientId = newClient.id))
            }

            Result.success(newClient.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
