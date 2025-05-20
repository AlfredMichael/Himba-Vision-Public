package ie.tus.himbavision.ui.utilitycomposables

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ie.tus.himbavision.jnibridge.HimbaJNIBridge
import ie.tus.himbavision.utility.Nanodet.LoadNanodetModel


// Composable function that sets up the Camera Preview
@Composable
fun CameraPreview(
    nanodetncnn: HimbaJNIBridge, // Interface to the NanoDet model (camera and object detection)
    facing: Int, // Specifies whether to use the front or back camera (0 for front, 1 for back)
    cpuGpuIndex: Int, // Index to choose whether to use CPU or GPU for model processing
    context: Context // Context for accessing system resources (e.g., to load assets)
) {
    // LifecycleOwner, used to manage the lifecycle of the camera preview
    val lifecycleOwner = LocalLifecycleOwner.current
    // Variables to store the surface texture and dimensions
    var currentSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var textureWidth by remember { mutableStateOf(0) }
    var textureHeight by remember { mutableStateOf(0) }
    // Variable to track how many times the surface texture is available
    var connectionCount by remember { mutableStateOf(0) }

    // Disposing effect when the lifecycle changes
    DisposableEffect(key1 = lifecycleOwner) {
        // Observer for lifecycle events (pause, resume, create, destroy)
        val observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // Close the camera when the app is paused
                nanodetncnn.closeCamera()
                Log.d("CameraPreview", "Camera closed onPause")
            }

            override fun onResume(owner: LifecycleOwner) {
                // Reuse the surface texture if it's available, otherwise log that it's null
                currentSurfaceTexture?.let {
                    Log.d("CameraPreview", "Reusing SurfaceTexture onResume")
                    // If SurfaceTexture exists, set it up for use with the camera and model
                    NewhandleSurfaceTextureAvailable(it, textureWidth, textureHeight, nanodetncnn, facing, cpuGpuIndex, context)
                } ?: run {
                    Log.d("CameraPreview", "SurfaceTexture is null onResume")
                }
            }

            override fun onCreate(owner: LifecycleOwner) {
                // Load the NanoDet model when the composable is created
                LoadNanodetModel(nanodetncnn, cpuGpuIndex, context)
                Log.d("CameraPreview", "Model loaded onCreate")
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Close the camera when the composable is destroyed
                nanodetncnn.closeCamera()
                Log.d("CameraPreview", "Camera closed onDestroy")
            }
        }

        // Add the lifecycle observer to the lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(observer)

        // Cleanup when composable is disposed
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Do not close the camera here
            Log.d("CameraPreview", "CameraPreview onDispose called")
        }
    }

    // TextureView for displaying the camera preview
    AndroidView(
        modifier = Modifier
            .fillMaxWidth() // Makes the TextureView fill the screen width
            .zIndex(1f), // Controls the layering of the view
        factory = { context ->
            TextureView(context).apply {
                // Listener for when the surface texture changes
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // SurfaceTexture is now available, store it and set up the camera
                        currentSurfaceTexture = st
                        textureWidth = width
                        textureHeight = height
                        connectionCount++ // Increment the connection count for debugging
                        Log.d("CameraPreview", "SurfaceTexture available, setting up camera. Connection count: $connectionCount")
                        // Call the function to handle SurfaceTexture and setup camera
                        handleSurfaceTextureAvailable(st, width, height, nanodetncnn, facing, cpuGpuIndex, context)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // Handle size changes if needed
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Handle updates to the surface texture if needed
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        // Clean up when the surface texture is destroyed
                        currentSurfaceTexture = null
                        Log.d("CameraPreview", "SurfaceTexture destroyed")
                        return handleSurfaceTextureDestroyed(surfaceTexture, nanodetncnn)
                    }
                }
            }
        }
    )
}

// Helper function to handle the setup when SurfaceTexture is available
fun handleSurfaceTextureAvailable(
    surfaceTexture: SurfaceTexture,
    width: Int,
    height: Int,
    nanodetncnn: HimbaJNIBridge,
    facing: Int,
    cpuGpuIndex: Int,
    context: Context
) {
    // Create a Surface object from the SurfaceTexture
    val surface = Surface(surfaceTexture)
    // Set the Surface as the output for the camera feed
    nanodetncnn.setOutputWindow(surface)
    Log.d("CameraPreview", "SurfaceTexture available, setting up camera")

    // Load the NanoDet model (again, in case it wasn't loaded already)
    LoadNanodetModel(nanodetncnn, cpuGpuIndex, context)

    // Open the camera after setting the output window
    nanodetncnn.openCamera(facing)
    Log.d("CameraPreview", "Camera opened")
}

// A simplified version of handleSurfaceTextureAvailable (used when resuming the camera)
fun NewhandleSurfaceTextureAvailable(
    surfaceTexture: SurfaceTexture,
    width: Int,
    height: Int,
    nanodetncnn: HimbaJNIBridge,
    facing: Int,
    cpuGpuIndex: Int,
    context: Context
) {
    // Load the NanoDet model
    LoadNanodetModel(nanodetncnn, cpuGpuIndex, context)

    // Open the camera again after loading the model
    nanodetncnn.openCamera(facing)
    Log.d("CameraPreview", "Camera opened")
}

// Placeholder function to handle size changes for the surface texture (if needed)
fun handleSurfaceTextureSizeChanged(
    surfaceTexture: SurfaceTexture,
    width: Int,
    height: Int
) {
    // Implement handling of size changes if necessary
}

// Placeholder function to handle texture updates (if needed)
fun handleSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    // Update or process the texture if necessary
}

// Function to handle the cleanup when SurfaceTexture is destroyed
fun handleSurfaceTextureDestroyed(
    surfaceTexture: SurfaceTexture,
    nanodetncnn: HimbaJNIBridge
): Boolean {
    // Close the camera and release the SurfaceTexture
    nanodetncnn.closeCamera()
    surfaceTexture.release()
    Log.d("CameraPreview", "SurfaceTexture destroyed, camera closed")
    return true
}
