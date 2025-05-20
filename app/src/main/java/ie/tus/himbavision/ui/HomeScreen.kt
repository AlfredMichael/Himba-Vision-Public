package ie.tus.himbavision.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.R
import ie.tus.himbavision.jnibridge.HimbaJNIBridge
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.ui.bottomappbar.CustomBottomAppBar
import ie.tus.himbavision.ui.dialogs.ObjectDetectionDialog
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.ui.theme.gothamFonts
import ie.tus.himbavision.ui.topappbar.CustomAppBar
import ie.tus.himbavision.ui.utilitycomposables.CameraPreview
import ie.tus.himbavision.utility.Locations.rememberLocationUpdates
import ie.tus.himbavision.utility.Network.ConnectivityReceiver
import ie.tus.himbavision.utility.Panoptic.detectObjects
import ie.tus.himbavision.utility.Panoptic.detectronObjectNames
import ie.tus.himbavision.utility.OCRAndTextSimilarity.jaccardSimilarity
import ie.tus.himbavision.utility.Nanodet.objectNames
import ie.tus.himbavision.utility.OCRAndTextSimilarity.processCompressedJPEG
import ie.tus.himbavision.utility.Other.rotateFrameRight
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlDetailsViewModel
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlViewModelFactory
import ie.tus.himbavision.viewmodel.UpdateLatLngViewModel
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModel
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.DisposableEffect
import ie.tus.himbavision.viewmodel.AuthViewModel
import ie.tus.himbavision.viewmodel.AuthViewModelFactory
import kotlinx.coroutines.Dispatchers
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedObject by rememberSaveable { mutableStateOf("") }
    var objectText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nanodetncnn = HimbaJNIBridge()
    val cpuGpuIndex by rememberSaveable { mutableStateOf(0) }
    val facing by rememberSaveable { mutableStateOf(1)  }

    val secureStorageHelper = SecureStorageHelper(context)
    val user = secureStorageHelper.getUser()
    val updateLatLngViewModel: UpdateLatLngViewModel = viewModel()
    val currentLocation by rememberLocationUpdates()


    val connectivityReceiver = remember { ConnectivityReceiver(context) }
    val isConnected by connectivityReceiver.isConnected.observeAsState("Disconnected")

    // State to hold the FPS value
    var nanodetFps by remember { mutableStateOf(0f) }

    var selectedOption by remember { mutableStateOf("Object Detection") }

    // Dynamically update the list of objects based on the selected option
    val objects = remember(selectedOption) {
        when (selectedOption) {
            "Object Detection" -> objectNames
            "Image Segmentation" -> detectronObjectNames
            else -> objectNames
        }
    }

    // Filtered list based on user input
    val filteredObjects = objects.filter { it.contains(selectedObject, ignoreCase = true) }

    val objectDetectionState = remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    // State to hold the captured image
    val capturedImage = remember { mutableStateOf<Bitmap?>(null) }

    // State to hold the recognized text
    val recognizedText = remember { mutableStateOf<String?>(null) }

    var focalLengthPx by remember { mutableStateOf(-1.0f) }

    var expandedModel by remember { mutableStateOf(false) }


    //Get Offline and Online voice control
    val getVoiceViewModel: RetrieveVoiceControlDetailsViewModel = viewModel(factory = RetrieveVoiceControlViewModelFactory(context))

    var isMicEnabled by rememberSaveable { mutableStateOf(false)}

    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))


    //----- VOICE CONTROL ---- START-------------------
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            //putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 50000) // 50 seconds
            //putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 50000) //50
        }
    }

    // ------END--------------


    // Observe the voice control state
    val voiceControlState by getVoiceViewModel.voiceControlState.observeAsState()

    LaunchedEffect(Unit) {
        user?.let {
            getVoiceViewModel.retrieveVoiceControl(it.email, context)
        }
    }

    // Update isMicEnabled based on the retrieved voice control state
    LaunchedEffect(voiceControlState) {
        voiceControlState?.let {
            isMicEnabled = it
        }
    }


        // Periodically fetch the FPS value
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            while (true) {
                nanodetFps = nanodetncnn.getFps()
                delay(500) // Adjust the delay as needed
            }
        }
    }

    LaunchedEffect(Unit) {
        while (focalLengthPx == -1.0f) {
            delay(1000) // Delay for 1 second
            focalLengthPx = nanodetncnn.getFocalLengthPx()
        }
        Log.d("FocalLengthConf", "Focal Length (px): $focalLengthPx")
    }

    // Update location every 10 seconds
    LaunchedEffect(currentLocation) {
        while (true) {
            delay(10000) // 10 seconds
            currentLocation?.let {
                val latLng = "${it.latitude}, ${it.longitude}"
                if (user != null) {
                    updateLatLngViewModel.updateLatLng(user.email, latLng, context)
                }
            }
        }
    }

    //------------Home Screen Main Functionality---------
    fun findObjectFunctionality(){
        selectedOption = if (isConnected == "disconnected") "Object Detection" else selectedOption

        var oCRText : String
        coroutineScope.launch(Dispatchers.IO) {
            // Retrieve the latest frame (JPEG-compressed byte array)
            val frameBytes = nanodetncnn.getLatestFrame()

            if (frameBytes != null && frameBytes.isNotEmpty()) {
                Log.d("TestFrames", "Byte array size: ${frameBytes.size}")

                // Decode the byte array to a Bitmap
                var bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                if (bitmap != null) {
                    // Rotate the frame to the right
                    bitmap = rotateFrameRight(bitmap)
                    capturedImage.value = bitmap // Update state to display the image
                    Log.d("HomeScreen", "Frame has been captured")

                    // Convert the rotated bitmap back to a byte array
                    val rotatedFrameBytes = ByteArrayOutputStream().apply {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, this)
                    }.toByteArray()

                    if (selectedOption == "Image Segmentation") {
                        processCompressedJPEG(rotatedFrameBytes, context = context) { text, error ->
                            if (error != null) {
                                Log.e("HomeScreen", "Text recognition failed: ${error.message}")
                            } else {
                                recognizedText.value = text
                                Log.d("HomeScreen", "Recognized text: $text")

                                if (objectText.isEmpty()) {
                                    // Call detectObjects function
                                    detectObjects(
                                        focalLengthPx = focalLengthPx.toDouble(),
                                        objectName = selectedObject,
                                        bitmap = bitmap,
                                        context = context,
                                    ) { results ->

                                        coroutineScope.launch(Dispatchers.Main){
                                            if (results != null && results.isNotEmpty()) {
                                                // Update the UI with detection results
                                                objectDetectionState.value = results.joinToString("\n")
                                                showDialog = true
                                                if(isMicEnabled){
                                                    speechRecognizer.stopListening()

                                                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                        override fun onStart(utteranceId: String?) {

                                                        }

                                                        override fun onDone(utteranceId: String?) {
                                                            if (utteranceId == "DETECTION_RESULTS") {
                                                                if (text != null && text.isNotEmpty()) {
                                                                    oCRText = "Reading text: " + text
                                                                    tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                                }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                                    // Restart the speech recognizrr so it listens after speaking
                                                                    speechRecognizer.startListening(speechIntent)
                                                                }
                                                            }
                                                        }

                                                        override fun onError(utteranceId: String?) {
                                                            speechRecognizer.startListening(speechIntent)
                                                        }
                                                    })

                                                    tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                                    objectDetectionState.value = ""
                                                    showDialog = false
                                                }

                                            } else {
                                                Log.e("HomeScreen", "Image Segmentation failed or no objects detected")
                                                objectDetectionState.value = "No objects detected"
                                                showDialog = true
                                                if(isMicEnabled){
                                                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                        override fun onStart(utteranceId: String?) {
                                                            speechRecognizer.stopListening()
                                                        }

                                                        override fun onDone(utteranceId: String?) {
                                                            if (utteranceId == "DETECTION_RESULTS") {
                                                                if (text != null && text.isNotEmpty()) {
                                                                    oCRText = "Reading text: " + text
                                                                    tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                                }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                                    // Restart the speech recognizrr so it listens after speaking
                                                                    speechRecognizer.startListening(speechIntent)
                                                                }
                                                            }
                                                        }

                                                        override fun onError(utteranceId: String?) {
                                                            speechRecognizer.startListening(speechIntent)
                                                        }
                                                    })
                                                    tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                                    objectDetectionState.value = ""
                                                    showDialog = false
                                                }

                                            }

                                        }

                                    }
                                }else{
                                    // Calculate the Jaccard similarity
                                    val similarity = jaccardSimilarity(objectText, text ?: "")
                                    Log.d("Similarity Score", similarity.toString())
                                    // If the text similarity is greater than 10%
                                    if (similarity > 0.10) {
                                        // Call detectObjects function
                                        detectObjects(
                                            focalLengthPx = focalLengthPx.toDouble(),
                                            objectName = selectedObject,
                                            bitmap = bitmap,
                                            context = context,
                                        ) { results ->
                                            if (results != null && results.isNotEmpty()) {
                                                // Update the UI with detection results
                                                objectDetectionState.value = results.joinToString("\n")
                                                showDialog = true
                                                if(isMicEnabled){
                                                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                        override fun onStart(utteranceId: String?) {
                                                            speechRecognizer.stopListening()
                                                        }

                                                        override fun onDone(utteranceId: String?) {
                                                            if (utteranceId == "DETECTION_RESULTS") {
                                                                oCRText = "Reading detected text: " + text
                                                                tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                            }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                                // Restart the speech recognizrr so it listens after speaking
                                                                speechRecognizer.startListening(speechIntent)
                                                            }
                                                        }

                                                        override fun onError(utteranceId: String?) {
                                                            speechRecognizer.startListening(speechIntent)
                                                        }
                                                    })

                                                    tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                                    objectDetectionState.value = ""
                                                    showDialog = false
                                                }

                                            } else {
                                                Log.e("HomeScreen", "Image Segmentation failed or no objects detected")
                                                objectDetectionState.value = "No objects detected"
                                                showDialog = true

                                                if(isMicEnabled){
                                                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                        override fun onStart(utteranceId: String?) {
                                                            speechRecognizer.stopListening()
                                                        }

                                                        override fun onDone(utteranceId: String?) {
                                                            if (utteranceId == "DETECTION_RESULTS") {
                                                                oCRText = "Reading detected text: " + text
                                                                tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                            }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                                // Restart the speech recognizrr so it listens after speaking
                                                                speechRecognizer.startListening(speechIntent)
                                                            }
                                                        }

                                                        override fun onError(utteranceId: String?) {
                                                            speechRecognizer.startListening(speechIntent)
                                                        }
                                                    })

                                                    tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                                    objectDetectionState.value = ""
                                                    showDialog = false
                                                }
                                            }
                                        }

                                    }else{
                                        Log.e("HomeScreen", "Object not found")
                                        objectDetectionState.value = "Object with text not found"
                                        showDialog = true
                                        if(isMicEnabled){
                                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String?) {
                                                    speechRecognizer.stopListening()
                                                }

                                                override fun onDone(utteranceId: String?) {
                                                    if (utteranceId == "DETECTION_RESULTS") {
                                                        if (text != null && text.isNotEmpty()) {
                                                            oCRText = "Found text "+ text + "though"
                                                            tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                        }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                            // Restart the speech recognizrr so it listens after speaking
                                                            speechRecognizer.startListening(speechIntent)
                                                        }
                                                    }
                                                }

                                                override fun onError(utteranceId: String?) {
                                                    speechRecognizer.startListening(speechIntent)
                                                }
                                            })
                                            tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                            objectDetectionState.value = ""
                                            showDialog = false
                                        }

                                    }

                                }
                            }
                        }

                    } else if (selectedOption == "Object Detection") {
                        // Process the image to recognize text
                        processCompressedJPEG(rotatedFrameBytes, context = context) { text, error ->
                            if (error != null) {
                                Log.e("HomeScreen", "Text recognition failed: ${error.message}")
                            } else {
                                recognizedText.value = text
                                Log.d("HomeScreen", "Recognized text: $text")

                                // If objectText is empty, perform object detection as usual
                                if (objectText.isEmpty()) {
                                    val detections = nanodetncnn.getAllFindDetections(selectedObject)
                                    Log.d("HomeScreen", selectedObject)
                                    objectDetectionState.value = detections.joinToString()
                                    showDialog = true
                                    if (isMicEnabled) {
                                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                            override fun onStart(utteranceId: String?) {
                                                speechRecognizer.stopListening()
                                            }

                                            override fun onDone(utteranceId: String?) {
                                                if (utteranceId == "DETECTION_RESULTS") {
                                                    if (text != null && text.isNotEmpty()) {
                                                        oCRText = "Reading text: " + text
                                                        tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                    }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                        // Restart the speech recognizrr so it listens after speaking
                                                        speechRecognizer.startListening(speechIntent)
                                                    }
                                                }
                                            }

                                            override fun onError(utteranceId: String?) {
                                                speechRecognizer.startListening(speechIntent)
                                            }
                                        })

                                        tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                        objectDetectionState.value = ""
                                        showDialog = false
                                    }

                                } else {
                                    // Calculate the Jaccard similarity
                                    val similarity = jaccardSimilarity(objectText, text ?: "")
                                    Log.d("Similarity Score", similarity.toString())
                                    // If the text similarity is greater than 10%
                                    if (similarity > 0.10) {
                                        // Perform object detection
                                        val detections = nanodetncnn.getAllFindDetections(selectedObject)
                                        Log.d("HomeScreen", selectedObject)
                                        objectDetectionState.value = detections.joinToString()
                                        showDialog = true

                                        if(isMicEnabled){
                                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String?) {
                                                    speechRecognizer.stopListening()
                                                }

                                                override fun onDone(utteranceId: String?) {
                                                    if (utteranceId == "DETECTION_RESULTS") {
                                                        oCRText = "Reading detected text: " + text
                                                        tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                    }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                        // Restart the speech recognizrr so it listens after speaking
                                                        speechRecognizer.startListening(speechIntent)
                                                    }
                                                }

                                                override fun onError(utteranceId: String?) {
                                                    speechRecognizer.startListening(speechIntent)
                                                }
                                            })

                                            tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                            objectDetectionState.value = ""
                                            showDialog = false
                                        }

                                    } else {
                                        Log.e("HomeScreen", "Object not found")
                                        objectDetectionState.value = "Object not found"
                                        showDialog = true
                                        if(isMicEnabled){
                                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String?) {
                                                    speechRecognizer.stopListening()
                                                }

                                                override fun onDone(utteranceId: String?) {
                                                    if (utteranceId == "DETECTION_RESULTS") {
                                                        if (text != null && text.isNotEmpty()) {
                                                            oCRText = "No text found either"
                                                            tts?.speak(oCRText, TextToSpeech.QUEUE_FLUSH, null, "TEXT_DETECTION_RESULTS")
                                                        }
                                                    }else if (utteranceId == "TEXT_DETECTION_RESULTS") {
                                                        // Restart the speech recognizrr so it listens after speaking
                                                        speechRecognizer.startListening(speechIntent)
                                                    }
                                                }

                                                override fun onError(utteranceId: String?) {
                                                    speechRecognizer.startListening(speechIntent)
                                                }
                                            })

                                            tts?.speak(objectDetectionState.value, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_RESULTS")
                                            objectDetectionState.value = ""
                                            showDialog = false
                                        }

                                    }
                                }
                            }
                        }
                    }

                } else {
                    Log.e("HomeScreen", "Failed to decode Bitmap from byte array")
                }
            } else {
                Log.e("HomeScreen", "Frame bytes are null or empty")
            }
        }
    }
    //-----------end------------


    DisposableEffect(isMicEnabled) {
        if (isMicEnabled) {
            // Initialize TextToSpeech with a listener
            tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.UK)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("HomeScreen", "Language not supported")
                    } else {
                        Log.d("HomeScreen", "TextToSpeech initialized")

                        // Speak the welcome message
                        val welcomeMessage = "You are currently at the home screen where you can find objects around you. To navigate to the Himba navigation screen, say 'move to navigation'. To find an object on this screen, say 'Find object ObjectName with text text'. To search for an object alone, say 'find object object name'. To search for text, say 'find text text'. If you want a full analysis of your environment, say 'full analysis'."
                        val utteranceId = "WELCOME_MESSAGE"

                        // Stop speech recognizer if it's listening to prevent conflicts
                        speechRecognizer.stopListening()

                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == "WELCOME_MESSAGE") {
                                    Handler(Looper.getMainLooper()).post {
                                        if (isMicEnabled) {
                                            speechRecognizer.startListening(speechIntent)
                                        }
                                    }
                                }
                            }
                            override fun onError(utteranceId: String?) {}
                        })

                        tts?.speak(
                            welcomeMessage,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            utteranceId
                        )
                    }
                } else {
                    Log.e("HomeScreen", "TextToSpeech initialization failed")
                }
            })

            val recognitionListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("HomeScreen", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("HomeScreen", "Beginning of speech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d("HomeScreen", "End of speech")
                }
                override fun onError(error: Int) {
                    Log.d("HomeScreen", "Error: $error")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Log.d("HomeScreen", "No match or speech timeout, providing feedback")

                        val ttsInstance = tts
                        if (ttsInstance != null) {
                            if (ttsInstance.isSpeaking) {
                                // If TTS is speaking, monitor until it finishes

                                coroutineScope.launch {
                                    while (ttsInstance.isSpeaking) {
                                        speechRecognizer.stopListening()
                                        delay(500) // Check every 500 milliseconds
                                    }
                                    // Start the speech recognizer once TTS has finished
                                    Handler(Looper.getMainLooper()).post {
                                        speechRecognizer.startListening(speechIntent)
                                    }
                                }
                            } else {
                                // Stop recognizer to prevent conflicts
                                speechRecognizer.stopListening()

                                // Provide audible feedback
                                val utteranceId = "ERROR_MESSAGE"

                                ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}

                                    override fun onDone(utteranceId: String?) {
                                        if (utteranceId == "ERROR_MESSAGE" && isMicEnabled) {
                                            // Restart listening on the main thread
                                            Handler(Looper.getMainLooper()).post {
                                                speechRecognizer.startListening(speechIntent)
                                            }
                                        }
                                    }

                                    override fun onError(utteranceId: String?) {}
                                })

                                ttsInstance.speak(
                                    "Sorry, I didn't catch that. Please try again.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "ERROR_MESSAGE"
                                )
                            }
                        } else {
                            speechRecognizer.startListening(speechIntent)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    //We need this flag to track whether the object exists or not so we don't try to search if the object does not exist
                    var objectExists = false
                    matches?.let {
                        val command = it[0].lowercase(Locale.ROOT)
                        Log.e("HomeScreenVoice", command)
                        when {
                            command.contains("move to navigation") -> {
                                navController.navigate("himba_screen")
                            }
                            command.contains("move to profile") -> {
                                navController.navigate("profile_screen")
                            }
                            command.contains("log me out") || command.contains("log out") || command.contains("sign out") -> {
                                viewModel.logoutUser()
                                navController.navigate("welcome_screen")
                            }
                            command.contains(
                                Regex("find object .* with text .*|" +
                                        "find object .* which text .*|" +
                                        "find object s .* with text .*|" +
                                        "find object .* witch text .*|" +
                                        "find objects .* witch text .*|" +
                                        "find objects .* with text .*|" +
                                        "find objects .* which text .*|" +
                                        "find object .* we text .*", RegexOption.IGNORE_CASE)) -> {
                                val matchResult = Regex("find object (.*?) with text (.*)", RegexOption.IGNORE_CASE)
                                    .find(command)

                                if (matchResult != null) {
                                    val objectName = matchResult.groupValues[1].trim()
                                    val textName = matchResult.groupValues[2].trim()

                                    selectedObject = objectName
                                    objectText = textName

                                    Log.d("HomeScreenTrack", "Extracted Object: $selectedObject, Extracted Text: $objectText")

                                    val matchingObjects = objects.filter { it.equals(objectName, ignoreCase = true) }

                                    if (selectedObject.isNotEmpty() && matchingObjects.isEmpty()) {
                                        Log.d("HomeScreenTrack", "matchingObjects called")
                                        val suggestions = objects.filter { it.startsWith(selectedObject.take(3), ignoreCase = true) }
                                        val suggestionMessage = if (suggestions.isNotEmpty()) {
                                            "Object does not exist. Suggestions: ${suggestions.joinToString(", ")}"
                                        } else {
                                            "Object does not exist."
                                        }
                                        tts?.speak(suggestionMessage, TextToSpeech.QUEUE_FLUSH, null, "SUGGESTION_MESSAGE")
                                        objectExists = false
                                    } else {
                                        val objectExistMessage = "Performing object detection on $selectedObject with text $objectText"
                                        tts?.speak(objectExistMessage, TextToSpeech.QUEUE_FLUSH, null, "OBJECTFOUNDCONFIRMATION_MESSAGE")
                                        objectExists = true
                                    }
                                } else {
                                    Log.e("HomeScreenTrack", "Failed to extract object name or text name.")
                                }
                            }

                            command.contains("find object")||command.contains("find objects") -> {
                                val objectName = command
                                    .replace(Regex(".*find object"), "").trim()
                                    .replace(Regex(".*find objects"), "").trim()
                                    .replace(Regex(".*find object s"), "").trim()
                                    .replace("empty", "")
                                    .replace(" ", "")
                                selectedObject = objectName
                                objectText = ""

                                val matchingObjects = objects.filter { it.equals(objectName, ignoreCase = true) }

                                if (selectedObject.isNotEmpty() && matchingObjects.isEmpty()) {
                                    Log.d("HomeScreenTrack", "matchingObjects called")
                                    val suggestions = objects.filter { it.startsWith(selectedObject.take(3), ignoreCase = true) }
                                    val suggestionMessage = if (suggestions.isNotEmpty()) {
                                        "Object does not exist. Suggestions: ${suggestions.joinToString(", ")}"
                                    } else {
                                        "Object does not exist."
                                    }
                                    tts?.speak(suggestionMessage, TextToSpeech.QUEUE_FLUSH, null, "SUGGESTION_MESSAGE")
                                    objectExists = false
                                }else{
                                    objectExists = true
                                    val objectExistMessage = "Performing object detection on ${selectedObject}"
                                    tts?.speak(objectExistMessage,TextToSpeech.QUEUE_FLUSH, null, "OBJECTFOUNDCONFIRMATION_MESSAGE" )

                                }
                            }
                            command.contains("find text") -> {
                                objectExists = true
                                val text = command
                                    .replace(Regex(".*find text"), "").trim()
                                    .replace(Regex(".*find texts"), "").trim()
                                    .replace("empty", "")
                                    .replace(" ", "")
                                Log.d("HomeScreen", "Finding text: $text")
                                objectText = text
                                selectedObject = "fredthedeve"
                                val textExistMessage = "Performing text recognition for text ${objectText}"
                                tts?.speak(textExistMessage,TextToSpeech.QUEUE_FLUSH, null, "TEXTFOUNDCONFIRMATION_MESSAGE" )
                            }
                            command.contains("search")||command.contains("full analysis") -> {
                                objectExists = true
                                Log.d("HomeScreen", "Performing full analysis")
                                objectText = ""
                                selectedObject = ""
                                val textExistMessage = "Performing full analysis on environment"
                                tts?.speak(textExistMessage,TextToSpeech.QUEUE_FLUSH, null, "FULLANALYSISCONFIRMATION_MESSAGE" )

                            }

                            command.contains("what objects can you detect") -> {
                                val objectsList = objects.joinToString(", ")
                                val objectsMessage = "Why did you do this? This is going to take a while my friend!! In summary i can detect 80 objects offline and about 130 objects online. Here are the following objects based on the selected technique: $objectsList"
                                tts?.speak(objectsMessage, TextToSpeech.QUEUE_FLUSH, null, "OBJECTSDETECTION_MESSAGE")
                            }

                            command.contains("cancel") -> {
                                // Stop  TTS and clear the queue
                                tts?.stop()
                                // Restart the speech recognizer
                                speechRecognizer.startListening(speechIntent)
                                return
                            }

                            command.contains("disable voice control") || command.contains("disables voice control") || command.contains("disabled voice control") -> {
                                isMicEnabled = false
                                return
                            }

                            command.contains("activate voice control") || command.contains("activates voice control") -> {
                                isMicEnabled = true
                                return
                            }

                            command.contains("activate image segmentation") || command.contains("activate segmentation") -> {
                                selectedOption = "Image Segmentation"
                            }

                            command.contains("activate object detection") || command.contains("activates detection") -> {
                                selectedOption = "Object Detection"
                            }


                        }
                        if (objectExists) {
                            findObjectFunctionality()
                            tts?.stop()
                            objectExists = false
                        }
                        speechRecognizer.startListening(speechIntent)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
            speechRecognizer.setRecognitionListener(recognitionListener)
        }

        onDispose {
            speechRecognizer.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }



    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CustomAppBar(
            navController,
            stringResource(R.string.home_screen_findObject),
            context = context,
            isMicEnabled = isMicEnabled,
            onMicToggle = { newMicState ->
                isMicEnabled = newMicState
            }
        ) },
        bottomBar = { CustomBottomAppBar(navController, "home_screen") }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, start = 15.dp, end = 15.dp, bottom = 25.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Object
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedObject,
                            onValueChange = { selectedObject = it },
                            label = { Text("Object Name", fontFamily = gothamFonts) },
                            textStyle = TextStyle(fontFamily = gothamFonts),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                        ) {
                            if (filteredObjects.isNotEmpty()) {
                                filteredObjects.forEach { objectName ->
                                    DropdownMenuItem(
                                        text = { Text(text = objectName) },
                                        onClick = {
                                            selectedObject = objectName
                                            expanded = false
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text(text = "Object does not exist") },
                                    onClick = {
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    // Model
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .zIndex(1f)
                            .clickable {
                                expandedModel = !expandedModel
                            }
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    expandedModel = !expandedModel
                                }
                        )
                        DropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false }
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    selectedOption = "Object Detection"
                                    expandedModel = false
                                },
                                text = {
                                    Text("Object Detection")
                                }
                            )

                            DropdownMenuItem(
                                onClick = {
                                    selectedOption = "Image Segmentation"
                                    expandedModel = false
                                },
                                text = {
                                    Text("Image Segmentation")
                                }
                            )
                        }
                    }
                }



                // Object Text
                OutlinedTextField(
                    value = objectText,
                    label = {
                        Text(
                            text = stringResource(R.string.homeScreenInputFieldObjectText),
                            fontFamily = gothamFonts
                        )
                    },
                    textStyle = TextStyle(fontFamily = gothamFonts),
                    onValueChange = { objectText = it },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(R.dimen.Home_Screen_TextFields_TFPadding))
                )

                // Search
                Button(
                    onClick = {
                        findObjectFunctionality()
                    },
                    Modifier.width(142.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "Search",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }


                Spacer(modifier=Modifier.height(12.dp))


                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .size(300.dp)
                    ) {

                        CameraPreview(nanodetncnn, facing, cpuGpuIndex, context)
                    }
                }

                // Display the captured image if available
                capturedImage.value?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured OCR Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }

                // Display the recognized text if available
                recognizedText.value?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                if (showDialog) {
                    ObjectDetectionDialog(
                        objectDetectionState = objectDetectionState.value,
                        onDismissRequest = { showDialog = false }
                    )
                }

                Spacer(modifier=Modifier.height(132.dp))

            }




            // Card at the bottom
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(8.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Status: $isConnected", fontFamily = gothamFonts)
                    Text(text = "FPS: $nanodetFps" , fontFamily = gothamFonts)
                }
            }
        }
    }
}




@Preview
@Composable
fun HomeScreenPreview(){
    HimbaVisionTheme {
        val navController = rememberNavController()
        HomeScreen(navController)

    }
}



/*
if (selectedOption == "Minimal Navigation") {

} else if (selectedOption == "Maximal Navigation") {

}
 */