package ie.tus.himbavision.utility.Permissions

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun RequestPermissions(content: @Composable () -> Unit) {
    // State variables to track whether the permissions have been granted or not
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var fineLocationPermissionGranted by remember { mutableStateOf(false) }
    var coarseLocationPermissionGranted by remember { mutableStateOf(false) }
    var backgroundLocationPermissionGranted by remember { mutableStateOf(false) }

    // Gets the current activity context, so we can exit the app if the permissions are denied
    val context = LocalContext.current as Activity

    // Create launcher for permission requests, after completing either update the state variables or exit the application entirely
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraPermissionGranted = true
        } else {
            // If permission is denied, exit the app
            context.finish()
        }
    }

    val fineLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fineLocationPermissionGranted = true
        } else {
            // If permission is denied, exit the app
            context.finish()
        }
    }

    val coarseLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            coarseLocationPermissionGranted = true
        } else {
            // If permission is denied, exit the app
            context.finish()
        }
    }

    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            backgroundLocationPermissionGranted = true
        } else {
            // If permission is denied, exit the app
            context.finish()
        }
    }

    // Request permission on first composition
    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Chained location permissions after camera permission has been granted
    LaunchedEffect(cameraPermissionGranted) {
        if (cameraPermissionGranted) {
            fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(fineLocationPermissionGranted) {
        if (fineLocationPermissionGranted) {
            coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(coarseLocationPermissionGranted) {
        if (coarseLocationPermissionGranted) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // Display the content only if all the permissions have been granted
    if (cameraPermissionGranted && fineLocationPermissionGranted && coarseLocationPermissionGranted && backgroundLocationPermissionGranted) {
        content()
    }
}
