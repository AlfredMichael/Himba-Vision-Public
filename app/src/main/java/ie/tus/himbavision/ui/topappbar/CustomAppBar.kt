package ie.tus.himbavision.ui.topappbar

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ie.tus.himbavision.R
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.ui.theme.gothamFonts
import ie.tus.himbavision.viewmodel.AuthViewModel
import ie.tus.himbavision.viewmodel.AuthViewModelFactory
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModel
import ie.tus.himbavision.viewmodel.UpdateVoiceControlViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable

fun CustomAppBar(
    navController: NavController,
    titleText: String,
    context: Context,
    isMicEnabled: Boolean,
    onMicToggle: (Boolean) -> Unit){

    val secureStorageHelper = SecureStorageHelper(context)
    val user = secureStorageHelper.getUser()

    //Offline and Online voice control
    val voiceViewModel: UpdateVoiceControlViewModel = viewModel(factory = UpdateVoiceControlViewModelFactory(context))

    //Firebase
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    viewModel.logoutUser()
                    navController.navigate("welcome_screen") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.LogOutDescription)
                    )
                }


                Spacer(modifier = Modifier.weight(1f))
                // Home Screen text
                Text(
                    text = titleText,
                    fontFamily = gothamFonts,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val newMicState = !isMicEnabled
                    onMicToggle(newMicState)
                    voiceViewModel.updateVoiceControl(user?.email ?: "", newMicState, context)
                }) {
                    Icon(
                        imageVector = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = stringResource(R.string.EveryScreenIconDescription)
                    )
                }
            }
        },
        modifier = Modifier.wrapContentHeight().padding(dimensionResource(R.dimen.Home_Screen_TopAppBar_padding))
    )

}