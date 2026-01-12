package uk.co.fireburn.raiform.util

import uk.co.fireburn.raiform.domain.model.Exercise
import uk.co.fireburn.raiform.domain.model.Session
import java.util.Locale
import java.util.UUID

object LegacyParser {

    data class ParseResult(
        val clientName: String,
        val sessions: List<Session>
    )

    /**
     * Regex Breakdown:
     * ^(.*?)\s+[-–—]\s+  -> Group 1: Name. Ends with hyphen/dash/emdash surrounded by space.
     * (bw|[\d\.]+)\s*    -> Group 2: Weight. 'bw' OR digits/dots.
     * (?:kg|lbs)?\s*     -> Optional unit (ignored).
     * [xX*]\s*           -> Separator (x, X, or *).
     * (\d+)\s*           -> Group 3: Sets.
     * [xX*]\s*           -> Separator.
     * (\d+)              -> Group 4: Reps.
     * \s*(?:[xX]|\(M\))? -> Optional 'X' or '(M)' for maintain.
     */
    private val EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+[-–—]\s+(bw|[\d\.]+)(?:kg|lbs)?\s*[xX*]\s*(\d+)\s*[xX*]\s*(\d+).*$""",
        option = RegexOption.IGNORE_CASE
    )

    // Updated Keywords to capture common workout splits
    private val SESSION_KEYWORDS =
        listOf(
            "PUSH",
            "PULL",
            "LOWER",
            "UPPER",
            "LEGS",
            "FULL BODY",
            "ARMS",
            "CARDIO",
            "BACK",
            "CHEST",
            "SHOULDERS"
        )

    fun parseLegacyNote(rawText: String): ParseResult {
        val lines = rawText.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) throw IllegalArgumentException("Empty text provided")

        // 1. First line is Client Name
        val clientName = lines.first().trim().toTitleCase()

        val sessions = mutableListOf<Session>()
        var currentSessionName = "Uncategorized"
        var currentExercises = mutableListOf<Exercise>()

        // 2. Iterate
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            val lineUpper = line.uppercase()

            // Check if line is a Header
            // Logic: Starts with a keyword OR ends with colon (e.g., "Monday Workout:")
            // AND does not contain the exercise separator " - "
            val isHeader = (SESSION_KEYWORDS.any { lineUpper.startsWith(it) } || line.endsWith(":"))
                    && !line.contains("-")

            if (isHeader) {
                // Save previous session if valid
                if (currentExercises.isNotEmpty()) {
                    sessions.add(
                        Session(
                            clientId = UUID.randomUUID().toString(), // Dummy ID
                            name = currentSessionName,
                            exercises = currentExercises.toList()
                        )
                    )
                }
                // Start new session
                currentSessionName = line.removeSuffix(":").toTitleCase()
                currentExercises = mutableListOf()
            } else {
                // Try to parse
                val exercise = parseExerciseLine(line)
                if (exercise != null) {
                    currentExercises.add(exercise)
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

        return ParseResult(clientName, sessions)
    }

    private fun parseExerciseLine(line: String): Exercise? {
        val match = EXERCISE_REGEX.find(line) ?: return null
        val (name, weightStr, setsStr, repsStr) = match.destructured

        // Check for maintain flag roughly in the whole line
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

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
