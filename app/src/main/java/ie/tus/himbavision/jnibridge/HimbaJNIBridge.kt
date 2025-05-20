package ie.tus.himbavision.jnibridge

import android.content.res.AssetManager
import android.view.Surface

class HimbaJNIBridge {
    external fun loadModel(assetManager: AssetManager, cpuGpu: Int): Boolean
    external fun openCamera(facing: Int): Boolean
    external fun closeCamera(): Boolean
    external fun setOutputWindow(surface: Surface): Boolean


    external fun getAllDetections(): Array<String>
    external fun getCenterDetections(): Array<String>
    external fun getMinNavDirections(): Array<String>
    external fun getMaxNavDirections(): Array<String>
    external fun getFps(): Float
    external fun getAllFindDetections(objectName: String): Array<String>
    external fun getLatestFrame(): ByteArray
    external fun getFocalLengthPx(): Float


    companion object {
        init {
            System.loadLibrary("himbavision")
        }
    }

}