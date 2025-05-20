package ie.tus.himbavision.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ie.tus.himbavision.R
import ie.tus.himbavision.jnibridge.HimbaJNIBridge
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.ui.bottomappbar.CustomBottomAppBar
import ie.tus.himbavision.ui.theme.gothamFonts
import ie.tus.himbavision.ui.topappbar.CustomAppBar
import ie.tus.himbavision.ui.utilitycomposables.CameraPreview
import ie.tus.himbavision.utility.Network.ConnectivityReceiver
import ie.tus.himbavision.utility.Locations.areLocationsClose
import ie.tus.himbavision.utility.DirectionsAPI.fetchOutdoorDirections
import ie.tus.himbavision.utility.Panoptic.navigateDetect
import ie.tus.himbavision.utility.Locations.rememberLocationUpdates
import ie.tus.himbavision.utility.Network.isOnline
import ie.tus.himbavision.utility.Other.rememberSaveableDirections
import ie.tus.himbavision.utility.Other.rotateFrameRight
import ie.tus.himbavision.utility.Vibrations.vibrateBasedOnDirection
import ie.tus.himbavision.viewmodel.AuthViewModel
import ie.tus.himbavision.viewmodel.AuthViewModelFactory
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlDetailsViewModel
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlViewModelFactory
import ie.tus.himbavision.viewmodel.UpdateLatLngViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HimbaNavScreen(navController: NavController) {
    var destination by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val currentLocation by rememberLocationUpdates()
    val directions = rememberSaveableDirections()
    val coroutineScope = rememberCoroutineScope()

    val nanodetncnn = HimbaJNIBridge()
    val cpuGpuIndex by rememberSaveable { mutableStateOf(0) }
    val facing by rememberSaveable { mutableStateOf(1) }

    val secureStorageHelper = SecureStorageHelper(context)
    val user = secureStorageHelper.getUser()
    val updateLatLngViewModel: UpdateLatLngViewModel = viewModel()

    // State to hold the minimal directions from the JNI
    var minNavDirections by remember { mutableStateOf(emptyArray<String>()) }
    var maxNavDirections by remember { mutableStateOf(emptyArray<String>()) }


    var panMinNavDirections by remember { mutableStateOf(emptyList<String>()) }
    var panMaxNavDirections by remember { mutableStateOf(emptyList<String>()) }


    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf("Minimal Navigation") }

    var focalLengthPx by remember { mutableStateOf(-1.0f) }

    // State to hold the captured image
    val capturedImage = remember { mutableStateOf<Bitmap?>(null) }

    val connectivityReceiver = remember { ConnectivityReceiver(context) }
    val isConnected by connectivityReceiver.isConnected.observeAsState("Disconnected")

    var expandedModel by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("Object Detection") }

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    //Get Offline and Online voice control
    val getVoiceViewModel: RetrieveVoiceControlDetailsViewModel =
        viewModel(factory = RetrieveVoiceControlViewModelFactory(context))

    var isMicEnabled by rememberSaveable { mutableStateOf(false) }
    var isContextualNavSpeaking by rememberSaveable { mutableStateOf(false) }
    var activateVoiceControlMaps by rememberSaveable { mutableStateOf(true) }

    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    //----- VOICE CONTROL ---- START-------------------
    var tts: TextToSpeech? by remember { mutableStateOf(null) }


    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
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


    // LaunchedEffect to periodically fetch directions
    LaunchedEffect(selectedModel) {
        if (selectedModel == "Object Detection") {
            while (true) {
                minNavDirections = nanodetncnn.getMinNavDirections()
                maxNavDirections = nanodetncnn.getMaxNavDirections()
                delay(1000) // Fetch directions every 1 second
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

    // Speak directions
    fun speakDirections(context: Context, directions: List<String>, tts: TextToSpeech) {
        Log.d("ConfirmationMs", "speakDirections called")
        CoroutineScope(Dispatchers.Main).launch {
            while (!isContextualNavSpeaking && isMicEnabled && activateVoiceControlMaps) {
                Log.d("ConfirmationMs", isContextualNavSpeaking.toString())
                Log.d("ConfirmationMs", isMicEnabled.toString())
                Log.d("ConfirmationMs", activateVoiceControlMaps.toString())
                if (directions.isNotEmpty()) {
                    val firstDirection = directions.first()
                    val parts = firstDirection.split(", ")
                    val textToSpeak = when {
                        parts[0].startsWith("Walk:") -> {
                            // Remove "Walk:" prefix
                            "Continue ahead " + parts[0].removePrefix("Walk:").trim()
                        }

                        parts[0].startsWith("Bus:") -> {
                            val departureStopPart =
                                parts[1] + ", " + parts[2] + ", " + parts[3] + ", " + parts[4]
                            val arrivalStopPart =
                                parts[0] + ", " + parts[1] + ", " + parts[2] + ", " + parts[3] + ", " + parts[4]

                            val agencyBusRegex = Regex("Bus: (.*)")
                            val arrivalStopRegex = Regex("Arrival Stop: (.*?) \\(Lat:")
                            val departureStopRegex = Regex("Departure Stop: (.*?) \\(Lat:")
                            val departureTimeRegex = Regex("Departure Time: (.*)")

                            val agencyBus = parts.find { agencyBusRegex.containsMatchIn(it) }
                                ?.let { agencyBusRegex.find(it)?.groupValues?.get(1) }
                            val arrivalStop =
                                arrivalStopRegex.find(arrivalStopPart)?.groupValues?.get(1)
                            val departureStop =
                                departureStopRegex.find(departureStopPart)?.groupValues?.get(1)
                            val departureTime =
                                parts.find { departureTimeRegex.containsMatchIn(it) }
                                    ?.let { departureTimeRegex.find(it)?.groupValues?.get(1) }

                            buildString {
                                append("Take ")
                                append(agencyBus ?: "the bus")
                                append(" from ")
                                append(departureStop ?: "the departure stop")
                                append(" to ")
                                append(arrivalStop ?: "the arrival stop")
                                departureTime?.let {
                                    append(" at $it")
                                }
                            }
                        }

                        else -> {
                            // Handle other types of directions if any
                            firstDirection
                        }
                    }

                    // Speak the direction
                    tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(16000)
                } else {
                    delay(8000) // 8 seconds before checking again
                }
            }
        }
    }

    fun speakOADirections(context: Context, tts: TextToSpeech) {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (selectedModel == "Object Detection" && selectedOption == "Haptic Feedback" || selectedModel == "Image Segmentation" && selectedOption == "Haptic Feedback") {
                    Log.d("HimbaNavScreen", "Haptic Feedback selected, skipping TTS")
                    delay(4000) // Wait for 4 seconds before checking again
                    continue
                }

                val oADirections = when {
                    selectedModel == "Object Detection" && selectedOption == "Minimal Navigation" -> minNavDirections
                    selectedModel == "Object Detection" && selectedOption == "Maximal Navigation" -> maxNavDirections
                    selectedModel == "Image Segmentation" && selectedOption == "Minimal Navigation" -> panMinNavDirections.toTypedArray()
                    selectedModel == "Image Segmentation" && selectedOption == "Maximal Navigation" -> panMaxNavDirections.toTypedArray()
                    else -> minNavDirections
                }

                // Log the full oADirections array
                Log.d("HimbaNavScreen", "Current oADirections: ${oADirections.joinToString(", ")}")

                if (oADirections.isEmpty()) {
                    Log.d("HimbaNavScreen", "No directions detected, waiting...")
                    delay(4000) // Wait for 4 seconds before rechecking
                    continue
                }

                val containsContinue =
                    oADirections.any { it.contains(Regex(".*continue.*", RegexOption.IGNORE_CASE)) }

                for (oadirection in oADirections) {
                    Log.d("HimbaNavScreen", "Speaking direction: $oadirection")

                    if (selectedOption == "Minimal Navigation" && containsContinue) {
                        isContextualNavSpeaking = false
                        tts?.speak(oadirection, TextToSpeech.QUEUE_FLUSH, null, null)
                        while (tts?.isSpeaking == true) {
                            delay(2000)
                        }
                        Log.d("HimbaNavScreen", "Finished speaking direction: $oadirection")
                        delay(4000) // Wait for 4 seconds before speaking the next direction
                    } else {
                        isContextualNavSpeaking = true
                        tts?.speak(oadirection, TextToSpeech.QUEUE_FLUSH, null, null)
                        while (tts?.isSpeaking == true) {
                            delay(100)
                        }
                        Log.d("HimbaNavScreen", "Finished speaking direction: $oadirection")
                        delay(1) // Continue with a delay of 1ms
                    }

                    speakDirections(context, directions, tts!!)
                }
            }
        }
    }

    fun GetDirections(){
        if (destination.isNotEmpty()){
            currentLocation?.let { location ->
                fetchOutdoorDirections(
                    context = context,
                    originLat = location.latitude,
                    originLng = location.longitude,
                    destination = destination
                ) { fetchedDirections ->
                    directions.clear()
                    directions.addAll(fetchedDirections.filter { direction ->
                        val parts = direction.split(", ")
                        if (parts[0].startsWith("Walk:")) {
                            parts.forEachIndexed { index, part -> Log.d("mNavScreen", "Part $index: $part") }
                            val endLat = parts[4].substringAfter("(").substringBefore(",").toDoubleOrNull()
                            Log.d("mNavScreen", "End Lat: $endLat")
                            val endLng = parts[5].substringAfter(", ").substringBefore(")").toDoubleOrNull()
                            Log.d("mNavScreen", "End Long: $endLng")
                            if (endLat != null && endLng != null) {
                                !areLocationsClose(location.latitude, location.longitude, endLat, endLng)
                            } else {
                                true
                            }
                        } else if (parts[0].startsWith("Bus:")) {
                            parts.forEachIndexed { index, part -> Log.d("bNavScreen", "Part $index: $part") }
                            val arrivalStopLat = parts[3].substringAfter("Lat: ").substringBefore(",").toDoubleOrNull()
                            Log.d("bNavScreen", "Arrival Stop Lat: $arrivalStopLat")
                            val arrivalStopLng = parts[4].substringAfter("Lng: ").substringBefore(")").toDoubleOrNull()
                            Log.d("bNavScreen", "Arrival Stop Long: $arrivalStopLng")
                            if (arrivalStopLat != null && arrivalStopLng != null) {
                                !areLocationsClose(location.latitude, location.longitude, arrivalStopLat, arrivalStopLng)
                            } else {
                                true
                            }
                        } else {
                            true
                        }
                    })


                }
            }
        }
    }

    // Coroutine to fetch and process frames
    DisposableEffect(selectedModel) {
        selectedModel = if (isConnected == "disconnected") "Object Detection" else selectedModel
        val job = coroutineScope.launch(Dispatchers.IO) { // Use Dispatchers.IO for background execution


            while (selectedModel == "Image Segmentation" && isOnline(context)) {
                val startTime = System.currentTimeMillis() // Record start time

                val frameBytes = nanodetncnn.getLatestFrame()
                if (frameBytes != null && frameBytes.isNotEmpty()) {
                    // Decode the byte array to a Bitmap
                    var bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                    if (bitmap != null) {
                        // Rotate the frame to the right
                        bitmap = rotateFrameRight(bitmap)
                        withContext(Dispatchers.Main) {
                            capturedImage.value = bitmap // Update state to display the image on the UI thread
                        }
                        Log.d("HimbaNavScreen", "Frame has been captured")

                        navigateDetect(
                            focalLengthPx.toDouble(),
                            bitmap,
                            context
                        ) { navigationInstructions ->
                            if (navigationInstructions != null) {
                                // Handle Minimal Navigation Instructions
                                val minimalInstructions = navigationInstructions.navigation.minimal_navigation
                                Log.d("NavigationPanopt", "Minimal Instructions:")
                                if (minimalInstructions != null) {
                                    panMinNavDirections = minimalInstructions
                                    minimalInstructions.forEach { instruction ->
                                        Log.d("NavigationPanopt", instruction)
                                    }
                                }

                                // Handle Maximal Navigation Instructions
                                val maximalInstructions = navigationInstructions.navigation.maximal_navigation
                                Log.d("NavigationPanopt", "Maximal Instructions:")
                                if (maximalInstructions != null) {
                                    panMaxNavDirections = maximalInstructions
                                    maximalInstructions.forEach { instruction ->
                                        Log.d("NavigationPanopt", instruction)
                                    }
                                }
                            } else {
                                Log.d("NavigationPanopt", "Failed to get navigation instructions.")
                            }
                        }
                    } else {
                        Log.d("HimbaNavScreen", "Failed to decode Bitmap from byte array")
                    }
                } else {
                    Log.d("HimbaNavScreen", "Frame bytes are null or empty")
                }

                val endTime = System.currentTimeMillis() // Record end time
                val processingTime = endTime - startTime // Calculate processing time

                // Wait for the processing time before fetching the next frame
                delay(processingTime)
            }
        }

        onDispose {
            job.cancel()
        }
    }

    //TTS
    DisposableEffect(isMicEnabled) {
        if (isMicEnabled) {
            // Initialize TextToSpeech with a listener
            tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.UK)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("HimbaNavScreen", "Language not supported")
                    } else {
                        Log.d("HimbaNavScreen", "TextToSpeech initialized")

                        // Speak the welcome message
                        val welcomeMessage =
                            "Currently on the navigation screen, say 'Set destination' to set a destination. Then, say 'Activate minimal, maximal, or haptic feedback' to test different navigation modes"
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

                        Log.e("ConfirmationMs", "Outdoor called")
                        // If the mic gets enabled without the screen recomposing execute the function to speak directions
                        speakDirections(context, directions, tts!!)

                        Log.e("ConfirmationMs", "Object avoidance called")
                        speakOADirections(context, tts!!)

                    }
                } else {
                    Log.e("HimbaNavScreen", "TextToSpeech initialization failed")
                }
            })
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    //Speech Recognizer
    DisposableEffect(isMicEnabled) {
        if (isMicEnabled) {
            Log.d("HimbaMavScreen", "recog")
            speechRecognizer.startListening(speechIntent)

            Log.d("HimbaMavScreen", "yep for speech")
            val recognitionListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("HimbaMavScreen", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("HimbaMavScreen", "Beginning of speech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d("HimbaMavScreen", "End of speech")
                }
                override fun onError(error: Int) {
                    Log.d("HimbaMavScreen", "Error: $error")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Log.d("HimbaMavScreen", "No match or speech timeout, providing feedback")

                        val ttsInstance = tts
                        if (ttsInstance != null) {
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


                            // Continue listening while TTS is speaking
                            if (isMicEnabled) {
                                Handler(Looper.getMainLooper()).post {
                                    speechRecognizer.startListening(speechIntent)
                                }
                            }
                        } else {
                            speechRecognizer.startListening(speechIntent)
                        }
                    }
                }


                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.let {
                        val command = it[0].lowercase(Locale.ROOT)
                        Log.e("HimbaMavScreen", command)
                        when {
                            command.contains("move to home") -> {
                                navController.navigate("home_screen")
                            }
                            command.contains("move to profile") -> {
                                navController.navigate("profile_screen")
                            }
                            command.contains("set destination")
                                    || command.contains("set destinations")
                                    || command.contains("destination")
                                    || command.contains("destinations")
                                    || command.contains("go to")  -> {
                                val text = command
                                    .replace(Regex(".*set destination"), "").trim()
                                    .replace(Regex(".*set destinations"), "").trim()
                                    .replace(Regex(".*destination"), "").trim()
                                    .replace(Regex(".*destinations"), "").trim()
                                    .replace(Regex(".*go to"), "").trim()
                                    .replace("empty", "")
                                    .replace(" ", "")
                                Log.d("HomeScreen", "Finding text: $text")
                                destination = text
                                val textExistMessage = "Finding route from current location to ${destination}"
                                //tts?.speak(textExistMessage,TextToSpeech.QUEUE_FLUSH, null, "ROUTE_CONF_DESTINATION_MESSAGE" )

                                GetDirections()

                            }
                            command.contains("log me out") || command.contains("log out") || command.contains("sign out") -> {
                                viewModel.logoutUser()
                                navController.navigate("welcome_screen")
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

                            command.contains("activate image segmentation") || command.contains("activate segmentation") -> {
                                selectedModel = "Image Segmentation"
                            }

                            command.contains("activate object detection") || command.contains("activate detection") -> {
                                selectedModel = "Object Detection"
                            }

                            command.contains("activate minimal navigation") || command.contains("activate minimal") || command.contains("activate minimum") || command.contains("activate basic") -> {
                                selectedOption = "Minimal Navigation"

                            }

                            command.contains("disable maps") -> {
                                activateVoiceControlMaps = false
                            }
                            command.contains("enable maps") -> {
                                activateVoiceControlMaps = true
                            }

                            command.contains("activate maximal navigation") || command.contains("activate maximal") || command.contains("activate maximum") || command.contains("activate detailed") -> {
                                selectedOption = "Maximal Navigation"
                            }

                            command.contains("activate haptic feedback") || command.contains("activate haptic") || command.contains("activate vibration") || command.contains("activate vibrations") -> {
                                selectedOption = "Haptic Feedback"
                            }


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
        }
    }



    // Update location every 10 seconds
    LaunchedEffect(currentLocation) {
        while (true) {
            delay(6000) // 6 seconds
            currentLocation?.let {
                val latLng = "${it.latitude}, ${it.longitude}"
                if (user != null) {
                    updateLatLngViewModel.updateLatLng(user.email, latLng, context)
                }
            }
            GetDirections()

        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CustomAppBar(navController,
            stringResource(R.string.nav_screen_findObject),
            context = context,
            isMicEnabled = isMicEnabled,
            onMicToggle = { newMicState ->
                isMicEnabled = newMicState
            }
        ) },
        bottomBar = { CustomBottomAppBar(navController, "himba_screen") }
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

                    Box(modifier = Modifier
                        .weight(1f)
                        .zIndex(1f)
                        .clickable {
                            expanded = !expanded
                            Log.d("BBDropdownMenu", "Expanded: $expanded")
                        }

                    ) {
                        OutlinedTextField(
                            value = selectedOption,
                            onValueChange = { },
                            label = { Text("Options") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Box(modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                expanded = !expanded
                                Log.d("BBDropdownMenu", "Expanded: $expanded")
                            })
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                selectedOption = "Minimal Navigation"
                                expanded = false
                            },
                                text ={
                                    Text("Minimal Navigation")
                                }
                            )

                            DropdownMenuItem(onClick = {
                                selectedOption = "Maximal Navigation"
                                expanded = false
                            },
                                text ={
                                    Text("Maximal Navigation")
                                }
                            )
                            DropdownMenuItem(onClick = {
                                selectedOption = "Haptic Feedback"
                                expanded = false
                            },
                                text ={
                                    Text("Haptic Feedback")
                                }
                            )

                        }
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    //Model
                    Box(modifier = Modifier
                        .wrapContentWidth()
                        .zIndex(1f)
                        .clickable {
                            expandedModel = !expandedModel
                        }

                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options")

                        Box(modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                expandedModel = !expandedModel
                            })
                        DropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                selectedModel = "Object Detection"
                                expandedModel = false
                            },
                                text ={
                                    Text("Object Detection")
                                }
                            )

                            DropdownMenuItem(onClick = {
                                selectedModel = "Image Segmentation"
                                expandedModel = false
                            },
                                text ={
                                    Text("Image Segmentation")
                                }
                            )

                        }
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    OutlinedTextField(
                        value = currentLocation?.let { "${it.latitude}, ${it.longitude}" } ?: "Location not available",
                        label = {
                            Text(
                                text = stringResource(R.string.navScreenFrom),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { /* prevent user from editing */ },
                        readOnly = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = dimensionResource(R.dimen.Home_Screen_TextFields_TFPadding))
                    )


                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedTextField(
                        value = destination,
                        label = {
                            Text(
                                text = stringResource(R.string.navScreenTo),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { destination = it },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = dimensionResource(R.dimen.Home_Screen_TextFields_TFPadding))
                    )
                }

                Button(
                    onClick = {
                        GetDirections()
                    },
                    Modifier.width(142.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "Go",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                /*
                Check if our current location is close to the end lat and lng for walk
                and arrivalStop lat and lng for bus, if it is store its index and delete all the
                directions from that index and above
                 */

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(5500) // 5.5 seconds
                        currentLocation?.let { location ->
                            var removeUpToIndex = -1
                            directions.forEachIndexed { index, direction ->
                                Log.d("Running again", "Running again after 5.5 secs")
                                val parts = direction.split(", ")
                                if (parts[0].startsWith("Walk:")) {
                                    val endLat = parts[4].substringAfter("(").substringBefore(",").toDoubleOrNull()
                                    val endLng = parts[5].substringAfter(", ").substringBefore(")").toDoubleOrNull()
                                    if (endLat != null && endLng != null) {
                                        val areClose = areLocationsClose(location.latitude, location.longitude, endLat, endLng)
                                        Log.d("RunningAreLocations", "Checking if locations are close for after 5.5 secs ${location.latitude} ${location.longitude} Then ${endLat} ${endLng} -> $areClose")
                                        if (areClose) {
                                            Log.d("RunningAreLocations", "Locations are close, removing direction")
                                            removeUpToIndex = index
                                            return@forEachIndexed
                                        }
                                    } else {
                                        Log.d("RunningAreLocations", "No lat and long for current direction")
                                    }
                                } else if (parts[0].startsWith("Bus:")) {
                                    val arrivalStopLat = parts[3].substringAfter("Lat: ").substringBefore(",").toDoubleOrNull()
                                    val arrivalStopLng = parts[4].substringAfter("Lng: ").substringBefore(")").toDoubleOrNull()
                                    if (arrivalStopLat != null && arrivalStopLng != null) {
                                        val busAreClose = areLocationsClose(location.latitude, location.longitude, arrivalStopLat, arrivalStopLng)
                                        if (busAreClose) {
                                            Log.d("RunningAreLocations", "Locations are close, removing direction")
                                            removeUpToIndex = index
                                            return@forEachIndexed
                                        }
                                    }
                                }
                            }

                            if (removeUpToIndex != -1) {
                                directions.subList(0, removeUpToIndex + 1).clear()
                                Log.d("RunningAreLocations", "Removed directions up to index $removeUpToIndex")
                            }

                            // Log the directions list after the removeAll operation
                            Log.d("RunningAreLocations", directions.toString())
                        }
                    }
                }





                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(500.dp)
                ) {
                    CameraPreview(nanodetncnn, facing, cpuGpuIndex, context)
                }

                Spacer(modifier = Modifier.width(30.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        //Heading
                        Text(
                            text = "Outdoor Navigational Directions",
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )

                        directions.forEach { direction ->
                            val parts = direction.split(", ")
                            if (parts[0].startsWith("Walk:")) {
                                val walkPart = parts[0]
                                val durationPart = parts[1]

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .widthIn(max = 150.dp)
                                ) {
                                    Text(
                                        text = "$walkPart",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else if (parts[0].startsWith("Bus:")) {
                                parts.forEachIndexed { index, part -> Log.d("NavScreen", "Part $index: $part") }

                                // Combine relevant parts for Departure Stop
                                val departureStopPart = parts[1] + ", " + parts[2] +", " + parts[3] +", " + parts[4]
                                val arrivalStopPart = parts[0] + ", " + parts[1] + ", " + parts[2] + ", " + parts[3] + ", " + parts[4]

                                // Use regular expressions to extract the required information
                                val agencyBusRegex = Regex("Bus: (.*)")
                                val arrivalStopRegex = Regex("Arrival Stop: (.*?) \\(Lat:")
                                val departureStopRegex = Regex("Departure Stop: (.*?) \\(Lat:")
                                val departureTimeRegex = Regex("Departure Time: (.*)")

                                val agencyBus = parts.find { agencyBusRegex.containsMatchIn(it) }?.let { agencyBusRegex.find(it)?.groupValues?.get(1) }
                                val arrivalStop = arrivalStopRegex.find(arrivalStopPart)?.groupValues?.get(1)
                                val departureStop = departureStopRegex.find(departureStopPart)?.groupValues?.get(1)
                                val departureTime = parts.find { departureTimeRegex.containsMatchIn(it) }?.let { departureTimeRegex.find(it)?.groupValues?.get(1) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .widthIn(max = 150.dp)
                                ) {
                                    Text(
                                        text = "Take $agencyBus from $departureStop to $arrivalStop at $departureTime",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .widthIn(max = 150.dp)
                                ) {
                                    Text(
                                        text = direction,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        //Heading
                        Text(
                            text = "Object Avoidance Directions",
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )

                        if (selectedModel == "Object Detection"){
                            if (selectedOption == "Minimal Navigation") {
                                minNavDirections.forEach { direction ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else if (selectedOption == "Maximal Navigation") {
                                maxNavDirections.forEach { direction ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else {
                                minNavDirections.forEach { direction ->
                                    vibrateBasedOnDirection(context, direction)

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }else if (selectedModel == "Image Segmentation"){
                            if (selectedOption == "Minimal Navigation"){
                                panMinNavDirections.forEach { direction ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }else if (selectedOption == "Maximal Navigation") {
                                panMaxNavDirections.forEach { direction ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else {
                                minNavDirections.forEach { direction ->
                                    vibrateBasedOnDirection(context, direction)

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .widthIn(max = 150.dp)
                                    ) {
                                        Text(
                                            text = direction,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(35.dp))
            }

            // Card at the bottom
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(8.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Status: $isConnected", fontFamily = gothamFonts)

                }
            }

        }
    }
}