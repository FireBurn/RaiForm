package uk.co.fireburn.raiform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.WeeklyResetUseCase

@HiltWorker
class WeeklyResetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RaiRepository,
    private val weeklyResetUseCase: WeeklyResetUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Get all active clients
            val activeClients = repository.getActiveClients().first()

            // 2. Iterate and check for resets
            activeClients.forEach { client ->
                // Fetch sessions snapshot
                val sessions = repository.getSessionsForClient(client.id).first()

                // UseCase handles the logic:
                // Checks dates, logs history if needed, resets flags, saves to DB
                weeklyResetUseCase(client, sessions)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // If it fails, retry later (e.g. if DB is locked or logic crashes)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "weekly_reset_worker"
        const val TAG = "reset_worker"
    }
}
