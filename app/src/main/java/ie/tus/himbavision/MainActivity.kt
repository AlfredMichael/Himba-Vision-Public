package ie.tus.himbavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.jnibridge.HimbaJNIBridge
import ie.tus.himbavision.ui.HimbaNavScreen
import ie.tus.himbavision.ui.HomeScreen
import ie.tus.himbavision.ui.ProfileScreen
import ie.tus.himbavision.ui.RegisterScreen
import ie.tus.himbavision.ui.SignInScreen
import ie.tus.himbavision.ui.SplashScreen
import ie.tus.himbavision.ui.WelcomeScreen
import ie.tus.himbavision.ui.theme.HimbaVisionTheme
import ie.tus.himbavision.utility.Permissions.RequestPermissions

class MainActivity : ComponentActivity() {

    private val nanodetncnn = HimbaJNIBridge()
    private val facing = mutableStateOf(1)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HimbaVision)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HimbaVisionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                   PermissionHandler{
                        AppNavigation()
                   }
                    //TestFrames()
                    //HomeScreen(navController)
                    //HimbaNavScreen(navController)
                   // AppNavigation()
                }
            }
        }
    }
}

@Composable
fun PermissionHandler(content: @Composable () -> Unit) {
    RequestPermissions {
        content()
    }
}


@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "splash_screen") {
        composable("splash_screen") { SplashScreen(navController) }
        composable("welcome_screen") { WelcomeScreen(navController) }
        composable("register_screen") { RegisterScreen(navController) }
        composable("signIn_screen") { SignInScreen(navController) }
        composable("home_screen") { HomeScreen(navController) }
        composable("himba_screen") { HimbaNavScreen(navController) }
        composable("profile_screen") { ProfileScreen(navController) }

    }
}
