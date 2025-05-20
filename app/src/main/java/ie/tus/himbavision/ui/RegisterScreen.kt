package ie.tus.himbavision.ui

import android.Manifest
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.R
import ie.tus.himbavision.ui.dialogs.AuthMessageCard
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.ui.theme.gothamFonts
import ie.tus.himbavision.viewmodel.AuthViewModel
import ie.tus.himbavision.viewmodel.AuthViewModelFactory
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModel
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModelFactory
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreenTopAppBar(navController: NavHostController) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {


                // Spacer to add some space between the arrow and the text
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.Register_Screen_TopAppBar_spacerWidth)))

                // Register text
                Text(
                    text = stringResource(R.string.register_text),
                    fontFamily = gothamFonts,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        navigationIcon = {
            // Back arrow button
            IconButton(
                onClick = {navController.popBackStack()}
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.backArrow_contentDescription)
                )
            }

        },
        modifier = Modifier.wrapContentHeight().padding(dimensionResource(R.dimen.Register_Screen_TopAppBar_padding))
    )
}


@Composable
fun RegisterScreen(navController: NavHostController){
    val context = LocalContext.current
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var emergencyContactEmail by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var reactiveVoiceControlEnabled by rememberSaveable { mutableStateOf(true) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
        }
    }

    // Variable to hold TextToSpeech instance
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d("RegScreen", "Permission granted")
        } else {
            Log.d("RegScreen", "Permission denied")
        }
    }

    // Ensure we speak the welcome message only once
    var hasSpokenWelcomeMessage by remember { mutableStateOf(false) }

    //Firebase
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    val errorMessage by viewModel.errorMessage.observeAsState()
    val successMessage by viewModel.successMessage.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val userId by viewModel.userId.observeAsState()
    val registerSuccess by viewModel.registerSuccess.observeAsState()
    var showAuthMessageCard by remember { mutableStateOf(false) }
    //End



    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            showAuthMessageCard = true
        }
    }


    DisposableEffect(reactiveVoiceControlEnabled) {
        if (reactiveVoiceControlEnabled) {
            // Initialize TextToSpeech with a listener
            tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.UK)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("RegScreen", "Language not supported")
                    } else {
                        Log.d("RegScreen", "TextToSpeech initialized")

                        // Speak the welcome message
                        if (!hasSpokenWelcomeMessage) {
                            val welcomeMessage = "Welcome to the registration page. You can say 'name' followed by your full name, 'email' followed by your email address, 'emergency email' followed by a family or friend email address, 'password' followed by your password. To disable email or password say 'disable voice control'"
                            val utteranceId = "WELCOME_MESSAGE"

                            // Stop speech recognizer if it's listening to prevent conflicts
                            speechRecognizer.stopListening()

                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId == "WELCOME_MESSAGE") {
                                        // Start listening after welcome message is spoken
                                        Handler(Looper.getMainLooper()).post {
                                            if (reactiveVoiceControlEnabled) {
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
                    Log.e("RegScreen", "TextToSpeech initialization failed")
                }
            })

            val recognitionListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("RegScreen", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("RegScreen", "Beginning of speech")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // Log.d("RegScreen", "RMS changed: $rmsdB")
                }
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Log.d("RegScreen", "Buffer received")
                }
                override fun onEndOfSpeech() {
                    Log.d("RegScreen", "End of speech")
                }
                override fun onError(error: Int) {
                    Log.d("RegScreen", "Error: $error")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Log.d("RegScreen", "No match or speech timeout, providing feedback")

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
                                    if (utteranceId == "ERROR_MESSAGE" && reactiveVoiceControlEnabled) {
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
                            command.contains("email") -> {
                                email = command.replace(Regex(".*email"), "")
                                    .replace("dash", "-")
                                    .replace("underscore", "_")
                                    .replace("at", "@")
                                    .replace("art", "@")
                                    .replace("hat", "@")
                                    .replace("hart", "@")
                                    .replace(" ", "")
                                    .trim()
                                Log.d("RegScreen", "Updated email: $email")
                            }
                            command.contains("password") -> {
                                password = command.replace(Regex(".*password"), "")
                                    .replace("dash", "-")
                                    .replace("underscore", "_")
                                    .replace("at", "@")
                                    .replace("art", "@")
                                    .replace("hat", "@")
                                    .replace("hart", "@")
                                    .replace(" ", "")
                                    .trim()
                                Log.d("RegScreen", "Updated password: $password")
                            }
                            command.contains("name") || command.contains("full name") -> {
                                fullName = command.replace(Regex(".*name"), "")
                                    .replace(Regex(".*full name"), "")
                                    .replace(Regex(".*fullname"), "")
                                    .replace("full", "")
                                    .trim()
                                Log.d("RegScreen", "Full name: $fullName")
                            }
                            command.contains("emergency")-> {
                                emergencyContactEmail = command.replace(Regex(".*emergency"), "")
                                    .replace(Regex(".*parent"), "")
                                    .replace("dash", "-")
                                    .replace("underscore", "_")
                                    .replace("at", "@")
                                    .replace("art", "@")
                                    .replace("hat", "@")
                                    .replace("hart", "@")
                                    .replace(" ", "")
                                    .trim()
                                Log.d("RegScreen", "emergency email: $emergencyContactEmail")
                            }
                            command.contains("go back") || command.contains("previous page") -> {
                                navController.navigate("welcome_screen")
                            }
                            command.contains("disable voice control") || command.contains("disables voice control") || command.contains("disabled voice control") -> {
                                reactiveVoiceControlEnabled = false
                            }
                            command.contains("register") || command.contains("finished") || command.contains("done")  -> {
                                val emptyFields = mutableListOf<String>()
                                if (email.isEmpty()) emptyFields.add("email")
                                if (password.isEmpty()) emptyFields.add("password")
                                if (emergencyContactEmail.isEmpty()) emptyFields.add("emergencyContactEmail")
                                if (fullName.isEmpty()) emptyFields.add("fullName")

                                if (emptyFields.isNotEmpty()) {
                                    val message = "The following fields are empty: ${emptyFields.joinToString(", ")}"
                                    tts?.speak(
                                        message,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "EMPTY_FIELDS_MESSAGE"
                                    )
                                } else {
                                    viewModel.registerUser(
                                        email = email,
                                        fullname = fullName,
                                        password = password,
                                        emergencyEmail = emergencyContactEmail,
                                        voiceControl = reactiveVoiceControlEnabled
                                    )
                                }
                            }
                        }
                        // Restart listening after processing the command
                        speechRecognizer.startListening(speechIntent)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                }
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Log.d("RegScreen", "Event: $eventType")
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

    Scaffold(modifier = Modifier
        .fillMaxSize(),
        topBar = { RegisterScreenTopAppBar(navController)}) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            //Speak to register text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_start),
                        end = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_end),
                        top = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_top),
                        bottom = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_bottom)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_spacer_height)))

                Text(
                    text = stringResource(R.string.signInAndLoginSpeak),
                    fontFamily = gothamFonts,
                    fontSize = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_text_fontSize).value.sp,
                    fontWeight = FontWeight.Normal,
                )
                Row(modifier = Modifier.padding(top = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_row_topPadding))) {
                    Text(
                        text = stringResource(R.string.signInAndLoginSpeakNextContent),
                        textAlign = TextAlign.Center,
                        fontFamily = gothamFonts,
                        fontSize = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_row_text_fontSize).value.sp,
                        fontWeight = FontWeight.Light,
                        style = TextStyle(
                            lineHeight = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold2Column_row_text_lineHeight).value.sp
                        )
                    )
                }
            }

            //Speak to register actions
            Column(modifier = Modifier.fillMaxWidth().padding(top = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_paddingTop))) {

                //---------------Reactive Voice Control Start-------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_RowA)), // Add horizontal padding to center the content
                    verticalAlignment = Alignment.CenterVertically, // Center the content vertically
                    horizontalArrangement = Arrangement.Center // Center the content horizontally
                ) {
                    // Text for Reactive Voice Control
                    Text(
                        text = stringResource(R.string.signInReactiveVoiceControl),
                        fontFamily = gothamFonts,
                        fontSize = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_RowA_Text).value.sp,
                        )

                    // Spacer to add space between the text and the switch
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_RowA_Spacer)))

                    Switch(
                        checked = reactiveVoiceControlEnabled,
                        onCheckedChange = {
                            reactiveVoiceControlEnabled = it
                            if (it) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        thumbContent = if (reactiveVoiceControlEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
                //------------------Reactive Voice Control End----------------------

                //---------------------------Text Fields-------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_ColumnA)), // Add horizontal padding to center the column
                    horizontalAlignment = Alignment.CenterHorizontally // Center the content horizontally
                ) {
                    // Full Name
                    OutlinedTextField(
                        value = fullName,
                        label = {
                            Text(
                                text = stringResource(R.string.inputFieldFullName),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { fullName = it },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text), // Use text keyboard
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_ColumnA_TFPadding)) // Add vertical padding between fields
                    )

                    // Personal Email
                    OutlinedTextField(
                        value = email,
                        label = {
                            Text(
                                text = stringResource(R.string.inputFieldPersonalEmail),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { email = it },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_ColumnA_TFPadding)) // Add vertical padding between fields
                    )

                    // Emergency Contact Email
                    OutlinedTextField(
                        value = emergencyContactEmail,
                        label = {
                            Text(
                                text = stringResource(R.string.inputFieldEmergencyContact),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { emergencyContactEmail = it },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email), // Use email keyboard
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_ColumnA_TFPadding)) // Add vertical padding between fields
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        label = {
                            Text(
                                text = stringResource(R.string.inputFieldPassword),
                                fontFamily = gothamFonts
                            )
                        },
                        textStyle = TextStyle(fontFamily = gothamFonts),
                        onValueChange = { password = it },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password), // Use password keyboard
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), // Toggle password visibility
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Check else Icons.Filled.Lock
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = if (passwordVisible) stringResource(R.string.inputFieldPasswordHidePassword) else stringResource(R.string.inputFieldPasswordShowPassword))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold3Column_ColumnA_TFPadding)) // Add vertical padding between fields
                    )
                }
                //----------------------Text Fields End----------------------

                //----------------------Button Start----------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding)), // Add horizontal padding to center the column
                    horizontalAlignment = Alignment.CenterHorizontally // Center the content horizontally
                ) {
                    // Create an Account Button
                    Button(
                        onClick = {
                            viewModel.registerUser(
                                email = email,
                                fullname = fullName,
                                password = password,
                                emergencyEmail = emergencyContactEmail,
                                voiceControl = reactiveVoiceControlEnabled
                                )

                                  },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start= dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_paddingstart),
                                end= dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_paddingend),
                                top = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_paddingtop)
                            )
                            .padding(vertical = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_paddingvertical)), // Add vertical padding
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_elevation))

                    ) {
                        Text(
                            text = stringResource(R.string.ButtonAndExtraText),
                            fontFamily = gothamFonts,
                            fontSize = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_text_size).value.sp // Increase text size
                        )
                    }

                    // Spacer
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_spacer_height)))

                    // Instruction Text
                    Text(
                        text = stringResource(R.string.ButtonAndExtraText_Text),
                        textAlign = TextAlign.Center,
                        fontFamily = gothamFonts,
                        fontSize = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_end_text_fontSize).value.sp,
                        fontWeight = FontWeight.Light,
                        style = TextStyle(
                            lineHeight = dimensionResource(R.dimen.Register_Screen_RegisterScreen_Scaffold4Column_Padding_Button_end_text_lineHeight).value.sp
                        ),
                        modifier = Modifier.fillMaxWidth() // Center the text
                    )
                }
                //----------------------Button End----------------------

            }
            //Blank space at the bottom
            Column {

            }


            if (showAuthMessageCard) {
                when {
                    errorMessage != null -> {
                        AuthMessageCard(
                            header = "Error",
                            message = errorMessage!!,
                            onDismiss = {
                                showAuthMessageCard = false
                                viewModel.resetMessages()
                            }
                        )
                    }
                    successMessage != null -> {
                        AuthMessageCard(
                            header = "Success",
                            message = successMessage!!,
                            onDismiss = {
                                showAuthMessageCard = false
                                viewModel.resetMessages()
                            }
                        )
                    }
                }
            }



        }
    }


    LaunchedEffect(registerSuccess) {
        if (registerSuccess == true) {
            viewModel.logLocalStorageData()
            // Clean up text to speech resources
            speechRecognizer.destroy()
            tts?.stop()
            tts?.shutdown()

            // Navigate to home_screen
            navController.navigate("home_screen")

            viewModel.resetRegisterSuccess()
        }
    }


}

@Preview
@Composable
fun RegisterScreenPreview(){
    val navController = rememberNavController()
    HimbaVisionTheme {
        RegisterScreen(navController)
    }
}




