package ie.tus.himbavision.utility.Network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


class ConnectivityReceiver(context: Context) : BroadcastReceiver() {

    // LiveData to observe the connectivity status as a String ("connected" or "disconnected")
    private val _isConnected = MutableLiveData<String>()
    val isConnected: LiveData<String> get() = _isConnected

    init {
        // Create an intent filter to listen for connectivity changes
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        // Register this BroadcastReceiver to listen for connectivity changes
        context.registerReceiver(this, filter)
        // Initialize the connection status when the object is created
        updateConnectionStatus(context)
    }

    // This method is called whenever a broadcast matching the intent filter is received
    override fun onReceive(context: Context, intent: Intent) {
        // Update the connection status whenever a connectivity change is detected
        updateConnectionStatus(context)
    }

    // Method to check and update the current network connection status
    private fun updateConnectionStatus(context: Context) {
        // Get the ConnectivityManager system service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check network status based on the API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For API level 23 (Marshmallow) and above, use the Network and NetworkCapabilities classes
            val network = connectivityManager.activeNetwork // Get the active network
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) // Get its capabilities
            _isConnected.postValue(
                when {
                    // Check if the network has WiFi transport capability
                    activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "connected"
                    // Check if the network has cellular transport capability
                    activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "connected"
                    // Check if the network has Ethernet transport capability
                    activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "connected"
                    // If none of the above conditions match, set status to "disconnected"
                    else -> "disconnected"
                }
            )
        } else {
            // For older API levels, use the deprecated activeNetworkInfo
            val networkInfo = connectivityManager.activeNetworkInfo // Get the network info
            // Update connection status based on whether the network is connected
            _isConnected.postValue(if (networkInfo?.isConnected == true) "connected" else "disconnected")
        }
    }
}
