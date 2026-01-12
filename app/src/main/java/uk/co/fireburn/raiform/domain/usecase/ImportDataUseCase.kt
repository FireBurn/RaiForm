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
            // 1. Parse the JSON file
            // The DataImporter now handles lenient parsing and default values
            // for old backup files missing new fields.
            val importedData = dataImporter.importFromJson(inputUri)

            // 2. Import Clients
            // Saving via Repository converts Domain -> Entity, setting
            // lastSyncTimestamp to System.currentTimeMillis() and isDeleted to false.
            importedData.clients.forEach { client ->
                repository.saveClient(client)
            }

            // 3. Import Sessions
            importedData.sessions.forEach { session ->
                // This handles the relational reconstruction (Templates/Links)
                repository.saveSession(session)
            }

            // 4. Import History Logs
            importedData.historyLogs.forEach { historyLog ->
                repository.logHistory(historyLog)
            }

            // 5. Trigger Immediate Sync
            // Since all imported items now have a fresh timestamp,
            // this will push them to Firestore as "New/Updated" data.
            repository.sync()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
