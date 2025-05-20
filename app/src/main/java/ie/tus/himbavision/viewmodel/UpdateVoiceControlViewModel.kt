package ie.tus.himbavision.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.utility.Network.isOnline

class UpdateVoiceControlViewModel(private val secureStorageHelper: SecureStorageHelper) : ViewModel() {
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance("Firebase-DB-URL")
    private val usersRef: DatabaseReference = firebaseDatabase.getReference("users")

    val errorMessage = MutableLiveData<String?>()

    fun updateVoiceControl(email: String, voiceControl: Boolean, context: Context) {
        if (isOnline(context)) {
            usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (userSnapshot in dataSnapshot.children) {
                            userSnapshot.ref.child("voiceControl").setValue(voiceControl)
                        }
                        updateLocalStorage(email, voiceControl)
                    } else {
                        errorMessage.value = "User not found"
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    errorMessage.value = databaseError.message
                }
            })
        } else {
            updateLocalStorage(email, voiceControl)
        }
    }

    private fun updateLocalStorage(email: String, voiceControl: Boolean) {
        val user = secureStorageHelper.getUser()
        if (user != null && user.email == email) {
            val updatedUser = user.copy(voiceControl = voiceControl)
            secureStorageHelper.saveUser(updatedUser)
        }
    }
}

