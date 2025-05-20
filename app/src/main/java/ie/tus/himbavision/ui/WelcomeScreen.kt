package ie.tus.himbavision.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.R
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.ui.theme.gothamFonts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.coroutines.delay
import java.util.*
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import ie.tus.himbavision.ui.helperFunctions.handleUnrecognizedCommand


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreenTopAppBar(){
    val isDarkTheme = isSystemInDarkTheme()
    val imageResource = if (isDarkTheme) R.drawable.himbaiconlight else R.drawable.himbaicon

    // Top App Bar
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = imageResource),
                    contentDescription = stringResource(R.string.brand_icon),
                    modifier = Modifier.size(dimensionResource(R.dimen.Welcome_Screen_TopAppBar_size))
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.Welcome_Screen_TopAppBar_spacer)))
                Text(
                    text= stringResource(R.string.welcome_text),
                    fontFamily = gothamFonts,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        modifier = Modifier.wrapContentHeight()

    )
}





@Composable
fun WelcomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
        }
    }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        permissionGranted = isGranted
    }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                val voices = tts?.voices
                val ukVoice = voices?.find { it.locale == Locale.UK }

                if (ukVoice != null) {
                    tts?.voice = ukVoice
                }
                audioManager.setMicrophoneMute(true)
                tts?.speak("Welcome to Himba Vision. Please say 'sign in' to log in, or 'register' to create an account.", TextToSpeech.QUEUE_FLUSH, null, "WELCOME_MESSAGE")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        audioManager.setMicrophoneMute(false)
                    }
                    override fun onError(utteranceId: String?) {
                        audioManager.setMicrophoneMute(false)
                    }
                })
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = { WelcomeScreenTopAppBar() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Register Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_padding))
                    .clickable { navController.navigate("register_screen") }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.welcomeregister),
                    contentDescription = stringResource(R.string.register_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = stringResource(R.string.register_text),
                    fontFamily = gothamFonts,
                    fontSize = dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_text).value.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_text_padding))
                )
            }

            // SignIn Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { navController.navigate("signIn_screen") }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.welcomelogin),
                    contentDescription = stringResource(R.string.signIn_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = stringResource(R.string.signIn_text),
                    fontFamily = gothamFonts,
                    fontSize = dimensionResource(R.dimen.Welcome_Screen_SignInSectionBox_text).value.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(dimensionResource(R.dimen.Welcome_Screen_SignInSectionBox_text_padding))
                )
            }
        }
    }

    // Request permission and start speech recognition if granted
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            delay(7000) // Adjust the delay as needed
            speechRecognizer.startListening(speechIntent)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Set up the recognition listener
    DisposableEffect(Unit) {
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speechRecognizer.startListening(speechIntent)
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    val command = it[0].toLowerCase(Locale.ROOT)
                    val signInKeywords = listOf("sign in", "sign-in", "sign me in", "log in", "login", "signing")
                    val registerKeywords = listOf("register", "create account", "register me up", "create an account", "create my account", "sign up")

                    when {
                        signInKeywords.any { keyword -> keyword in command } -> navController.navigate("signIn_screen")
                        registerKeywords.any { keyword -> keyword in command } -> navController.navigate("register_screen")
                        else -> handleUnrecognizedCommand(context, command, { navController.navigate(it) }, speechIntent, tts)
                    }
                } ?: handleUnrecognizedCommand(context, "", { navController.navigate(it) }, speechIntent, tts)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose {
            speechRecognizer.destroy()
        }
    }
}



//@Composable
//fun WelcomeScreen(navController: NavHostController) {
//    Scaffold(modifier = Modifier
//        .fillMaxSize(),
//        topBar = {WelcomeScreenTopAppBar()}
//
//    ) { innerPadding ->
//
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//                .verticalScroll(rememberScrollState())
//        ) {
//            // Register Section
//            Box(
//                modifier = Modifier
//                    .weight(1f)
//                    .fillMaxWidth()
//                    .padding(bottom = dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_padding))
//                    .clickable { navController.navigate("register_screen")  }
//            ) {
//                Image(
//                    painter = painterResource(id = R.drawable.welcomeregister),
//                    contentDescription = stringResource(R.string.register_image),
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//                Text(
//                    text = stringResource(R.string.register_text),
//                    fontFamily = gothamFonts,
//                    fontSize = dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_text).value.sp,
//                    fontWeight = FontWeight.Bold,
//                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .padding(dimensionResource(R.dimen.Welcome_Screen_RegisterSectionBox_text_padding))
//                )
//            }
//
//            // SignIn Section
//            Box(
//                modifier = Modifier
//                    .weight(1f)
//                    .fillMaxWidth()
//                    .clickable { navController.navigate("signIn_screen") }
//            ) {
//                Image(
//                    painter = painterResource(id = R.drawable.welcomelogin),
//                    contentDescription = stringResource(R.string.signIn_image),
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//                Text(
//                    text = stringResource(R.string.signIn_text),
//                    fontFamily = gothamFonts,
//                    fontSize = dimensionResource(R.dimen.Welcome_Screen_SignInSectionBox_text).value.sp,
//                    fontWeight = FontWeight.Bold,
//                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .padding(dimensionResource(R.dimen.Welcome_Screen_SignInSectionBox_text_padding))
//                )
//            }
//        }
//    }
//}
//


@Preview
@Composable
fun WelcomeScreenPreview(){
    val navController = rememberNavController()
    HimbaVisionTheme {
        WelcomeScreen(navController)
    }
}