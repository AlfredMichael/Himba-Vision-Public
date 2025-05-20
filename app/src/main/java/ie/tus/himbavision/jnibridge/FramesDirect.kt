package ie.tus.himbavision.jnibridge


class FramesDirect {
    companion object {
        // Load the native library
        init {
            System.loadLibrary("himbavision")
        }

        // Declare the native functions
        @JvmStatic
        external fun openCamera(cameraFacing: Int): Int

        @JvmStatic
        external fun getLatestFrame(): ByteArray
    }
}

