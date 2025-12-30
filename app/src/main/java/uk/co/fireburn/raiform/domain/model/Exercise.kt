package uk.co.fireburn.raiform.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val weight: Double = 0.0,

    // Force Firestore to use "isBodyweight" instead of shortening it to "bodyweight"
    @get:PropertyName("isBodyweight")
    val isBodyweight: Boolean = false,

    val sets: Int = 0,
    val reps: Int = 0,

    val maintainWeight: Boolean = false,

    // Force Firestore to use "isDone" instead of shortening it to "done"
    @get:PropertyName("isDone")
    val isDone: Boolean = false
) {
    // Tells Firestore: "Do not save this to the database, it's just a calculation"
    @get:Exclude
    val volume: Double
        get() = if (isBodyweight) 0.0 else weight * sets * reps
}
