package uk.co.fireburn.raiform.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.UUID

@IgnoreExtraProperties // Silences warnings for 'volume' existing in DB but not in constructor
data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val weight: Double = 0.0,

    // Maps the DB field "bodyweight" to this property
    @PropertyName("bodyweight")
    val isBodyweight: Boolean = false,

    val sets: Int = 0,
    val reps: Int = 0,

    val maintainWeight: Boolean = false,

    // Maps the DB field "done" to this property
    @PropertyName("done")
    val isDone: Boolean = false
) {
    // Tells Firestore: "Do not save this to the database, it's just a calculation"
    @get:Exclude
    val volume: Double
        get() = if (isBodyweight) 0.0 else weight * sets * reps
}
