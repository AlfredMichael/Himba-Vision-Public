package ie.tus.himbavision.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import ie.tus.himbavision.dataclasses.User
import ie.tus.himbavision.dataclasses.UserForFirebase
import ie.tus.himbavision.localsecurestorage.SecureStorageHelper
import ie.tus.himbavision.utility.Network.isOnline

class AuthViewModel(private val secureStorageHelper: SecureStorageHelper) : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase: FirebaseDatabase = FirebaseDatabase.getInstance("Firebase-DB-URL")

    // LiveData to hold the error message
    val errorMessage = MutableLiveData<String?>()

    // LiveData to hold the success message
    val successMessage = MutableLiveData<String?>()

    // LiveData to hold the loading state
    val isLoading = MutableLiveData<Boolean>()

    // LiveData to hold the user's id
    val userId = MutableLiveData<String>()

    // LiveData to indicate login success
    val loginSuccess = MutableLiveData<Boolean>()

    // LiveData to indicate register success
    val registerSuccess = MutableLiveData<Boolean>()

    // Method to register a new user
    fun registerUser(fullname: String, email: String, password: String, emergencyEmail: String, voiceControl: Boolean) {
        isLoading.value = true

        if (fullname.isEmpty() || email.isEmpty() || password.isEmpty() || emergencyEmail.isEmpty()) {
            errorMessage.value = "All fields are required."
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val userId = firebaseUser?.uid

                    if (userId != null) {
                        //User object without password for Firebase
                        val userForFirebase = UserForFirebase(
                            fullname = fullname,
                            email = email,
                            emergencyEmail = emergencyEmail,
                            voiceControl = voiceControl
                        )

                        //User object with password for local storage
                        val userForLocalStorage = User(
                            fullname = fullname,
                            email = email,
                            password = password,
                            emergencyEmail = emergencyEmail,
                            voiceControl = voiceControl
                        )

                        val userRef = firebaseDatabase.reference.child("users").child(userId)
                        userRef.setValue(userForFirebase)
                            .addOnCompleteListener { dbTask ->
                                isLoading.value = false
                                if (dbTask.isSuccessful) {
                                    successMessage.value = "User registered successfully"
                                    registerSuccess.value = true
                                    this.userId.value = userId

                                    // Save user details to the secure local storage
                                    secureStorageHelper.saveUser(userForLocalStorage)
                                } else {
                                    errorMessage.value = dbTask.exception?.message
                                    registerSuccess.value = false
                                }
                            }
                    } else {
                        isLoading.value = false
                        errorMessage.value = "User ID is null"
                        registerSuccess.value = false
                    }
                } else {
                    isLoading.value = false
                    errorMessage.value = task.exception?.message
                    registerSuccess.value = false
                }
            }
    }

    // Method to log in a user
    fun loginUser(email: String, password: String, context: Context) {
        isLoading.value = true

        // Check if user data exists in local storage
        val localUser = secureStorageHelper.getUser()
        if (localUser != null && localUser.email == email && localUser.password == password) {

            if (isOnline(context)) {
                Log.d("SecureStorageHelper", "Device is online, attempting Firebase authentication")
                // Auto-authenticate the user
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        isLoading.value = false
                        if (task.isSuccessful) {
                            loginSuccess.value = true
                            this.userId.value = auth.currentUser?.uid
                            successMessage.value = "User logged in successfully"
                            Log.d("SecureStorageHelper", "Authenticated local credentials")
                        } else {
                            errorMessage.value = task.exception?.message
                            loginSuccess.value = false
                        }
                    }

            }else{
                Log.d("SecureStorageHelper", "Device is offline.")
                isLoading.value = false
                loginSuccess.value = true
                successMessage.value = "User logged in successfully (offline mode)"

                Log.d("SecureStorageHelper", "offline mode activated")

            }

        } else {
            if (email.isEmpty() || password.isEmpty()) {
                errorMessage.value = "All fields are required."
                return
            }

            // Authenticate the user and retrieve data from Firebase
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val userId = firebaseUser?.uid

                        if (userId != null) {
                            val userRef = firebaseDatabase.reference.child("users").child(userId)
                            userRef.get().addOnCompleteListener { dbTask ->
                                isLoading.value = false
                                if (dbTask.isSuccessful) {
                                    val user = dbTask.result.getValue(User::class.java)
                                    if (user != null) {

                                        val userForLocalStorage = User(
                                            fullname = user.fullname,
                                            email = user.email,
                                            password = password, // Use the entered password
                                            emergencyEmail = user.emergencyEmail,
                                            voiceControl = user.voiceControl,
                                            lastKnownLocation = user.lastKnownLocation
                                        )

                                        // Save user details to the secure local storage
                                        secureStorageHelper.saveUser(userForLocalStorage)
                                        loginSuccess.value = true
                                        this.userId.value = userId
                                        successMessage.value = "User logged in successfully"
                                    } else {
                                        errorMessage.value = "User data not found"
                                        loginSuccess.value = false
                                    }
                                } else {
                                    errorMessage.value = dbTask.exception?.message
                                    loginSuccess.value = false
                                }
                            }
                        } else {
                            isLoading.value = false
                            errorMessage.value = "User ID is null"
                            loginSuccess.value = false
                        }
                    } else {
                        isLoading.value = false
                        errorMessage.value = task.exception?.message
                        loginSuccess.value = false
                    }
                }
        }
    }

    fun resetMessages() {
        errorMessage.postValue(null)
        successMessage.postValue(null)
    }

    fun resetRegisterSuccess() {
        registerSuccess.postValue(false)
    }

    fun resetLoginSuccess() {
        loginSuccess.postValue(false)
    }

    fun autoLogin(context: Context) {
        val localUser = secureStorageHelper.getUser()
        if (localUser != null) {
            loginUser(localUser.email, localUser.password, context)
        }
    }


    // Method to log unencrypted data from local storage
    fun logLocalStorageData() {
        secureStorageHelper.logUnencryptedData()
    }

    // Method to log out a user
    fun logoutUser() {
        auth.signOut()
        secureStorageHelper.clearUser()
        successMessage.value = "User logged out successfully"
    }
}
