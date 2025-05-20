package ie.tus.himbavision.utility.DirectionsAPI

import android.content.Context
import android.util.Log
import ie.tus.himbavision.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.text.Html

fun fetchOutdoorDirections(
    context: Context,
    originLat: Double,
    originLng: Double,
    destination: String,
    callback: (List<String>) -> Unit
) {
    val apiKey = context.getString(R.string.google_maps_api_key)
    val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=$originLat,$originLng" +
            "&destination=$destination" +
            "&mode=transit&key=$apiKey"

    Log.d("MapScreen", "Generated URL: $url")

    // Use OkHttp for making the HTTP request
    val client = OkHttpClient()

    val request = Request.Builder()
        .url(url)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("DirectionsAPI", "Error fetching directions: ${e.message}")
            callback(emptyList()) // Return an empty list on failure
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                response.body?.string()?.let { jsonResponse ->
                    Log.d("DirectionsAPI", "Response: $jsonResponse")

                    // Parse JSON and extract instructions
                    val instructions = parseDirectionsResponse(jsonResponse)
                    callback(instructions) // Return the instructions
                } ?: callback(emptyList())
            } else {
                Log.e("DirectionsAPI", "API call failed: ${response.message}")
                callback(emptyList())
            }
        }
    })
}

// Helper function to parse the JSON response and extract relevant directions
private fun parseDirectionsResponse(jsonResponse: String): List<String> {
    val instructions = mutableListOf<String>()

    try {
        val jsonObject = JSONObject(jsonResponse)

        // Check if the response status is OK
        if (jsonObject.getString("status") != "OK") {
            Log.e("DirectionsAPI", "Response status not OK: ${jsonObject.getString("status")}")
            return instructions
        }

        // Access the first route
        val routes = jsonObject.getJSONArray("routes")
        if (routes.length() == 0) {
            Log.e("DirectionsAPI", "No routes found")
            return instructions
        }
        val route = routes.getJSONObject(0)

        // Access the first leg of the route
        val legs = route.getJSONArray("legs")
        if (legs.length() == 0) {
            Log.e("DirectionsAPI", "No legs found")
            return instructions
        }
        val leg = legs.getJSONObject(0)

        // Function to process each step and its nested steps
        fun processStep(step: JSONObject) {
            if (step.has("html_instructions")) {
                val rawHtmlInstructions = step.getString("html_instructions")
                val cleanedInstructions = cleanHtmlTagsUsingHtml(rawHtmlInstructions).replace("\n", " ")
                val duration = step.getJSONObject("duration").getString("text")
                val startLocation = step.getJSONObject("start_location")
                val endLocation = step.getJSONObject("end_location")
                val startLat = startLocation.getDouble("lat")
                val startLng = startLocation.getDouble("lng")
                val endLat = endLocation.getDouble("lat")
                val endLng = endLocation.getDouble("lng")

                val travelMode = step.getString("travel_mode")
                when (travelMode) {
                    "WALKING" -> {
                        instructions.add("Walk: $cleanedInstructions, Duration: $duration, Start: ($startLat, $startLng), End: ($endLat, $endLng)")
                    }
                    "TRANSIT" -> {
                        val transitDetails = step.getJSONObject("transit_details")
                        val line = transitDetails.getJSONObject("line")
                        val agenciesArray = line.getJSONArray("agencies")
                        val agency = if (agenciesArray.length() > 0) {
                            agenciesArray.getJSONObject(0).getString("name")
                        } else {
                            "Unknown Agency"
                        }
                        val busNumber = line.getString("short_name")
                        val departureStop = transitDetails.getJSONObject("departure_stop")
                        val departureStopName = departureStop.getString("name")
                        val departureLocation = departureStop.getJSONObject("location")
                        val departureLat = departureLocation.getDouble("lat")
                        val departureLng = departureLocation.getDouble("lng")
                        val arrivalStop = transitDetails.getJSONObject("arrival_stop")
                        val arrivalStopName = arrivalStop.getString("name")
                        val arrivalLocation = arrivalStop.getJSONObject("location")
                        val arrivalLat = arrivalLocation.getDouble("lat")
                        val arrivalLng = arrivalLocation.getDouble("lng")
                        val departureTime = transitDetails.getJSONObject("departure_time").getString("text")

                        val transitInfo = "Bus: $agency $busNumber, Departure Stop: $departureStopName (Lat: $departureLat, Lng: $departureLng), Arrival Stop: $arrivalStopName (Lat: $arrivalLat, Lng: $arrivalLng), Departure Time: $departureTime, Duration: $duration"

                        instructions.add(transitInfo)
                    }
                    else -> {
                        instructions.add("Other Travel Mode: $cleanedInstructions, Duration: $duration, Start: ($startLat, $startLng), End: ($endLat, $endLng)")
                    }
                }

                // Process nested steps if any
                if (step.has("steps")) {
                    val nestedSteps = step.getJSONArray("steps")
                    for (j in 0 until nestedSteps.length()) {
                        processStep(nestedSteps.getJSONObject(j))
                    }
                }
            }
        }

        // Iterate through each step in the leg
        val steps = leg.getJSONArray("steps")
        for (i in 0 until steps.length()) {
            processStep(steps.getJSONObject(i))
        }
    } catch (e: JSONException) {
        Log.e("DirectionsAPI", "Error parsing JSON: ${e.message}")
        Log.e("DirectionsAPI", "Stack trace: ${Log.getStackTraceString(e)}")
        Log.e("DirectionsAPI", "JSON response: $jsonResponse")
    }

    return instructions
}


// Helper Function to clean HTML tags using Html.fromHtml
private fun cleanHtmlTagsUsingHtml(html: String): String {
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}

