package ie.tus.himbavision.dataclasses

data class User(
    val fullname: String,
    val email: String,
    val password: String,
    val emergencyEmail: String,
    val voiceControl: Boolean,
    val lastKnownLocation: String = "Unknown"
) {
    // No-argument constructor for Firebase to serialize and deserialize our User data class
    constructor() : this("", "", "", "", false, "Unknown")
}
