package uk.co.fireburn.raiform.util

import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import java.util.Locale
import java.util.UUID

object LegacyParser {

    data class MeasurementRaw(
        val type: String,
        val value: Double
    )

    data class ParseResult(
        val clientName: String,
        val sessions: List<Session>,
        val exerciseBodyParts: Map<String, String>, // Map<ExerciseName, BodyPart>
        val measurements: List<MeasurementRaw>
    )

    private val EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+[-–—]\s+(bw|[\d\.]+)(?:kg|lbs)?\s*[xX*]\s*(\d+)\s*[xX*]\s*(\d+).*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val SIMPLE_EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+(\d+)\s*reps?$""",
        option = RegexOption.IGNORE_CASE
    )

    private val MEASUREMENT_REGEX = Regex(
        pattern = """^(Shoulder width|Shoulders?|Arms?|Waist|Chest|Legs?|Weight|Body Weight)\s+(\d+(?:\.\d+)?)$""",
        option = RegexOption.IGNORE_CASE
    )

    // Explicit Body Part Headers
    private val BODY_PARTS = setOf(
        "CHEST", "LEGS", "SHOULDERS", "BICEPS", "TRICEPS", "BACK", "ABS", "ABS & CORE", "CARDIO"
    )

    // Session Keywords
    private val SESSION_KEYWORDS = listOf(
        "PUSH", "PULL", "LOWER", "UPPER", "FULL BODY", "DAY"
    )

    fun parseLegacyNote(rawText: String): ParseResult {
        val lines = rawText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) throw IllegalArgumentException("Empty text provided")

        val clientName = lines.first().trim().toTitleCase()

        val sessions = mutableListOf<Session>()
        val measurements = mutableListOf<MeasurementRaw>()
        val exerciseBodyPartMap = mutableMapOf<String, String>()

        var currentSessionName = "Imported Routine"
        var currentExercises = mutableListOf<Exercise>()
        var currentBodyPart = "Other"

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val lineUpper = line.uppercase()

            // A. Check for Body Part Header
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

            // C. Check for Session Header
            val isSessionHeader =
                (SESSION_KEYWORDS.any { lineUpper.startsWith(it) } || line.endsWith(":"))
                        && !line.contains("-") && !SIMPLE_EXERCISE_REGEX.matches(line)

            if (isSessionHeader) {
                if (currentExercises.isNotEmpty()) {
                    sessions.add(
                        Session(
                            clientId = UUID.randomUUID().toString(),
                            name = currentSessionName,
                            exercises = currentExercises.toList()
                        )
                    )
                }
                currentSessionName = line.removeSuffix(":").toTitleCase()
                currentExercises = mutableListOf()

                // Smart Context: If header is "LOWER...", switch body part to Legs
                if (lineUpper.contains("LOWER")) {
                    currentBodyPart = "Legs"
                } else if (lineUpper.contains("UPPER") || lineUpper.contains("PUSH") || lineUpper.contains(
                        "PULL"
                    )
                ) {
                    // Reset to "Other" for mixed days, forcing per-exercise guessing
                    currentBodyPart = "Other"
                }
            } else {
                // D. Parse Exercise
                val exercise = parseExerciseLine(line)
                if (exercise != null) {
                    currentExercises.add(exercise)

                    // Determine Body Part
                    // 1. Use the current header if specific (e.g. under "CHEST" or "LOWER")
                    // 2. If generic ("Other"), try to guess from the name
                    val determinedBodyPart = if (currentBodyPart == "Other") {
                        guessBodyPart(exercise.name)
                    } else {
                        currentBodyPart
                    }

                    exerciseBodyPartMap[exercise.name] = determinedBodyPart
                }
            }
        }

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

    private fun guessBodyPart(name: String): String {
        val n = name.lowercase()
        return when {
            n.containsAny(
                "squat",
                "leg",
                "lunge",
                "calf",
                "hamstring",
                "quad",
                "glute",
                "split",
                "deadlift",
                "hip thrust",
                "abduction"
            ) -> "Legs"

            n.containsAny(
                "bench",
                "chest",
                "fly",
                "pec",
                "push up",
                "press"
            ) && !n.contains("leg") && !n.contains("shoulder") && !n.contains("overhead") -> "Chest"

            n.containsAny(
                "pull up",
                "pull down",
                "row",
                "lat",
                "chin up",
                "back",
                "rack pull"
            ) -> "Back"

            n.containsAny(
                "shoulder",
                "delts",
                "raise",
                "face pull",
                "overhead",
                "military",
                "arnold",
                "clean"
            ) -> "Shoulders"

            n.containsAny("curl", "bicep", "hammer", "preacher") -> "Biceps"
            n.containsAny(
                "tricep",
                "extension",
                "skull",
                "dip",
                "pushdown",
                "kickback"
            ) -> "Triceps"

            n.containsAny("abs", "core", "plank", "crunch", "sit up", "leg raise") -> "Abs & Core"
            n.containsAny("run", "cardio", "treadmill", "bike", "rowing", "elliptical") -> "Cardio"
            else -> "Other"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
