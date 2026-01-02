package uk.co.fireburn.raiform.util

import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import java.util.Locale
import java.util.UUID // Add this import

object LegacyParser {

    data class ParseResult(
        val clientName: String,
        val sessions: List<Session>
    )

    /**
     * Regex Breakdown:
     * ^(.*?)\s-\s       -> Group 1: Exercise Name (Lazy match until ' - ')
     * (bw|[\d\.]+)\s*   -> Group 2: Weight (Either 'bw' or a number)
     * x\s*              -> Separator 'x'
     * (\d+)x(\d+)       -> Group 3 & 4: Sets x Reps
     * \s*(X)?           -> Group 5: Optional 'X' for maintain weight
     */
    private val EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s-\s(bw|[\d\.]+)\s*x\s*(\d+)x(\d+)\s*(X)?.*$""",
        option = RegexOption.IGNORE_CASE
    )

    // Updated Keywords
    private val SESSION_KEYWORDS =
        listOf("PUSH", "PULL", "LOWER", "UPPER", "LEGS", "FULL BODY", "ARMS", "CARDIO")

    fun parseLegacyNote(rawText: String): ParseResult {
        val lines = rawText.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) throw IllegalArgumentException("Empty text provided")

        // 1. First line is always Client Name (Apply Title Case)
        val clientName = lines.first().trim().toTitleCase()

        val sessions = mutableListOf<Session>()
        var currentSessionName = "Uncategorized"
        var currentExercises = mutableListOf<Exercise>()

        // 2. Iterate remaining lines
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val lineUpper = line.uppercase()

            // Check if line STARTS with a keyword AND is not an exercise
            val isHeader = SESSION_KEYWORDS.any { keyword ->
                lineUpper.startsWith(keyword)
            } && !line.contains(" - ")

            if (isHeader) {
                // Save previous session if it has exercises
                if (currentExercises.isNotEmpty()) {
                    sessions.add(
                        // Assign a dummy clientId here, it will be replaced during save in ImportLegacyNoteUseCase
                        Session(
                            clientId = UUID.randomUUID().toString(), // Dummy ID
                            name = currentSessionName,
                            exercises = currentExercises.toList()
                        )
                    )
                }
                // Start new session (Apply Title Case)
                currentSessionName = line.toTitleCase()
                currentExercises = mutableListOf()
            } else {
                // Try to parse as Exercise
                val exercise = parseExerciseLine(line)
                if (exercise != null) {
                    currentExercises.add(exercise)
                }
            }
        }

        // Add the final session
        if (currentExercises.isNotEmpty()) {
            sessions.add(
                Session(
                    clientId = UUID.randomUUID().toString(), // Dummy ID
                    name = currentSessionName,
                    exercises = currentExercises.toList()
                )
            )
        }

        return ParseResult(clientName, sessions)
    }

    private fun parseExerciseLine(line: String): Exercise? {
        val match = EXERCISE_REGEX.find(line) ?: return null
        val (name, weightStr, setsStr, repsStr, maintainStr) = match.destructured

        val isBodyweight = weightStr.equals("bw", ignoreCase = true)
        val weight = if (isBodyweight) 0.0 else weightStr.toDoubleOrNull() ?: 0.0
        val maintainWeight = maintainStr.equals("X", ignoreCase = true)

        return Exercise(
            name = name.trim().toTitleCase(), // Apply Title Case
            weight = weight,
            isBodyweight = isBodyweight,
            sets = setsStr.toIntOrNull() ?: 0,
            reps = repsStr.toIntOrNull() ?: 0,
            maintainWeight = maintainWeight
        )
    }

    // Helper Extension
    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
