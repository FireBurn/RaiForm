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
            val importedData = dataImporter.importFromJson(inputUri)

            // 2. Import Clients
            importedData.clients.forEach { client ->
                repository.saveClient(client)
            }

            // 3. Import Sessions (handles Relational Data)
            importedData.sessions.forEach { session ->
                repository.saveSession(session)
            }

            // 4. Import History Logs
            importedData.historyLogs.forEach { historyLog ->
                repository.logHistory(historyLog)
            }

            // 5. Import Global Exercise Definitions (Body Parts)
            importedData.exerciseDefinitions.forEach { (name, bodyPart) ->
                repository.saveExerciseDefinition(name, bodyPart)
            }

            // 6. Import Body Measurements
            importedData.bodyMeasurements.forEach { measurement ->
                repository.saveBodyMeasurement(measurement)
            }

            // 7. Trigger Immediate Sync
            repository.sync()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
