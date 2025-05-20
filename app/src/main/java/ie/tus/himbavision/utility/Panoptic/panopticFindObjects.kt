package ie.tus.himbavision.utility.Panoptic

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import ie.tus.himbavision.dataclasses.DetectionResult
import ie.tus.himbavision.localsecurestorage.SegmentationKeyStorageHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

fun detectObjects(
    focalLengthPx: Double,
    objectName: String,
    bitmap: Bitmap,
    context: Context,
    callback: (List<String>?) -> Unit
) {

    // Retrieve the segmentation key from local storage
    val segmentationKeyStorageHelper = SegmentationKeyStorageHelper(context)
    val segmentationKey = segmentationKeyStorageHelper.getSegmentationKey()

    // Initialize OkHttpClient
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    // Resize the bitmap to reduce image size (optional)
    val resizedBitmap = resizeBitmap(bitmap, 800, 800) // Adjust dimensions as needed

    // Convert Bitmap to ByteArray (PNG format)
    val byteArrayOutputStream = ByteArrayOutputStream()
    resizedBitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream) // Compress to reduce size
    val imageBytes = byteArrayOutputStream.toByteArray()

    // Create RequestBody for the image
    val MEDIA_TYPE_PNG = "image/png".toMediaTypeOrNull()
    val imageRequestBody = imageBytes.toRequestBody(MEDIA_TYPE_PNG)

    // Build the multipart form
    val multipartBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("image", "image.png", imageRequestBody)
        .addFormDataPart("focal_length_px", focalLengthPx.toString())
        .addFormDataPart("object_name", objectName)
        .build()

    // Build the request
    val request = Request.Builder()
        //https://number.ngrok-free.app or for premium https://number.ngrok.app
        .url("https://$segmentationKey.ngrok.app/detect")
        .addHeader("ngrok-skip-browser-warning", "true")
        .post(multipartBody)
        .build()

    // Make the asynchronous request
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("DetectObjects", "Request Failed: ${e.message}")
            callback(null) // Return null on failure
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!it.isSuccessful) {
                    Log.e("DetectObjects", "Unexpected code $response")
                    callback(null)
                } else {
                    val responseBody = it.body?.string()
                    if (responseBody != null) {
                        // Parse JSON using Gson
                        val gson = Gson()
                        val detectionResult = gson.fromJson(responseBody, DetectionResult::class.java)

                        // Decide which results to return
                        val results = detectionResult.object_results ?: detectionResult.all_results

                        callback(results) // Pass the list of detection results
                    } else {
                        callback(null)
                    }
                }
            }
        }
    })
}

// Helper function to resize the bitmap
fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val ratioBitmap = bitmap.width.toFloat() / bitmap.height.toFloat()
    val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

    var finalWidth = maxWidth
    var finalHeight = maxHeight
    if (ratioMax > ratioBitmap) {
        finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
    } else {
        finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
}