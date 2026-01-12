package uk.co.fireburn.raiform.ui.screens.active_session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import uk.co.fireburn.raiform.domain.model.Exercise
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session
    val context = LocalContext.current

    // State to hold the active ringtone so we can stop it later
    var activeRingtone by remember { mutableStateOf<Ringtone?>(null) }
    var isAlarmRinging by remember { mutableStateOf(false) }

    // Cleanup ringtone if user leaves screen
    DisposableEffect(Unit) {
        onDispose {
            activeRingtone?.stop()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ActiveSessionEvent.TimerFinished -> {
                    // Play sound and vibrate
                    activeRingtone = playAlarmSound(context)
                    vibratePhone(context)
                    isAlarmRinging = true
                }
            }
        }
    }

    var exerciseInDialog by remember { mutableStateOf<Exercise?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onNavigateBack) {
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
        },
        containerColor = MaterialTheme.colorScheme.background
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
                // 1. Rest Timer Header (Modified)
                item {
                    RestTimerHeader(
                        isRunning = state.isTimerRunning,
                        isAlarmRinging = isAlarmRinging,
                        timeLeft = state.timerValue,
                        totalTime = state.timerTotalTime,
                        onStart = {
                            // Ensure previous alarm is stopped before starting new
                            activeRingtone?.stop()
                            isAlarmRinging = false
                            viewModel.startTimer(it)
                        },
                        onStop = {
                            // Stop Timer Logic
                            viewModel.stopTimer()
                            activeRingtone?.stop()
                            isAlarmRinging = false
                        }
                    )
                }

                // 2. List of Exercises
                items(session.exercises, key = { it.id }) { exercise ->
                    ActiveExerciseCard(
                        exercise = exercise,
                        onToggle = { viewModel.toggleExerciseDone(exercise.id) },
                        onEdit = {
                            isCreatingNew = false
                            exerciseInDialog = exercise
                        },
                        onToggleMaintain = { viewModel.toggleMaintainWeight(exercise.id) }
                    )
                }

                item {
                    Button(
                        onClick = {
                            isCreatingNew = true
                            exerciseInDialog =
                                Exercise(name = "", sets = 3, reps = 10, weight = 0.0)
                        },
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
                        Text("NEW EXERCISE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (exerciseInDialog != null) {
            ExerciseDialog(
                exercise = exerciseInDialog!!,
                isNew = isCreatingNew,
                onDismiss = { exerciseInDialog = null },
                onConfirm = { name, weight, isBodyweight, sets, reps ->
                    if (isCreatingNew) {
                        viewModel.addExercise(name, weight, isBodyweight, sets, reps)
                    } else {
                        viewModel.updateExerciseValues(
                            exerciseInDialog!!.id,
                            name,
                            weight,
                            isBodyweight,
                            sets,
                            reps
                        )
                    }
                    exerciseInDialog = null
                },
                onDelete = {
                    if (!isCreatingNew) {
                        viewModel.deleteExercise(exerciseInDialog!!.id)
                    }
                    exerciseInDialog = null
                }
            )
        }
    }
}

/**
 * Plays the default alarm sound.
 * Checks for Silent/Vibrate mode first to avoid disruption.
 * Sets AudioAttributes to ALARM to ensure correct routing.
 */
private fun playAlarmSound(context: Context): Ringtone? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Don't play sound if the phone is in Silent or Vibrate mode
    if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
        audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
    ) {
        return null
    }

    return try {
        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val r = RingtoneManager.getRingtone(context, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true // Loop until stopped
        }
        r.play()
        r
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun vibratePhone(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Vibrate for 1 second
    vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
fun RestTimerHeader(
    isRunning: Boolean,
    isAlarmRinging: Boolean,
    timeLeft: Int,
    totalTime: Int,
    onStart: (Int) -> Unit,
    onStop: () -> Unit
) {
    if (isAlarmRinging) {
        // --- ALARM RINGING STATE ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { onStop() }, // Click anywhere to stop
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "STOP ALARM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    } else if (isRunning) {
        // --- COUNTDOWN STATE ---
        val progressFraction by animateFloatAsState(
            targetValue = if (totalTime > 0) timeLeft.toFloat() / totalTime.toFloat() else 0f,
            label = "TimerProgress",
            animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { onStop() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Fill Animation
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        .align(Alignment.CenterStart)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${timeLeft}s",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    } else {
        // --- IDLE STATE (Two Buttons) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { onStart(60) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("1 MIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = { onStart(120) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("2 MINS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// Helper to format Double weight (removes .0 if whole number)
private fun formatWeight(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

@Composable
fun ActiveExerciseCard(
    exercise: Exercise,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onToggleMaintain: () -> Unit
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
            // Drag handle removed
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (exercise.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (exercise.isDone) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
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
                    val weightText = if (exercise.isBodyweight) {
                        when {
                            exercise.weight > 0 -> "BW + ${formatWeight(exercise.weight)}kg"
                            exercise.weight < 0 -> "BW - ${formatWeight(abs(exercise.weight))}kg"
                            else -> "Body Weight"
                        }
                    } else {
                        "${formatWeight(exercise.weight)}kg"
                    }
                    DataBadge(text = weightText, isActive = !exercise.isDone)
                    Spacer(modifier = Modifier.width(8.dp))
                    DataBadge(
                        text = "${exercise.sets} x ${exercise.reps}",
                        isActive = !exercise.isDone
                    )
                }
            }
            IconButton(onClick = onToggleMaintain) {
                if (exercise.maintainWeight) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Maintain Weight",
                        tint = Color.Red,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Increase Weight",
                        tint = Color.Green,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDialog(
    exercise: Exercise,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Boolean, Int, Int) -> Unit,
    onDelete: () -> Unit
) {
    var nameText by remember { mutableStateOf(exercise.name) }
    // Use formatWeight for initial display
    var weightText by remember { mutableStateOf(formatWeight(abs(exercise.weight))) }
    var weightModifier by remember { mutableIntStateOf(if (exercise.weight >= 0) 1 else -1) }
    var setsText by remember { mutableStateOf(exercise.sets.toString()) }
    var repsText by remember { mutableStateOf(exercise.reps.toString()) }
    var isBodyweight by remember { mutableStateOf(exercise.isBodyweight) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Exercise?") },
            text = { Text("Are you sure you want to remove '${exercise.name}'?") },
            confirmButton = {
                Button(
                    onClick = { onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("REMOVE") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCEL") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isNew) "New Exercise" else "Edit Exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = nameText, onValueChange = { nameText = it },
                        label = { Text("Exercise Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Body Weight Exercise?")
                        Switch(checked = isBodyweight, onCheckedChange = { isBodyweight = it })
                    }
                    if (isBodyweight) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = weightModifier == 1,
                                onClick = { weightModifier = 1 },
                                label = { Text("Weighted (+)") },
                                leadingIcon = if (weightModifier == 1) {
                                    { Icon(Icons.Default.Done, null) }
                                } else null
                            )
                            FilterChip(
                                selected = weightModifier == -1,
                                onClick = { weightModifier = -1 },
                                label = { Text("Assisted (-)") },
                                leadingIcon = if (weightModifier == -1) {
                                    { Icon(Icons.Default.Done, null) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = weightText, onValueChange = { weightText = it },
                        label = {
                            Text(if (isBodyweight) "Modifier Weight (kg)" else "Weight (kg)")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    val finalWeight = if (isBodyweight) w * weightModifier else w
                    val s = setsText.toIntOrNull() ?: 0
                    val r = repsText.toIntOrNull() ?: 0
                    if (nameText.isNotBlank()) {
                        onConfirm(nameText, finalWeight, isBodyweight, s, r)
                    }
                }) { Text("SAVE") }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete, "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
