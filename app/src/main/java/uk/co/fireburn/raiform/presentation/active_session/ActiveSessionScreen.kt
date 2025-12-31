package uk.co.fireburn.raiform.presentation.active_session

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import uk.co.fireburn.raiform.domain.model.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    navController: NavController,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session

    // State for the Edit Dialog
    var exerciseToEdit by remember { mutableStateOf<Exercise?>(null) }

    // State for Add Exercise Dialog
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.name ?: "Workout",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        // FIX: Use AutoMirrored icon to avoid deprecation warning
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (state.isLoading || session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. List of Exercises
                items(session.exercises) { exercise ->
                    ActiveExerciseCard(
                        exercise = exercise,
                        onToggle = { viewModel.toggleExerciseDone(exercise.id) },
                        onEdit = { exerciseToEdit = exercise } // Open Dialog
                    )
                }

                // 2. Add Exercise Button at bottom
                item {
                    Button(
                        onClick = { showAddExerciseDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ADD EXERCISE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- Dialogs ---

        // 1. Add Exercise Dialog
        if (showAddExerciseDialog) {
            AlertDialog(
                onDismissRequest = { showAddExerciseDialog = false },
                title = { Text("Add Exercise") },
                text = {
                    OutlinedTextField(
                        value = newExerciseName,
                        onValueChange = { newExerciseName = it },
                        label = { Text("Exercise Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newExerciseName.isNotBlank()) {
                            viewModel.addExercise(newExerciseName)
                            newExerciseName = "" // Reset
                            showAddExerciseDialog = false
                        }
                    }) {
                        Text("ADD")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddExerciseDialog = false }) { Text("CANCEL") }
                }
            )
        }

        // 2. Edit / Delete Dialog
        if (exerciseToEdit != null) {
            EditExerciseDialog(
                exercise = exerciseToEdit!!,
                onDismiss = { exerciseToEdit = null },
                onConfirm = { name, weight, sets, reps ->
                    viewModel.updateExerciseValues(exerciseToEdit!!.id, name, weight, sets, reps)
                    exerciseToEdit = null
                },
                onDelete = {
                    viewModel.deleteExercise(exerciseToEdit!!.id)
                    exerciseToEdit = null
                }
            )
        }
    }
}

@Composable
fun ActiveExerciseCard(
    exercise: Exercise,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val cardColor = if (exercise.isDone)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (exercise.isDone) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox (Click to toggle done)
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (exercise.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (exercise.isDone) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Info (Click to Edit)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() } // Click text to edit
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (exercise.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DataBadge(
                        text = if (exercise.isBodyweight) "BW" else "${exercise.weight}kg",
                        isActive = !exercise.isDone
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DataBadge(
                        text = "${exercise.sets} x ${exercise.reps}",
                        isActive = !exercise.isDone
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditExerciseDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int, Int) -> Unit,
    onDelete: () -> Unit
) {
    var nameText by remember { mutableStateOf(exercise.name) }
    var weightText by remember { mutableStateOf(if (exercise.isBodyweight) "0" else exercise.weight.toString()) }
    var setsText by remember { mutableStateOf(exercise.sets.toString()) }
    var repsText by remember { mutableStateOf(exercise.reps.toString()) }

    // Internal state to switch between Edit Mode and Delete Confirmation Mode
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        // --- DELETE CONFIRMATION ---
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Exercise?") },
            text = { Text("Are you sure you want to remove '${exercise.name}' from this workout?") },
            confirmButton = {
                Button(
                    onClick = { onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("REMOVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCEL") }
            }
        )
    } else {
        // --- EDIT MODE ---
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Name Input
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Exercise Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Weight Input (if not bodyweight)
                    if (!exercise.isBodyweight) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    // 3. Sets & Reps
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = setsText,
                            onValueChange = { setsText = it },
                            label = { Text("Sets") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val w = weightText.toDoubleOrNull() ?: 0.0
                    val s = setsText.toIntOrNull() ?: 0
                    val r = repsText.toIntOrNull() ?: 0
                    if (nameText.isNotBlank()) {
                        onConfirm(nameText, w, s, r)
                    }
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Icon Button
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                }
            }
        )
    }
}

@Composable
fun DataBadge(text: String, isActive: Boolean) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray) else null
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
        )
    }
}