package uk.co.fireburn.raiform.ui.screens.stats

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClientStatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress & Stats") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Fun Fact Card
                item {
                    FunFactCard(
                        totalVolume = state.totalVolumeKg,
                        totalReps = state.totalReps,
                        fact = state.funFact
                    )
                }

                // 2. Personal Bests
                item {
                    Text(
                        "Personal Bests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.personalBests.isEmpty()) {
                        Text("Complete workouts to see PBs!", color = Color.Gray)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.personalBests) { pb ->
                                PBCard(pb)
                            }
                        }
                    }
                }

                // 3. Progress Graph
                item {
                    Text(
                        "Progress Over Time",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    if (state.exerciseNames.isNotEmpty()) {
                        ExerciseSelector(
                            exercises = state.exerciseNames,
                            selected = state.selectedGraphExercise,
                            onSelect = { viewModel.selectGraphExercise(it) }
                        )
                        Spacer(Modifier.height(16.dp))

                        val dataPoints = state.graphData[state.selectedGraphExercise] ?: emptyList()
                        if (dataPoints.size >= 2) {
                            ProgressChart(dataPoints)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Need at least 2 logs to show graph", color = Color.Gray)
                            }
                        }
                    } else {
                        Text("No data available yet.", color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ... FunFactCard, PBCard remain same ...
@Composable
fun FunFactCard(totalVolume: Double, totalReps: Int, fact: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "LIFETIME STATS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fact,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Total Volume", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${totalVolume.toInt()} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text("Total Reps", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "$totalReps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PBCard(pb: PersonalBest) {
    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(pb.date))
    Card(
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700))
            Spacer(Modifier.height(4.dp))
            Text(pb.exerciseName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                text = if (pb.isBodyweight) "BW" else "${pb.weight}kg",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelector(
    exercises: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected ?: "Select Exercise",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            exercises.forEach { exercise ->
                DropdownMenuItem(
                    text = { Text(exercise) },
                    onClick = {
                        onSelect(exercise)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ProgressChart(data: List<Pair<Long, Double>>) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        factory = { context ->
            LineChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = false
                axisRight.isEnabled = false

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.textColor = textColor
                xAxis.valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return sdf.format(Date(value.toLong()))
                    }
                }

                axisLeft.textColor = textColor
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
            }
        },
        update = { chart ->
            val entries = data.map { Entry(it.first.toFloat(), it.second.toFloat()) }
            val dataSet = LineDataSet(entries, "Progress").apply {
                color = primaryColor
                valueTextColor = textColor
                lineWidth = 2f
                circleRadius = 4f
                setCircleColor(primaryColor)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = primaryColor
                fillAlpha = 50
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
