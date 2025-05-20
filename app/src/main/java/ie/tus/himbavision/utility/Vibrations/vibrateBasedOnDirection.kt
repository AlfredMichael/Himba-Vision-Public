package ie.tus.himbavision.utility.Vibrations

import android.content.Context
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Build
import android.annotation.SuppressLint

//long[] pattern = {0, 500, 200, 500, 200, 500};
@SuppressLint("NewApi")
fun vibrateBasedOnDirection(context: Context, direction: String) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        val pattern = when {
            direction.contains("Move left", ignoreCase = true) -> longArrayOf(0, 100, 50, 100)
            direction.contains("Move right", ignoreCase = true) -> longArrayOf(0, 100, 100, 100, 100, 100)
            direction.contains("Slow down, no safe path found", ignoreCase = true) -> longArrayOf(0, 300, 200, 300)
            direction.contains("Cannot find path, be careful", ignoreCase = true) -> longArrayOf(0, 500, 200, 500, 200, 500)
            else -> null //No vibration for continue ahead
        }

        pattern?.let{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        }


    }
}
