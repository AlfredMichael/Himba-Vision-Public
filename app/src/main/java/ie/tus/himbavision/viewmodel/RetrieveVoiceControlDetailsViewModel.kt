package ie.tus.himbavision.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.utility.Network.isOnline

class RetrieveVoiceControlDetailsViewModel(private val secureStorageHelper: SecureStorageHelper) : ViewModel() {
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance("Firebase-DB-URL")
    private val usersRef: DatabaseReference = firebaseDatabase.getReference("users")

    val voiceControlState = MutableLiveData<Boolean?>()
    val errorMessage = MutableLiveData<String?>()

    fun retrieveVoiceControl(email: String, context: Context) {
        if (isOnline(context)) {
            usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (userSnapshot in dataSnapshot.children) {
                            val voiceControl = userSnapshot.child("voiceControl").getValue(Boolean::class.java)
                            if (voiceControl == true || voiceControl == false) {
                                voiceControlState.value = voiceControl
                            } else {
                                Log.d("RetrieveVoiceControl", "value is not true or false")
                            }
                        }
                    } else {
                        errorMessage.value = "User not found"
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    errorMessage.value = databaseError.message
                }
            })
        } else {
            val user = secureStorageHelper.getUser()
            if (user != null && user.email == email) {
                val voiceControl = user.voiceControl
                if (voiceControl == true || voiceControl == false) {
                    voiceControlState.value = voiceControl
                } else {
                    Log.d("RetrieveVoiceControl", "value is not true or false")
                }
            } else {
                errorMessage.value = "User not found"
            }
        }
    }

}
