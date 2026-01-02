package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.LegacyParser
import javax.inject.Inject

class ImportLegacyNoteUseCase @Inject constructor(
    private val repository: RaiRepository
) {

    /**
     * Parses a raw text note (e.g. from Google Keep) and creates a full Client + Session structure.
     * Returns the ID of the newly created Client on success.
     */
    suspend operator fun invoke(rawText: String): Result<String> {
        return try {
            // 1. Parse pure text into domain objects
            val parseResult = LegacyParser.parseLegacyNote(rawText)

            // 2. Create the Client Entity
            val newClient = Client(name = parseResult.clientName)

            // 3. Save Client first to ensure ID exists
            repository.saveClient(newClient)

            // 4. Save all sessions associated with this client
            // We iterate and save individually or via batch if repository supported it
            // The parser generates ephemeral IDs, but they are UUIDs so we can use them directly
            parseResult.sessions.forEach { session ->
                repository.saveSession(newClient.id, session)
            }

            Result.success(newClient.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
