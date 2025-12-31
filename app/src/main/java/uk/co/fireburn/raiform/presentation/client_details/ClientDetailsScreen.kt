package uk.co.fireburn.raiform.presentation.client_details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import uk.co.fireburn.raiform.domain.model.Session
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailsScreen(
    navController: NavController,
    viewModel: ClientDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }

    // Scheduling Dialog
    var sessionToSchedule by remember { mutableStateOf<Session?>(null) }

    // Rename Dialog
    var sessionToRename by remember { mutableStateOf<Session?>(null) }

    // Reorder State
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val list = state.sessions.toMutableList()
            val item = list.removeAt(from.index)
            list.add(to.index, item)
            viewModel.onReorderSessions(list)
        }
    )

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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                state = reorderState.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .reorderable(reorderState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    ReorderableItem(reorderState, key = session.id) { isDragging ->
                        val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)

                        Box(modifier = Modifier.shadow(elevation.value)) {
                            SessionCard(
                                session = session,
                                onClick = { navController.navigate("active_session/${state.client?.id}/${session.id}") },
                                onAction = { action ->
                                    when (action) {
                                        SessionAction.Rename -> sessionToRename = session
                                        SessionAction.Delete -> viewModel.deleteSession(session)
                                        SessionAction.Schedule -> sessionToSchedule = session
                                        SessionAction.ToggleSkip -> viewModel.toggleSkipSession(
                                            session
                                        )
                                    }
                                },
                                dragHandle = {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        // FIX: Use proper detection modifier for this library version
                                        modifier = Modifier.detectReorderAfterLongPress(reorderState)
                                    )
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // --- DIALOGS ---

        // 1. Scheduler Dialog
        if (sessionToSchedule != null) {
            ScheduleDialog(
                currentDay = sessionToSchedule!!.scheduledDay ?: 1,
                currentHour = sessionToSchedule!!.scheduledHour ?: 9,
                currentMinute = sessionToSchedule!!.scheduledMinute ?: 0,
                onDismiss = { sessionToSchedule = null },
                onSave = { d, h, m ->
                    viewModel.updateSchedule(sessionToSchedule!!, d, h, m)
                    sessionToSchedule = null
                }
            )
        }

        // 2. Add Session Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("New Session") },
                text = {
                    OutlinedTextField(
                        value = newSessionName,
                        onValueChange = { newSessionName = it },
                        label = { Text("Session Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newSessionName.isNotBlank()) {
                            viewModel.addSession(newSessionName)
                            newSessionName = ""
                            showAddDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }

        // 3. Rename Dialog
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

enum class SessionAction { Rename, Delete, Schedule, ToggleSkip }

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
            DayOfWeek.of(session.scheduledDay).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val time =
            String.format("%02d:%02d", session.scheduledHour ?: 0, session.scheduledMinute ?: 0)
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
                        text = { Text("Schedule") },
                        onClick = { showMenu = false; onAction(SessionAction.Schedule) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (session.isSkippedThisWeek) "Restore Week" else "Cancel Week") },
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

@Composable
fun ScheduleDialog(
    currentDay: Int,
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int) -> Unit
) {
    var selectedDay by remember { mutableStateOf(currentDay) }
    var selectedHour by remember { mutableStateOf(currentHour) }
    var selectedMinute by remember { mutableStateOf(currentMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Schedule") },
        text = {
            Column {
                Text("Day of Week (1=Mon ... 7=Sun)")
                Slider(
                    value = selectedDay.toFloat(),
                    onValueChange = { selectedDay = it.toInt() },
                    valueRange = 1f..7f,
                    steps = 5
                )
                Text(DayOfWeek.of(selectedDay).name)

                Spacer(Modifier.height(16.dp))

                Text("Hour (0-23)")
                Slider(
                    value = selectedHour.toFloat(),
                    onValueChange = { selectedHour = it.toInt() },
                    valueRange = 0f..23f
                )
                Text("$selectedHour:${String.format("%02d", selectedMinute)}")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedDay, selectedHour, selectedMinute) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}