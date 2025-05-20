package ie.tus.himbavision.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper

class AuthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(SecureStorageHelper(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

