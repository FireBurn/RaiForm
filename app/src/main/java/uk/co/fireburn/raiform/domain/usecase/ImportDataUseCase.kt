package uk.co.fireburn.raiform.domain.usecase

import android.net.Uri
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.DataImporter
import javax.inject.Inject

class ImportDataUseCase @Inject constructor(
    private val repository: RaiRepository,
    private val dataImporter: DataImporter
) {
    suspend operator fun invoke(inputUri: Uri): Result<Unit> {
        return try {
            val importedData = dataImporter.importFromJson(inputUri)

            // 1. Import Clients
            importedData.clients.forEach { client ->
                repository.saveClient(client)
            }

            // 2. Import Sessions
            importedData.sessions.forEach { session ->
                // Session now contains clientId, so we can directly save it
                repository.saveSession(session)
            }

            // 3. Import History Logs
            importedData.historyLogs.forEach { historyLog ->
                repository.logHistory(historyLog)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
