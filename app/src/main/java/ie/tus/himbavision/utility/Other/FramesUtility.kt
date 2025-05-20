package ie.tus.himbavision.utility.Other

import android.graphics.Bitmap
import android.graphics.Matrix


// Rotate the frame to the right (90 degrees clockwise)
fun rotateFrameRight(frame: Bitmap): Bitmap {
    // Create a Matrix object to handle transformations on the Bitmap
    val matrix = Matrix()

    // Rotate the matrix by 90 degrees clockwise
    matrix.postRotate(90f)

    // Create and return a new Bitmap that is the result of applying the rotation
    // Parameters:
    // - frame: The source Bitmap
    // - 0, 0: The x and y coordinates of the starting point in the source Bitmap
    // - frame.width, frame.height: The width and height of the source Bitmap
    // - matrix: The transformation matrix to apply
    // - true: Whether to apply anti-aliasing for smoother edges
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
}

