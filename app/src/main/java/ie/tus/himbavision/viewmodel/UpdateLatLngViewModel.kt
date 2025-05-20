package ie.tus.himbavision.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import ie.tus.himbavision.utility.Network.isOnline

class UpdateLatLngViewModel : ViewModel() {
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance("Firebase-DB-URL")
    private val usersRef: DatabaseReference = firebaseDatabase.getReference("users")

    val errorMessage = MutableLiveData<String?>()

    fun updateLatLng(email: String, latLng: String, context: Context) {
        if (isOnline(context)) {
            usersRef.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (userSnapshot in dataSnapshot.children) {
                            userSnapshot.ref.child("lastKnownLocation").setValue(latLng)
                            Log.d("lastKnownLocation", "Updated successfully")
                        }
                    } else {
                        errorMessage.value = "User not found"
                        Log.d("lastKnownLocation", "not Updated successfully")
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    errorMessage.value = databaseError.message
                    Log.d("lastKnownLocation", databaseError.message)
                }
            })

        }else{
            //Do nothing or maybe store it locally but tbh there is no need, we cant do anything with
            // the location data stored locally and by the way we can always retrieve it at anytime
        }

    }
}
