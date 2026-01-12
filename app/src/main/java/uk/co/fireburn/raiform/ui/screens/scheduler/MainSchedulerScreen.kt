package uk.co.fireburn.raiform.ui.screens.scheduler

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NextWeek
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import uk.co.fireburn.raiform.ui.components.DartboardClock
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSchedulerScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainSchedulerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SchedulerEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduler") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isAllSchedulingComplete) {
            SchedulingCompleteView(
                modifier = Modifier.padding(padding),
                onBack = onNavigateBack
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. DROPDOWNS ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val highlightColor = MaterialTheme.colorScheme.primaryContainer

                    // --- Client Dropdown with Pulse ---
                    val clientPulse =
                        remember { androidx.compose.animation.Animatable(Color.Transparent) }
                    LaunchedEffect(state.selectedClient) {
                        clientPulse.animateTo(highlightColor, tween(200))
                        clientPulse.animateTo(Color.Transparent, tween(500))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = state.selectedClient?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth()
                                    .background(clientPulse.value, RoundedCornerShape(4.dp)),
                                label = { Text("Client") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                state.clients.forEach { client ->
                                    DropdownMenuItem(
                                        text = { Text(client.name) },
                                        onClick = {
                                            viewModel.selectClient(client)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // --- Session Dropdown with Pulse ---
                    val sessionPulse =
                        remember { androidx.compose.animation.Animatable(Color.Transparent) }
                    LaunchedEffect(state.selectedSession) {
                        sessionPulse.animateTo(highlightColor, tween(200))
                        sessionPulse.animateTo(Color.Transparent, tween(500))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = state.selectedSession?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth()
                                    .background(sessionPulse.value, RoundedCornerShape(4.dp)),
                                label = { Text("Session") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                state.clientSessions.forEach { session ->
                                    val timeInfo = if (session.scheduledDay != null) {
                                        val d = DayOfWeek.of(session.scheduledDay)
                                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                        " ($d ${session.scheduledHour}h)"
                                    } else ""

                                    DropdownMenuItem(
                                        text = { Text("${session.name}$timeInfo") },
                                        onClick = {
                                            viewModel.selectSession(session)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. DAY SELECTOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (day in 1..7) {
                        val dayName = DayOfWeek.of(day)
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault())

                        DayCircle(
                            text = dayName,
                            isSelected = state.selectedDay == day,
                            onClick = { viewModel.selectDay(day) }
                        )
                    }
                }

                // 3. CLOCK (Takes remaining space)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val taken = state.occupiedSlots[state.selectedDay] ?: emptyList()
                    DartboardClock(
                        takenHours = taken,
                        selectedHour = state.selectedHour,
                        onHourSelected = { viewModel.selectHour(it) }
                    )
                }

                // 4. MAIN ACTION BUTTON (Confirm)
                val isConflict = state.detectedConflictName != null
                val btnContainerColor =
                    if (isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                val btnContentColor =
                    if (isConflict) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

                Button(
                    onClick = { viewModel.confirmSchedule() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = btnContainerColor),
                    enabled = state.selectedHour != -1
                ) {
                    if (isConflict) {
                        Icon(Icons.Default.Warning, null, tint = btnContentColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "OVERWRITE ${state.detectedConflictName?.uppercase()}?",
                            color = btnContentColor,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.Check, null, tint = btnContentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("CONFIRM TIME", color = btnContentColor, fontWeight = FontWeight.Bold)
                    }
                }

                // 5. SECONDARY ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.skipSession() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SKIP", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.moveToNextWeek() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NextWeek,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("NEXT WEEK", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DayCircle(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor
        )
    }
}

@Composable
fun SchedulingCompleteView(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Done",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "All Done!",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Everyone is scheduled for the week.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
        ) {
            Text("Back to Dashboard")
        }
    }
}
