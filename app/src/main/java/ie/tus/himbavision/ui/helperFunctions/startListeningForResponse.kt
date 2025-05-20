package ie.tus.himbavision.ui.helperFunctions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale


var continueListening = true

fun startListeningForResponse(
    context: Context,
    speechRecognizer: SpeechRecognizer,
    speechIntent: Intent,
    tts: TextToSpeech?,
    navigate: (String) -> Unit
) {
    Log.d("HimbaVision", "startListeningForResponse: Starting speech recognition")
    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("HimbaVision", "RecognitionListener onReadyForSpeech")
        }
        override fun onBeginningOfSpeech() {
            Log.d("HimbaVision", "RecognitionListener onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {
            Log.d("HimbaVision", "RecognitionListener onRmsChanged: $rmsdB")
        }
        override fun onBufferReceived(buffer: ByteArray?) {
            Log.d("HimbaVision", "RecognitionListener onBufferReceived")
        }
        override fun onEndOfSpeech() {
            Log.d("HimbaVision", "RecognitionListener onEndOfSpeech")
        }
        override fun onError(error: Int) {
            Log.d("HimbaVision", "RecognitionListener onError: $error")
            if (error == SpeechRecognizer.ERROR_NO_MATCH && continueListening) {
                Log.d("HimbaVision", "RecognitionListener onError: No match found")
                tts?.speak("Sorry, I didn't catch that. Please try again.", TextToSpeech.QUEUE_FLUSH, null, "NO_MATCH")
                // Relaunch the speech recognizer
                Handler(Looper.getMainLooper()).post {
                    Log.d("HimbaVision", "Relaunching SpeechRecognizer")
                    val newSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    startListeningForResponse(context, newSpeechRecognizer, speechIntent, tts, navigate)
                }
            }
        }
        override fun onResults(results: Bundle?) {
            Log.d("HimbaVision", "RecognitionListener onResults")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.let {
                val response = it[0].lowercase(Locale.ROOT)
                Log.d("HimbaVision", "RecognitionListener onResults: Response received: $response")
                val yesKeywords = listOf("yes", "yeah", "hmm")
                val noKeywords = listOf("no", "nah", "hmm hmm")

                when {
                    yesKeywords.any { keyword -> keyword in response } -> {
                        Log.d("HimbaVision", "RecognitionListener onResults: Yes response")
                        // Relaunch the welcome screen
                        navigate("welcome_screen")
                    }
                    noKeywords.any { keyword -> keyword in response } -> {
                        Log.d("HimbaVision", "RecognitionListener onResults: No response")
                        // Handle unrecognized response
                        tts?.speak("Reverting back to manual mode, You're on your own now.", TextToSpeech.QUEUE_FLUSH, null, "UNRECOGNIZED_RESPONSE")
                        // Stop the speech recognizer and do not relaunch it
                        continueListening = false
                        speechRecognizer.stopListening()
                        speechRecognizer.destroy()
                    }
                    else -> {
                        Log.d("HimbaVision", "RecognitionListener onResults: Unrecognized response")
                        // Handle unrecognized response
                        tts?.speak("Sorry, I didn't understand that. Please try again.", TextToSpeech.QUEUE_FLUSH, null, "UNRECOGNIZED_RESPONSE")
                    }
                }
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            Log.d("HimbaVision", "RecognitionListener onPartialResults")
        }
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("HimbaVision", "RecognitionListener onEvent: $eventType")
        }
    })
    speechRecognizer.startListening(speechIntent)
}

