package uk.co.fireburn.raiform.presentation.scheduler

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.NextWeek
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import uk.co.fireburn.raiform.domain.model.Client
import uk.co.fireburn.raiform.domain.model.Session
import uk.co.fireburn.raiform.presentation.components.DartboardClock
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSchedulerScreen(
    navController: NavController,
    viewModel: MainSchedulerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Scheduler") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. SELECT CLIENT
            Text(
                "1. Select Client",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.clients) { client ->
                    ClientChip(
                        client = client,
                        isSelected = state.selectedClient?.id == client.id,
                        onClick = { viewModel.selectClient(client) }
                    )
                }
            }

            // 2. SELECT SESSION
            if (state.clientSessions.isNotEmpty()) {
                Text(
                    "2. Select Session",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.clientSessions) { session ->
                        SessionChip(
                            session = session,
                            isSelected = state.selectedSession?.id == session.id,
                            onClick = { viewModel.selectSession(session) }
                        )
                    }
                }
            }

            // 3. SCHEDULE (Day & Time)
            if (state.selectedSession != null) {
                Text(
                    "3. Assign Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Day Buttons (Mon - Sun)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // Small gap between buttons
                ) {
                    for (day in 1..7) {
                        // Use SHORT style for "Mon", "Tue", etc.
                        val dayName =
                            DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

                        DayButton(
                            text = dayName,
                            isSelected = state.selectedDay == day,
                            onClick = { viewModel.selectDay(day) },
                            modifier = Modifier.weight(1f) // Distribute width equally
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dartboard Clock (Auto-confirms on selection)
                val taken = state.occupiedSlots[state.selectedDay] ?: emptyList()

                DartboardClock(
                    takenHours = taken,
                    selectedHour = state.selectedHour,
                    onHourSelected = { viewModel.selectHour(it) } // Triggers auto-advance
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. ACTION BUTTONS (Skip / Next Week)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Skip Session
                    Button(
                        onClick = { viewModel.skipSession() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, null)
                        Spacer(Modifier.width(8.dp))
                        Text("SKIP")
                    }

                    // Move to Next Week
                    Button(
                        onClick = { viewModel.moveToNextWeek() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.NextWeek, null)
                        Spacer(Modifier.width(8.dp))
                        Text("NEXT WEEK")
                    }
                }
            } else if (state.selectedClient != null && state.clientSessions.isNotEmpty()) {
                // All done state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "All sessions scheduled!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.Green
                    )
                }
            }
        }
    }
}

@Composable
fun ClientChip(client: Client, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(client.name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.Black
        )
    )
}

@Composable
fun SessionChip(session: Session, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(session.name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = Color.Black
        )
    )
}

@Composable
fun DayButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp) // Comfortable touch height
            .clip(CircleShape) // Makes it a stadium/capsule shape
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp, // Slightly smaller text to fit "Mon" in tight spaces
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
        )
    }
}