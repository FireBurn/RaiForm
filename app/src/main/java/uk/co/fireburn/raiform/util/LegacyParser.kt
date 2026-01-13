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

    private val EXERCISE_REGEX = Regex(
        pattern = """^(.*?)\s+[-–—]\s+(bw|[\d\.]+)(?:kg|lbs)?\s*[xX*]\s*(\d+)\s*[xX*]\s*(\d+).*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val SESSION_KEYWORDS =
        listOf(
            "PUSH", "PULL", "LOWER", "UPPER", "LEGS", "FULL BODY",
            "ARMS", "CARDIO", "BACK", "CHEST", "SHOULDERS",
            "BICEPS", "TRICEPS", "ABS", "CORE", "GLUTES", "CALVES"
        )

    fun parseLegacyNote(rawText: String, combineSessions: Boolean = false): ParseResult {
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

            val isKeywordMatch = SESSION_KEYWORDS.any { lineUpper == it }
            val isExplicitHeader = line.endsWith(":")
            val isHeader = (isKeywordMatch || isExplicitHeader) && !line.contains("-")

            if (isHeader) {
                // Save previous session if valid
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
            } else {
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

        // --- COMBINE LOGIC ---
        if (combineSessions && sessions.isNotEmpty()) {
            val combinedExercises = mutableListOf<Exercise>()

            sessions.forEach { session ->
                // Insert a "Header" exercise to act as a visual separator
                // We use 0 sets/0 reps as the marker for a header in the UI
                val headerName = session.name.uppercase()

                // Avoid adding header if it's just "Uncategorized" and effectively the only one,
                // but usually users provide headers.
                combinedExercises.add(
                    Exercise(
                        id = UUID.randomUUID().toString(),
                        name = headerName,
                        weight = 0.0,
                        isBodyweight = true, // Irrelevant for header
                        sets = 0, // MARKER
                        reps = 0, // MARKER
                        maintainWeight = false,
                        isDone = false
                    )
                )
                combinedExercises.addAll(session.exercises)
            }

            val combinedSession = Session(
                clientId = UUID.randomUUID().toString(),
                name = "Full Routine",
                exercises = combinedExercises
            )

            return ParseResult(clientName, listOf(combinedSession))
        }

        return ParseResult(clientName, sessions)
    }

    private fun parseExerciseLine(line: String): Exercise? {
        val match = EXERCISE_REGEX.find(line) ?: return null
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

    private fun String.toTitleCase(): String {
        return this.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
