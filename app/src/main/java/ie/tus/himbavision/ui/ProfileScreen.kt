package ie.tus.himbavision.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.R
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.localsecurestorage.SegmentationKeyStorageHelper
import ie.tus.himbavision.ui.bottomappbar.CustomBottomAppBar
import ie.tus.himbavision.ui.dialogs.ErrorMessageDialog
import ie.tus.himbavision.ui.dialogs.ObjectDetectionDialog
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.ui.theme.gothamFonts
import ie.tus.himbavision.ui.topappbar.CustomAppBar
import ie.tus.himbavision.utility.Locations.rememberLocationUpdates
import ie.tus.himbavision.viewmodel.AuthViewModel
import ie.tus.himbavision.viewmodel.AuthViewModelFactory
import ie.tus.himbavision.viewmodel.RetrieveProfileViewModel
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlDetailsViewModel
import ie.tus.himbavision.viewmodel.RetrieveVoiceControlViewModelFactory
import ie.tus.himbavision.viewmodel.UpdateLatLngViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val segmentationKeyStorageHelper = remember { SegmentationKeyStorageHelper(context) }
    var segmentationKey by rememberSaveable { mutableStateOf(segmentationKeyStorageHelper.getSegmentationKey()) }
    val secureStorageHelper = SecureStorageHelper(context)
    val user = secureStorageHelper.getUser()
    val currentLocation by rememberLocationUpdates()

    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    val retrieveProfileViewModel: RetrieveProfileViewModel = viewModel()
    val updateLatLngViewModel: UpdateLatLngViewModel = viewModel()

    var errorMessage by remember { mutableStateOf<String?>(null) }


    //Get Offline and Online voice control
    val getVoiceViewModel: RetrieveVoiceControlDetailsViewModel = viewModel(factory = RetrieveVoiceControlViewModelFactory(context))

    var isMicEnabled by rememberSaveable { mutableStateOf(false)}

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
    var hasSpokenWelcomeMessage by remember { mutableStateOf(false) }


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

    // Check if user data exists
    if (user == null) {
        // Navigate back to the profile screen if user data does not exist
        LaunchedEffect(Unit) {
            navController.navigate("profile_screen")
        }
    } else {
        // Call the ViewModel to get user details from Firebase using the email
        LaunchedEffect(Unit) {
            retrieveProfileViewModel.getUserByEmail(user.email, context)
        }

        // Observe the user details from the ViewModel
        val userDetails by retrieveProfileViewModel.userDetails.observeAsState()

        // Update location every 10 seconds
        LaunchedEffect(currentLocation) {
            while (true) {
                delay(10000) // 10 seconds
                currentLocation?.let {
                    val latLng = "${it.latitude}, ${it.longitude}"
                    updateLatLngViewModel.updateLatLng(user.email, latLng, context)
                }
            }
        }



        DisposableEffect(isMicEnabled) {
            if (isMicEnabled) {
                // Initialize TextToSpeech with a listener
                tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.UK)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("Profile", "Language not supported")
                        } else {
                            Log.d("Profile", "TextToSpeech initialized")

                            // Speak the welcome message
                            if (!hasSpokenWelcomeMessage) {
                                val welcomeMessage = "You're at the profile page, to set your segmentation key say 'set segmentation key key' "
                                val utteranceId = "WELCOME_MESSAGE"

                                // Stop speech recognizer if it's listening to prevent conflicts
                                speechRecognizer.stopListening()

                                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) {
                                        if (utteranceId == "WELCOME_MESSAGE") {
                                            // Start listening after welcome message is spoken
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

                                hasSpokenWelcomeMessage = true
                            }
                        }
                    } else {
                        Log.e("Profile", "TextToSpeech initialization failed")
                    }
                })

                val recognitionListener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("Profile", "Ready for speech")
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("Profile", "Beginning of speech")
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        // Log.d("Profile", "RMS changed: $rmsdB")
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Log.d("Profile", "Buffer received")
                    }
                    override fun onEndOfSpeech() {
                        Log.d("Profile", "End of speech")
                    }
                    override fun onError(error: Int) {
                        Log.d("Profile", "Error: $error")
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            Log.d("Profile", "No match or speech timeout, providing feedback")

                            val ttsInstance = tts // Safe reference to tts
                            if (ttsInstance != null) {
                                // Stop recognizer to prevent conflicts
                                speechRecognizer.stopListening()

                                // Provide audible feedback
                                if (ttsInstance.isSpeaking) {
                                    ttsInstance.stop()
                                }
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
                            } else {
                                // If TTS isn't initialized, restart listening anyway
                                speechRecognizer.startListening(speechIntent)
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.let {
                            val command = it[0].toLowerCase(Locale.ROOT)
                            Log.d("RegScreen", "Recognized command: $command")
                            when {
                                command.contains("set segmentation") ||command.contains("set segmentation key") || command.contains("segmentation key") -> {
                                    segmentationKey = command.substringAfterLast("segmentation key")
                                        .replace(Regex(".*segmentation key"), "")
                                        .replace(Regex(".*set segmentation key"), "")
                                        .replace(Regex(".*set segmentation"), "")
                                        .replace("dash", "-")
                                        .replace("hyphen", "-")
                                        .replace(" ", "")
                                        .trim()

                                    segmentationKey?.let { segmentationKeyStorageHelper.saveSegmentationKey(it) }
                                }

                                command.contains("submit") -> {
                                    segmentationKey?.let { segmentationKeyStorageHelper.saveSegmentationKey(it) }
                                }
                                command.contains("log me out") || command.contains("log out") || command.contains("sign out") -> {
                                    viewModel.logoutUser()
                                    navController.navigate("welcome_screen")
                                }

                                command.contains("disable voice control") || command.contains("disables voice control") || command.contains("disabled voice control") -> {
                                    isMicEnabled = false
                                    return
                                }
                                command.contains("move to home") -> {
                                    navController.navigate("home_screen")
                                }
                                command.contains("move to navigation") -> {
                                    navController.navigate("himba_screen")
                                }


                            }
                            // Restart listening after processing the command
                            speechRecognizer.startListening(speechIntent)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Log.d("Profile", "Event: $eventType")
                    }
                }
                speechRecognizer.setRecognitionListener(recognitionListener)
            }

            onDispose {
                speechRecognizer.destroy()
                tts?.stop()
                tts?.shutdown()
                hasSpokenWelcomeMessage = false
            }
        }


        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { CustomAppBar(
                navController,
                stringResource(R.string.profile_screen),
                context = context,
                isMicEnabled = isMicEnabled,
                onMicToggle = { newMicState ->
                    isMicEnabled = newMicState
                }
            ) },
            bottomBar = { CustomBottomAppBar(navController, "profile_screen") }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Show error message dialog if there's an error
                retrieveProfileViewModel.errorMessage.observeAsState().value?.let {
                    ErrorMessageDialog(
                        errorMessage = it,
                        onDismissRequest = {
                            errorMessage = null
                            retrieveProfileViewModel.errorMessage.value = null
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally

                ) {
                    Image(
                        painter = painterResource(id = R.drawable.himbawhite),
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(4.dp, color = Color.Gray, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row {
                    Text(
                        text = "Name: ",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = userDetails?.fullname ?: "",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Thin
                    )
                }

                Row {
                    Text(
                        text = "Email: ",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = userDetails?.email ?: "",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Thin
                    )
                }

                Row {
                    Text(
                        text = "Emergency Email: ",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = userDetails?.emergencyEmail ?: "",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Thin
                    )
                }

                Row {
                    Text(
                        text = "Current Location: ",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = userDetails?.lastKnownLocation ?: "",
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Thin
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Please enter your segmentation key below. Ensure it is entered exactly as it was sent.",
                    fontFamily = gothamFonts,
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                OutlinedTextField(
                    value = segmentationKey ?: "",
                    onValueChange = { segmentationKey = it },
                    label = { Text("Segmentation Key", fontFamily = gothamFonts) },
                    textStyle = TextStyle(fontFamily = gothamFonts),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        segmentationKey?.let { segmentationKeyStorageHelper.saveSegmentationKey(it) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(142.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "Enter",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

    }

}


@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    HimbaVisionTheme {
        val navController = rememberNavController()
        ProfileScreen(navController)
    }
}