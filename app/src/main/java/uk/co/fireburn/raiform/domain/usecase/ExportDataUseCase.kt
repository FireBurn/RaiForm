package uk.co.fireburn.raiform.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.model.ExportData
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.util.DataExporter
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val repository: RaiRepository,
    private val dataExporter: DataExporter
) {
    suspend operator fun invoke(outputUri: Uri): Result<Unit> {
        return try {
            val clients = repository.getAllClients().first()
            val sessions = repository.getAllSessions().first()
            val allHistoryLogs = repository.getAllHistoryLogs().first() // Use the new method

            val exportData = ExportData(
                clients = clients,
                sessions = sessions,
                historyLogs = allHistoryLogs
            )
            dataExporter.exportToJson(exportData, outputUri)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
