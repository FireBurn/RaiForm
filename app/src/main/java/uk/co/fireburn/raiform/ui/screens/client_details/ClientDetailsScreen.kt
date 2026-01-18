package uk.co.fireburn.raiform.ui.screens.client_details

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.ui.components.DartboardScheduleDialog
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class SessionAction { Rename, Delete, Schedule, ToggleSkip }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String, String) -> Unit,
    onNavigateToStats: (String) -> Unit,
    onNavigateToMeasurements: (String) -> Unit,
    onNavigateToClient: (String, String?, Int?) -> Unit,
    viewModel: ClientDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    var addToFullRoutine by remember { mutableStateOf(false) }

    var sessionToSchedule by remember { mutableStateOf<Session?>(null) }
    var sessionToScheduleDay by remember { mutableStateOf<Int?>(null) }

    var sessionToRename by remember { mutableStateOf<Session?>(null) }

    // Conflict State
    var conflictData by remember { mutableStateOf<ClientDetailsEvent.ShowConflictDialog?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ClientDetailsEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is ClientDetailsEvent.ShowConflictDialog -> {
                    conflictData = event
                }

                is ClientDetailsEvent.NavigateToClient -> {
                    onNavigateToClient(event.clientId, event.sessionId, event.day)
                }

                is ClientDetailsEvent.OpenScheduleDialog -> {
                    sessionToSchedule = event.session
                    sessionToScheduleDay = event.defaultDay
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val list = state.sessions.toMutableList()
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            viewModel.onReorderSessions(list)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.client?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("Weekly Schedule", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.client != null) {
                    SmallFloatingActionButton(
                        onClick = { onNavigateToMeasurements(state.client!!.id) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = "Measurements")
                    }

                    SmallFloatingActionButton(
                        onClick = { onNavigateToStats(state.client!!.id) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Stats")
                    }
                }

                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            val fullRoutineSessions =
                state.sessions.filter { it.groupId != null && it.name.startsWith("Full Routine") }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    val displayName =
                        if (session.groupId != null && session.name.startsWith("Full Routine")) {
                            if (session.scheduledDay != null) {
                                val dayName = DayOfWeek.of(session.scheduledDay)
                                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                                "Full Routine $dayName"
                            } else {
                                val index = fullRoutineSessions.indexOf(session) + 1
                                "Full Routine $index"
                            }
                        } else {
                            session.name
                        }

                    ReorderableItem(reorderableState, key = session.id) { isDragging ->
                        val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)

                        Box(modifier = Modifier.shadow(elevation.value)) {
                            SessionCard(
                                session = session.copy(name = displayName),
                                onClick = {
                                    if (state.client != null) {
                                        onNavigateToSession(state.client!!.id, session.id)
                                    }
                                },
                                onAction = { action ->
                                    when (action) {
                                        SessionAction.Rename -> sessionToRename = session
                                        SessionAction.Delete -> viewModel.deleteSession(session)
                                        SessionAction.Schedule -> {
                                            sessionToSchedule = session
                                            sessionToScheduleDay = null
                                        }

                                        SessionAction.ToggleSkip -> viewModel.toggleSkipSession(
                                            session
                                        )
                                    }
                                },
                                dragHandle = {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        modifier = Modifier.draggableHandle(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Dialogs ---

        if (sessionToSchedule != null) {
            val clientName = state.client?.name ?: ""
            val defaultDay = sessionToScheduleDay ?: sessionToSchedule!!.scheduledDay ?: 1

            DartboardScheduleDialog(
                title = "Reschedule $clientName: ${sessionToSchedule!!.name}",
                currentDay = defaultDay,
                currentHour = sessionToSchedule!!.scheduledHour ?: -1,
                globalOccupiedSlots = state.globalOccupiedSlots,
                sessionToIgnoreId = sessionToSchedule!!.id,
                onDismiss = {
                    sessionToSchedule = null
                    sessionToScheduleDay = null
                },
                onSkip = {
                    viewModel.toggleSkipSession(sessionToSchedule!!)
                    sessionToSchedule = null
                    sessionToScheduleDay = null
                },
                onNextWeek = {
                    viewModel.toggleSkipSession(sessionToSchedule!!)
                    sessionToSchedule = null
                    sessionToScheduleDay = null
                },
                onSave = { d, h ->
                    viewModel.tryUpdateSchedule(sessionToSchedule!!, d, h)
                    sessionToSchedule = null
                    sessionToScheduleDay = null
                }
            )
        }

        if (conflictData != null) {
            val data = conflictData!!
            AlertDialog(
                onDismissRequest = { conflictData = null },
                title = { Text("Slot Occupied") },
                text = { Text("This time is already taken by ${data.conflictName}. Do you want to unschedule them and take this slot?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.forceUpdateSchedule(
                                data.sessionToSchedule,
                                data.conflictingSession,
                                data.targetDay,
                                data.targetHour
                            )
                            conflictData = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Overwrite & Fix") }
                },
                dismissButton = {
                    TextButton(onClick = { conflictData = null }) { Text("Cancel") }
                }
            )
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("New Session") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newSessionName,
                            onValueChange = { newSessionName = it },
                            label = { Text("Session Name") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { addToFullRoutine = !addToFullRoutine }
                        ) {
                            Checkbox(
                                checked = addToFullRoutine,
                                onCheckedChange = { addToFullRoutine = it }
                            )
                            Text("Add to Full Routine Group")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newSessionName.isNotBlank() || addToFullRoutine) {
                            viewModel.addSession(newSessionName, addToFullRoutine)
                            newSessionName = ""
                            addToFullRoutine = false
                            showAddDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (sessionToRename != null) {
            var renameText by remember { mutableStateOf(sessionToRename!!.name) }
            AlertDialog(
                onDismissRequest = { sessionToRename = null },
                title = { Text("Rename Session") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Session Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameSession(sessionToRename!!, renameText)
                            sessionToRename = null
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToRename = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// ... SessionCard is unchanged
@Composable
fun SessionCard(
    session: Session,
    onClick: () -> Unit,
    onAction: (SessionAction) -> Unit,
    dragHandle: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val scheduleText = if (session.isSkippedThisWeek) {
        "Cancelled for this week"
    } else if (session.scheduledDay != null) {
        val day =
            DayOfWeek.of(session.scheduledDay).getDisplayName(TextStyle.FULL, Locale.getDefault())
        val hour = session.scheduledHour ?: 0
        val minute = session.scheduledMinute ?: 0
        val amPm = if (hour >= 12) "pm" else "am"
        val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour

        val time = if (minute == 0) "$h$amPm" else String.format(
            Locale.getDefault(),
            "%d:%02d%s",
            h,
            minute,
            amPm
        )
        "$day @ $time"
    } else {
        "Unscheduled"
    }

    val cardColor =
        if (session.isSkippedThisWeek) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!session.isSkippedThisWeek) onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dragHandle()
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (session.isSkippedThisWeek) Color.Gray else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.isSkippedThisWeek) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (session.scheduledDay != null) "Reschedule" else "Schedule") },
                        onClick = { showMenu = false; onAction(SessionAction.Schedule) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (session.isSkippedThisWeek) "Restore" else "Cancel") },
                        onClick = { showMenu = false; onAction(SessionAction.ToggleSkip) },
                        leadingIcon = { Icon(Icons.Default.Cancel, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onAction(SessionAction.Rename) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onAction(SessionAction.Delete) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
