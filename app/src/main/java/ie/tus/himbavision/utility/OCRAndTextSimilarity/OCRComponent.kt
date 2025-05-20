package ie.tus.himbavision.utility.OCRAndTextSimilarity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import ie.tus.himbavision.utility.Network.isOnline

/**
 * Function to process a compressed JPEG image and extract text using MLs Kit online or on-device OCR.
 * @param jpegData ByteArray containing the compressed JPEG image data.
 * @param onTextRecognized Callback to return the recognized text or handle errors.
 */

fun processCompressedJPEG(jpegData: ByteArray, context: Context,  onTextRecognized: (String?, Exception?) -> Unit) {
    try {
        // Decode the JPEG byte array into a Bitmap
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

        // Create an InputImage for ML Kit
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val recognizer: TextRecognizer

        if (isOnline(context)) {
            // Initialize the cloud-based text recognizer with higher accuracy
            recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
        }else{
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }


        // Process the image and extract text
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                // Combine all text blocks into a single string
                val sortedTextBlocks = visionText.textBlocks.sortedWith(compareBy({ it.boundingBox?.top }, { it.boundingBox?.left }))
                val recognizedText = sortedTextBlocks.joinToString("\n") { it.text }
                onTextRecognized(recognizedText, null) // Return recognized text
            }
            .addOnFailureListener { e ->
                onTextRecognized(null, e) // Return the error
            }
    } catch (e: Exception) {
        onTextRecognized(null, e) // Handle decoding or other unexpected errors
    }
}
