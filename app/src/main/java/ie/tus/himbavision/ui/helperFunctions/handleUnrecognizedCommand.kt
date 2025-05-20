package ie.tus.himbavision.ui.helperFunctions


import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log


fun handleUnrecognizedCommand(
    context: Context,
    command: String,
    navigate: (String) -> Unit,
    speechIntent: Intent,
    tts: TextToSpeech?
) {
    Log.d("HimbaVision", "handleUnrecognizedCommand: Command received: $command")
    tts?.speak("We heard $command. This is not a command, so no action will be performed. Would you like to try again?", TextToSpeech.QUEUE_FLUSH, null, "UNRECOGNIZED_COMMAND")

    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d("HimbaVision", "TTS onStart: $utteranceId")
        }
        override fun onDone(utteranceId: String?) {
            Log.d("HimbaVision", "TTS onDone: $utteranceId")
            // Ensure the SpeechRecognizer is created and used on the main thread
            Handler(Looper.getMainLooper()).post {
                Log.d("HimbaVision", "Creating SpeechRecognizer")
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                startListeningForResponse(context, speechRecognizer, speechIntent, tts, navigate)
            }
        }
        override fun onError(utteranceId: String?) {
            Log.d("HimbaVision", "TTS onError: $utteranceId")
        }
    })
}







