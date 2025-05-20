package ie.tus.himbavision.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.AppNavigation
import ie.tus.himbavision.R
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.ui.theme.gothamFonts
import kotlinx.coroutines.delay


@Composable
fun AnimatedWiFiSignalSymbol() {
    var currentArc by remember { mutableIntStateOf(0) }
    val isDarkTheme = isSystemInDarkTheme()
    val activeColor = if (isDarkTheme) Color.White else Color.Black

    val arcColors = listOf(
        animateColorAsState(if (currentArc >= 3) activeColor else Color.Gray, label = "Arc 1 Color").value,
        animateColorAsState(if (currentArc >= 2) activeColor else Color.Gray, label = "Arc 2 Color").value,
        animateColorAsState(if (currentArc >= 1) activeColor else Color.Gray, label = "Arc 3 Color").value,
        animateColorAsState(if (currentArc >= 0) activeColor else Color.Gray, label = "Arc 4 Color").value
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // Delay between color changes
            currentArc = (currentArc + 1) % 4
        }
    }

    Canvas(modifier = Modifier
        .size(150.dp)
        .graphicsLayer(rotationZ = 180f)) { // Rotate the symbol 180 degrees
        val width = size.width
        val height = size.height

        // Draw the WiFi signal arcs with animated colors
        drawArc(
            color = arcColors[0],
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * 0.1f, height * 0.5f),
            size = androidx.compose.ui.geometry.Size(width * 0.8f, height * 0.4f),
            style = Stroke(width = 8f)
        )
        drawArc(
            color = arcColors[1],
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * 0.2f, height * 0.6f),
            size = androidx.compose.ui.geometry.Size(width * 0.6f, height * 0.3f),
            style = Stroke(width = 8f)
        )
        drawArc(
            color = arcColors[2],
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * 0.3f, height * 0.7f),
            size = androidx.compose.ui.geometry.Size(width * 0.4f, height * 0.2f),
            style = Stroke(width = 8f)
        )
        drawArc(
            color = arcColors[3],
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * 0.4f, height * 0.8f),
            size = androidx.compose.ui.geometry.Size(width * 0.2f, height * 0.1f),
            style = Stroke(width = 8f)
        )
    }
}

@Composable
fun SplashScreen(navController: NavHostController) {
    val isDarkTheme = isSystemInDarkTheme()
    val imageResource = if (isDarkTheme) R.drawable.himbaiconlight else R.drawable.himbaicon

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())) {
            // First Column (70% of the screen)
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Image
                Image(
                    painter = painterResource(id = imageResource),
                    contentDescription = "Himba Vision Logo",
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.Splash_Screen_logo_size))
                )

                // Add Animated WiFi Signal Symbol below the image
                AnimatedWiFiSignalSymbol()


            }

            // Second Column (30% of the screen)
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.brand_name),
                    fontSize = dimensionResource(R.dimen.Splash_Screen_brand_name_font_size).value.sp,
                    fontFamily = gothamFonts,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .padding(
                            top = dimensionResource(
                                R.dimen.Splash_Screen_tagline_padding_top
                            ),
                            start = dimensionResource(
                                R.dimen.Splash_Screen_tagline_padding_side
                            ),
                            end = dimensionResource(
                                R.dimen.Splash_Screen_tagline_padding_side
                            )
                        )
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.tagline),
                        fontSize = dimensionResource(R.dimen.Splash_Screen_tagline_font_size).value.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontFamily = gothamFonts,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            }

        }
    }


    // Navigate to WelcomeScreen after a delay
    LaunchedEffect(Unit) {
        delay(2000) // 2 seconds delay
        navController.navigate("welcome_screen") {
            popUpTo("splash_screen") { inclusive = true }
        }
    }

}


//Light UI Preview
@Preview
@Composable
fun SplashScreenLightPreview(){
    val navController = rememberNavController()
    HimbaVisionTheme(darkTheme = false) {
        SplashScreen(navController)
    }
}


//Dark UI Preview
@Preview
@Composable
fun SplashScreenDarkPreview(){
    val navController = rememberNavController()
    HimbaVisionTheme(darkTheme = true) {
        SplashScreen(navController)
    }
}


