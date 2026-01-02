package uk.co.fireburn.raiform.domain.usecase

import uk.co.fireburn.raiform.domain.repository.RaiRepository
import javax.inject.Inject

/**
 * Use case to trigger a data synchronization between Local (Room) and Remote (Firestore).
 * This can be called from the UI (Pull-to-refresh) or a background Worker.
 */
class SyncDataUseCase @Inject constructor(
    private val repository: RaiRepository
) {
    suspend operator fun invoke() {
        repository.sync()
    }
}
