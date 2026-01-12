package uk.co.fireburn.raiform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DartboardScheduleDialog(
    currentDay: Int,
    currentHour: Int,
    globalOccupiedSlots: Map<Int, List<Int>>,
    sessionToIgnoreId: String,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(currentDay) }

    val takenForDay = globalOccupiedSlots[selectedDay] ?: emptyList()
    // Don't show the current session's time as "Taken" so we can re-select it
    val displayTaken = if (selectedDay == currentDay) {
        takenForDay.filter { it != currentHour }
    } else {
        takenForDay
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Session") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Day Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (day in 1..7) {
                        val dayName = DayOfWeek.of(day)
                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            .take(1) // Just first letter for compactness

                        val isSelected = (day == selectedDay)
                        val bgColor =
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val textColor =
                            if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }

                // Clock
                DartboardClock(
                    takenHours = displayTaken,
                    selectedHour = if (selectedDay == currentDay) currentHour else -1,
                    onHourSelected = { hour -> onSave(selectedDay, hour) }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
