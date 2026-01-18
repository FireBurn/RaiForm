package uk.co.fireburn.raiform.util

import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import java.util.Locale
import java.util.UUID

object LegacyParser {

    data class MeasurementRaw(
        val type: String, // "Weight", "Waist", "Arm", etc.
        val value: Double
    )

    data class ParseResult(
        val clientName: String,
        val sessions: List<Session>,
        val exerciseBodyParts: Map<String, String>, // Map<ExerciseName, BodyPart>
        val measurements: List<MeasurementRaw>
    )

    /**
     * Regex Breakdown (Standard):
     * ^(.*?)\s+[-–—]\s+  -> Name + separator
     * (bw|[\d\.]+)\s*    -> Weight
     * ... sets x reps ...
     */
    private val EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+[-–—]\s+(bw|[\d\.]+)(?:kg|lbs)?\s*[xX*]\s*(\d+)\s*[xX*]\s*(\d+).*$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Fallback Regex for simple formats like "Sit up 50 reps"
     * Group 1: Name
     * Group 2: Reps
     */
    private val SIMPLE_EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+(\d+)\s*reps?$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Regex for Measurements
     * Group 1: Type (Shoulder width, Arm, Waist, etc)
     * Group 2: Value
     */
    private val MEASUREMENT_REGEX = Regex(
        pattern = """^(Shoulder width|Shoulders?|Arms?|Waist|Chest|Legs?|Weight|Body Weight)\s+(\d+(?:\.\d+)?)$""",
        option = RegexOption.IGNORE_CASE
    )

    // Body Parts to identify headers
    private val BODY_PARTS = setOf(
        "CHEST", "LEGS", "SHOULDERS", "BICEPS", "TRICEPS", "BACK", "ABS", "ABS & CORE", "CARDIO"
    )

    // Session Keywords (still used if user explicitly splits days)
    private val SESSION_KEYWORDS = listOf(
        "PUSH", "PULL", "LOWER", "UPPER", "FULL BODY", "DAY"
    )

    fun parseLegacyNote(rawText: String): ParseResult {
        val lines = rawText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) throw IllegalArgumentException("Empty text provided")

        // 1. First line is Client Name
        val clientName = lines.first().trim().toTitleCase()

        val sessions = mutableListOf<Session>()
        val measurements = mutableListOf<MeasurementRaw>()
        val exerciseBodyPartMap = mutableMapOf<String, String>()

        var currentSessionName = "Imported Routine"
        var currentExercises = mutableListOf<Exercise>()
        var currentBodyPart = "Other" // Default body part

        // 2. Iterate
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val lineUpper = line.uppercase()

            // A. Check for Body Part Header
            // Exact match (case insensitive) against known list
            if (BODY_PARTS.contains(lineUpper)) {
                currentBodyPart = line.toTitleCase()
                continue
            }

            // B. Check for Measurements
            val measureMatch = MEASUREMENT_REGEX.find(line)
            if (measureMatch != null) {
                val (type, valStr) = measureMatch.destructured
                val value = valStr.toDoubleOrNull()
                if (value != null) {
                    measurements.add(MeasurementRaw(type.toTitleCase(), value))
                }
                continue
            }

            // C. Check for Session Header (e.g. "Day 1", "Push")
            // Only if it doesn't look like an exercise (no separator)
            val isSessionHeader =
                (SESSION_KEYWORDS.any { lineUpper.startsWith(it) } || line.endsWith(":"))
                        && !line.contains("-") && !SIMPLE_EXERCISE_REGEX.matches(line)

            if (isSessionHeader) {
                // Save previous session
                if (currentExercises.isNotEmpty()) {
                    sessions.add(
                        Session(
                            clientId = UUID.randomUUID().toString(),
                            name = currentSessionName,
                            exercises = currentExercises.toList()
                        )
                    )
                }
                // Start new session
                currentSessionName = line.removeSuffix(":").toTitleCase()
                currentExercises = mutableListOf()
                // Reset body part for new session? Or keep?
                // Usually keeps unless changed, but let's default back to Other if ambiguous
                // For now, we keep previous body part context as user might list "Chest" then split days
            } else {
                // D. Parse Exercise
                val exercise = parseExerciseLine(line)
                if (exercise != null) {
                    currentExercises.add(exercise)
                    // Map this exercise name to the current body part
                    exerciseBodyPartMap[exercise.name] = currentBodyPart
                }
            }
        }

        // Add final session
        if (currentExercises.isNotEmpty()) {
            sessions.add(
                Session(
                    clientId = UUID.randomUUID().toString(),
                    name = currentSessionName,
                    exercises = currentExercises.toList()
                )
            )
        }

        return ParseResult(clientName, sessions, exerciseBodyPartMap, measurements)
    }

    private fun parseExerciseLine(line: String): Exercise? {
        // Try Standard Format (Name - Weight x Sets x Reps)
        var match = EXERCISE_REGEX.find(line)
        if (match != null) {
            val (name, weightStr, setsStr, repsStr) = match.destructured
            val maintainWeight = line.trim().endsWith("X", ignoreCase = true) ||
                    line.contains("(M)", ignoreCase = true)
            val isBodyweight = weightStr.equals("bw", ignoreCase = true)
            val weight = if (isBodyweight) 0.0 else weightStr.toDoubleOrNull() ?: 0.0

            return Exercise(
                name = name.trim().toTitleCase(),
                weight = weight,
                isBodyweight = isBodyweight,
                sets = setsStr.toIntOrNull() ?: 0,
                reps = repsStr.toIntOrNull() ?: 0,
                maintainWeight = maintainWeight
            )
        }

        // Try Simple Format (Name 50 reps)
        match = SIMPLE_EXERCISE_REGEX.find(line)
        if (match != null) {
            val (name, repsStr) = match.destructured
            return Exercise(
                name = name.trim().toTitleCase(),
                weight = 0.0,
                isBodyweight = true,
                sets = 1,
                reps = repsStr.toIntOrNull() ?: 0
            )
        }

        return null
    }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
