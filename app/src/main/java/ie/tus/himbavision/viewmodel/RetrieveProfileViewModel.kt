package ie.tus.himbavision.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.FirebaseDatabase
import androidx.lifecycle.LiveData
import com.google.firebase.database.*
import ie.tus.himbavision.dataclasses.User
import ie.tus.himbavision.utility.Network.isOnline

class RetrieveProfileViewModel : ViewModel() {
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance("Firebase-DB-URL")
    private val usersRef: DatabaseReference = firebaseDatabase.getReference("users")

    // LiveData to hold the user details
    private val _userDetails = MutableLiveData<User?>()
    val userDetails: LiveData<User?> get() = _userDetails

    // LiveData to hold the error message
    val errorMessage = MutableLiveData<String?>()

    fun getUserByEmail(email: String, context: Context) {
        if (isOnline(context)) {
            usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (userSnapshot in dataSnapshot.children) {
                            val user = userSnapshot.getValue(User::class.java)
                            _userDetails.value = user
                            return
                        }
                    } else {
                        errorMessage.value = "User not found"
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    errorMessage.value = databaseError.message
                }
            })
        }else{
            errorMessage.value = "Please ensure you are connected to the to the internet to view your profile"

        }

    }
}
