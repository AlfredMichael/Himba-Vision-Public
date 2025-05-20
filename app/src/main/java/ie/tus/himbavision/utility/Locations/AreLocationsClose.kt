package ie.tus.himbavision.utility.Locations

import kotlin.math.*

fun areLocationsClose(
    currentLat: Double,
    currentLng: Double,
    targetLat: Double,
    targetLng: Double,
    thresholdMeters: Double = 6.0 //Threshold distance in meters
): Boolean {
    val earthRadius = 6371000.0 // Radius of the Earth in meters

    val dLat = Math.toRadians(targetLat - currentLat)
    val dLng = Math.toRadians(targetLng - currentLng)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(currentLat)) * cos(Math.toRadians(targetLat)) *
            sin(dLng / 2) * sin(dLng / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    val distance = earthRadius * c

    return distance <= thresholdMeters
}
