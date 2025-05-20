package ie.tus.himbavision.utility.Panoptic


import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ie.tus.himbavision.dataclasses.NavigationInstructions
import ie.tus.himbavision.localsecurestorage.SegmentationKeyStorageHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

fun navigateDetect(
    focalLengthPx: Double,
    bitmap: Bitmap,
    context: Context,
    callback: (NavigationInstructions?) -> Unit
) {
    Log.d("navigateDetectP", focalLengthPx.toString())

    // Retrieve the segmentation key from local storage
    val segmentationKeyStorageHelper = SegmentationKeyStorageHelper(context)
    val segmentationKey = segmentationKeyStorageHelper.getSegmentationKey()

    // Initialize OkHttpClient
    val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    // Resize the bitmap to reduce image size (optional)
    val resizedBitmap = resizeBitmap(bitmap, 800, 800)

    // Convert Bitmap to ByteArray (PNG format)
    val byteArrayOutputStream = ByteArrayOutputStream()
    resizedBitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream)
    val imageBytes = byteArrayOutputStream.toByteArray()

    // Create RequestBody for the image
    val MEDIA_TYPE_PNG = "image/png".toMediaTypeOrNull()
    val imageRequestBody = imageBytes.toRequestBody(MEDIA_TYPE_PNG)

    // Build the multipart form
    val multipartBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("image", "image.png", imageRequestBody)
        .addFormDataPart("focal_length_px", focalLengthPx.toString())
        .build()

    // Build the request
    val request = Request.Builder()
        //https://number.ngrok-free.app or for premium https://number.ngrok.app
        .url("https://$segmentationKey.ngrok.app/navigate")
        .addHeader("ngrok-skip-browser-warning", "true")
        .post(multipartBody)
        .build()

    // Make the asynchronous request
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Navigate", "Request Failed: ${e.message}")
            callback(null)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!it.isSuccessful) {
                    Log.e("Navigate", "Unexpected code $response")
                    callback(null)
                } else {
                    val responseBody = it.body?.string()
                    Log.d("Navigate", "Response Body: $responseBody") // raw response
                    if (responseBody != null) {
                        try {
                            // Parse JSON using Gson
                            val gson = Gson()
                            val navigationInstructions = gson.fromJson(responseBody, NavigationInstructions::class.java)

                            callback(navigationInstructions)
                        } catch (e: JsonSyntaxException) {
                            Log.e("Navigate", "JSON Parsing Error: ${e.message}")
                            callback(null)
                        }
                    } else {
                        callback(null)
                    }
                }
            }
        }
    })
}
