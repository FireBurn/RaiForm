package uk.co.fireburn.raiform.ui.screens.client_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.domain.repository.RaiRepository
import uk.co.fireburn.raiform.domain.usecase.ManageSessionUseCase
import uk.co.fireburn.raiform.domain.usecase.WeeklyResetUseCase
import javax.inject.Inject

data class ClientDetailsUiState(
    val client: Client? = null,
    val sessions: List<Session> = emptyList(),
    val globalOccupiedSlots: Map<Int, List<Int>> = emptyMap(),
    val allGlobalSessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class ClientDetailsEvent {
    data class ShowConflictDialog(
        val conflictName: String,
        val conflictingSession: Session,
        val targetDay: Int,
        val targetHour: Int,
        val sessionToSchedule: Session
    ) : ClientDetailsEvent()

    data class ShowMessage(val message: String) : ClientDetailsEvent()
    data class NavigateToClient(val clientId: String, val sessionId: String?) : ClientDetailsEvent()
    data class OpenScheduleDialog(val session: Session) : ClientDetailsEvent()
}

@HiltViewModel
class ClientDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle, // Accessed directly for logic
    private val repository: RaiRepository,
    private val manageSessionUseCase: ManageSessionUseCase,
    private val weeklyResetUseCase: WeeklyResetUseCase
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(ClientDetailsUiState())
    val uiState: StateFlow<ClientDetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ClientDetailsEvent>()
    val events: SharedFlow<ClientDetailsEvent> = _events.asSharedFlow()

    init {
        loadData()
        loadGlobalSchedule()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getClient(clientId),
                repository.getSessionsForClient(clientId)
            ) { client, sessions ->
                client to sessions
            }.collect { (client, sessions) ->
                if (client == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                    return@collect
                }
                val sortedSessions = sessions.sortedWith(
                    compareBy({ it.scheduledDay ?: 8 }, { it.scheduledHour ?: 25 })
                )
                val processedSessions = weeklyResetUseCase(client, sortedSessions)

                _uiState.update {
                    it.copy(client = client, sessions = processedSessions, isLoading = false)
                }

                // Check for Auto-Reschedule Link
                // We check if "rescheduleSessionId" was passed in navigation
                val targetSessionId = savedStateHandle.get<String>("rescheduleSessionId")
                if (targetSessionId != null) {
                    val targetSession = processedSessions.find { it.id == targetSessionId }
                    if (targetSession != null) {
                        // Trigger UI to open dialog immediately
                        _events.emit(ClientDetailsEvent.OpenScheduleDialog(targetSession))
                        // Clear state so it doesn't happen again on rotation
                        savedStateHandle["rescheduleSessionId"] = null
                    }
                }
            }
        }
    }

    private fun loadGlobalSchedule() {
        viewModelScope.launch {
            repository.getAllSessions().collect { allSessions ->
                val map = mutableMapOf<Int, MutableList<Int>>()
                allSessions.forEach { session ->
                    if (session.scheduledDay != null && session.scheduledHour != null && !session.isSkippedThisWeek) {
                        val list = map.getOrPut(session.scheduledDay) { mutableListOf() }
                        list.add(session.scheduledHour)
                    }
                }
                _uiState.update {
                    it.copy(
                        globalOccupiedSlots = map,
                        allGlobalSessions = allSessions
                    )
                }
            }
        }
    }

    // --- Actions ---

    fun tryUpdateSchedule(session: Session, day: Int, hour: Int) {
        viewModelScope.launch {
            // Check for conflict
            val conflict = _uiState.value.allGlobalSessions.find {
                it.scheduledDay == day &&
                        it.scheduledHour == hour &&
                        it.id != session.id &&
                        !it.isSkippedThisWeek
            }

            if (conflict != null) {
                // Find owner name
                val ownerClient = repository.getClient(conflict.clientId).first()
                val ownerName = ownerClient?.name ?: "Unknown"

                // Trigger Dialog in UI
                _events.emit(
                    ClientDetailsEvent.ShowConflictDialog(
                        conflictName = ownerName,
                        conflictingSession = conflict,
                        targetDay = day,
                        targetHour = hour,
                        sessionToSchedule = session
                    )
                )
            } else {
                // No conflict, just save
                manageSessionUseCase.updateSchedule(clientId, session, day, hour, 0)
                _events.emit(ClientDetailsEvent.ShowMessage("Scheduled!"))
            }
        }
    }

    fun forceUpdateSchedule(
        sessionToSchedule: Session,
        conflictingSession: Session,
        day: Int,
        hour: Int
    ) {
        viewModelScope.launch {
            // 1. Bump the conflicting session (Unschedule it)
            val bumped = conflictingSession.copy(
                scheduledDay = null, scheduledHour = null, scheduledMinute = null
            )
            repository.saveSession(bumped)

            // 2. Schedule the new one
            manageSessionUseCase.updateSchedule(clientId, sessionToSchedule, day, hour, 0)

            // 3. Chain: Navigate to the bumped client so user can fix their schedule
            if (conflictingSession.clientId != clientId) {
                _events.emit(ClientDetailsEvent.ShowMessage("Overwritten. Rescheduling ${conflictingSession.name}..."))
                _events.emit(
                    ClientDetailsEvent.NavigateToClient(
                        conflictingSession.clientId,
                        conflictingSession.id
                    )
                )
            } else {
                _events.emit(ClientDetailsEvent.ShowMessage("Swapped successfully."))
                // If same client, we could optionally open the dialog for the bumped session here too
                // but usually the UI list updates and it moves to "Unscheduled"
                _events.emit(ClientDetailsEvent.OpenScheduleDialog(bumped))
            }
        }
    }

    // Standard CRUD methods

    fun addSession(name: String, addToFullRoutine: Boolean) {
        viewModelScope.launch {
            manageSessionUseCase.createSession(
                clientId,
                name,
                addToFullRoutine
            )
        }
    }

    fun renameSession(session: Session, newName: String) {
        viewModelScope.launch { manageSessionUseCase.renameSession(clientId, session, newName) }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch { manageSessionUseCase.deleteSession(session.id) }
    }

    fun toggleSkipSession(session: Session) {
        viewModelScope.launch { manageSessionUseCase.toggleSkipSession(clientId, session) }
    }

    fun onReorderSessions(newOrder: List<Session>) {
        _uiState.update { it.copy(sessions = newOrder) }
        viewModelScope.launch {
            repository.updateSessionOrder(newOrder)
        }
    }
}
