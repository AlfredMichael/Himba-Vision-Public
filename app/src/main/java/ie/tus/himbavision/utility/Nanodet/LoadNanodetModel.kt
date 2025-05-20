package ie.tus.himbavision.utility.Nanodet

import android.content.Context
import android.util.Log
import ie.tus.himbavision.jnibridge.HimbaJNIBridge


// Function to load a NanoDet model using the HimbaJNIBridge class
fun LoadNanodetModel(nanodetncnn: HimbaJNIBridge, cpuGpuIndex: Int, context: Context) {
    // Attempt to load the model from the app's assets using the specified CPU/GPU index
    val success = nanodetncnn.loadModel(context.assets, cpuGpuIndex)

    // Check if the model loading was successful
    if (!success) {
        // Log an error message if the model failed to load
        Log.d("HomeComposable", "Failed to load model")
    } else {
        // Log a success message if the model was successfully loaded
        Log.d("HomeComposable", "Model was loaded loaded loaded loadedH")
    }
}
