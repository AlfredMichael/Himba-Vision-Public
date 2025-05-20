package ie.tus.himbavision.ui.bottomappbar

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person3
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import ie.tus.himbavision.ui.SplashScreen
import ie.tus.himbavision.ui.theme.HimbaVisionTheme

@Composable
fun CustomBottomAppBar(navController: NavController, selectedScreen:String) {
    val isDarkTheme = isSystemInDarkTheme()
    val selectedColor = if (isDarkTheme) Color.Black else Color.White

    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {navController.navigate("home_screen")},
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selectedScreen == "home_screen") selectedColor else Color.Transparent)

            ) {
                Icon(Icons.Filled.Search, contentDescription = "Object Finder")

            }
            IconButton(onClick = {navController.navigate("himba_screen")},
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selectedScreen == "himba_screen") selectedColor else Color.Transparent)

            ) {
                Icon(Icons.Filled.Route, contentDescription = "Contextually Aware Navigator")
            }
            //profile_screen

            IconButton(onClick = {navController.navigate("profile_screen")},
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selectedScreen == "profile_screen") selectedColor else Color.Transparent)

            ) {
                Icon(Icons.Filled.Person3, contentDescription = "My Profile")
            }

        }
    }
}



@Preview
@Composable
fun CustomBottomAppBarDarkPreview(){
    val navController = rememberNavController()
    HimbaVisionTheme(darkTheme = true) {
        CustomBottomAppBar(navController,"himba_screen")
    }
}
