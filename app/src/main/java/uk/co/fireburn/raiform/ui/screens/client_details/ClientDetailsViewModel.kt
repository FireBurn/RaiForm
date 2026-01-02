package uk.co.fireburn.raiform.ui.screens.client_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ClientDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RaiRepository,
    private val manageSessionUseCase: ManageSessionUseCase,
    private val weeklyResetUseCase: WeeklyResetUseCase
) : ViewModel() {

    // Hilt/Navigation automatically populates the SavedStateHandle with route arguments
    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(ClientDetailsUiState())
    val uiState: StateFlow<ClientDetailsUiState> = _uiState.asStateFlow()

    init {
        loadData()
        loadGlobalSchedule()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Combine Client and Sessions into one state update
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

                // 1. Sort Sessions
                val sortedSessions = sessions.sortedWith(
                    compareBy(
                        { it.scheduledDay ?: 8 },
                        { it.scheduledHour ?: 25 },
                        { it.scheduledMinute ?: 61 },
                        { it.name }
                    )
                )

                // 2. Check for Weekly Reset (Ensure UI shows fresh state even if Worker hasn't run yet)
                val processedSessions = weeklyResetUseCase(client, sortedSessions)

                _uiState.update {
                    it.copy(
                        client = client,
                        sessions = processedSessions,
                        isLoading = false,
                        error = null
                    )
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
                _uiState.update { it.copy(globalOccupiedSlots = map) }
            }
        }
    }

    fun addSession(name: String) {
        viewModelScope.launch {
            manageSessionUseCase.createSession(clientId, name)
        }
    }

    fun renameSession(session: Session, newName: String) {
        viewModelScope.launch {
            manageSessionUseCase.renameSession(clientId, session, newName)
        }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            manageSessionUseCase.deleteSession(session.id)
        }
    }

    fun toggleSkipSession(session: Session) {
        viewModelScope.launch {
            manageSessionUseCase.toggleSkipSession(clientId, session)
        }
    }

    fun updateSchedule(session: Session, day: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            manageSessionUseCase.updateSchedule(clientId, session, day, hour, minute)
        }
    }

    fun onReorderSessions(newOrder: List<Session>) {
        // Optimistic update
        _uiState.update { it.copy(sessions = newOrder) }

        // When reordering via drag-and-drop, we generally want to swap their schedule times
        // if they are scheduled, OR just change the list order if they aren't.
        // The previous implementation swapped schedule properties. We'll maintain that logic.

        val oldOrder =
            _uiState.value.sessions // This might be the already updated one if flow emitted fast?
        // Actually, for robust reordering, we should rely on the incoming list 'newOrder'
        // and apply the schedule slots from the positions they landed in,
        // OR just save the new list order.

        // Previous logic: Swapping schedule times based on index.
        // This is tricky because Flow updates come from DB.
        // For simple reordering, we just save the list order to DB.
        // BUT, if the list is sorted by Time in the UI (see loadData sorting),
        // dragging to reorder effectively means "Reschedule".

        // Since loadData sorts by time, manual reordering only really works for
        // sessions with the SAME time (or unscheduled ones).
        // The previous implementation tried to swap the time properties.

        viewModelScope.launch {
            // Get current DB state to ensure we have valid times to swap
            val currentSessions = repository.getSessionsForClient(clientId).first()
                .sortedWith(compareBy({ it.scheduledDay ?: 8 }, { it.scheduledHour ?: 25 }))

            val schedules = currentSessions.map {
                Triple(
                    it.scheduledDay,
                    it.scheduledHour,
                    it.scheduledMinute
                )
            }

            val reorderedWithSwappedSchedules = newOrder.mapIndexed { index, session ->
                if (index < schedules.size) {
                    val (day, hour, min) = schedules[index]
                    session.copy(scheduledDay = day, scheduledHour = hour, scheduledMinute = min)
                } else {
                    session
                }
            }

            manageSessionUseCase.updateSessionOrder(clientId, reorderedWithSwappedSchedules)
        }
    }
}
