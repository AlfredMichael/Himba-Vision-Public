package ie.tus.himbavision.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ie.tus.himbavision.jnibridge.FramesDirect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.intellij.lang.annotations.JdkConstants.HorizontalAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


@Composable
fun TestFrames() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isCameraOpen = remember { mutableStateOf(false) }

    // State to hold the captured image
    val capturedImage = remember { mutableStateOf<Bitmap?>(null) }

    // Open the camera when the composable is first composed
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            FramesDirect.openCamera(1)
            isCameraOpen.value = true
        }
    }

    Column(modifier = Modifier
        .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    )
        {
        Button(onClick = {
            coroutineScope.launch {
                if (!isCameraOpen.value) {
                    // Optional: Handle the case where the camera is not ready yet
                    Log.e("TestFrames", "Camera is not yet initialized")
                    return@launch
                }

                // Retrieve the latest frame (JPEG-compressed byte array)
                val frameBytes = FramesDirect.getLatestFrame()

                if (frameBytes != null && frameBytes.isNotEmpty()) {
                    Log.d("TestFrames", "Byte array size: ${frameBytes.size}")

                    // Decode the byte array to a Bitmap
                    val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                    if (bitmap != null) {
                        capturedImage.value = bitmap // Update state to display the image
                    } else {
                        Log.e("TestFrames", "Failed to decode Bitmap from byte array")
                    }

                    // Save the JPEG bytes to a temporary file
                    val tempFile = File.createTempFile("frame", ".jpg", context.cacheDir)
                    FileOutputStream(tempFile).use { it.write(frameBytes) }

                    // Send the frame to the API
                    sendFrameToApi(tempFile)

                    // Clean up the temporary file if needed
                    tempFile.deleteOnExit()
                } else {
                    Log.e("TestFrames", "Frame bytes are null or empty")
                }
            }
        }) {
            Text(text = "Capture Frame")
        }

        // Display the captured image if available
        capturedImage.value?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}


suspend fun sendFrameToApi(file: File) {
    withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, RequestBody.create("image/jpeg".toMediaTypeOrNull(), file))
            .build()

        val request = Request.Builder()
            .url("http://192.168.0.252:5000/upload/")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        println(response.body?.string())
    }
}

//http://192.168.0.252:5000