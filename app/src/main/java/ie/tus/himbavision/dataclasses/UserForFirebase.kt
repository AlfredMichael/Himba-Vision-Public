package ie.tus.himbavision.dataclasses

data class UserForFirebase(
    val fullname: String,
    val email: String,
    val emergencyEmail: String,
    val voiceControl: Boolean,
    val lastKnownLocation: String = "Unknown"
) {
    // No-argument constructor for Firebase to serialize and deserialize our UserForFirebase data class
    constructor() : this("", "", "", false, "Unknown")
}

