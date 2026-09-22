package com.myapps.onlineexaminationapps.ui.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SignUpViewModel : ViewModel() {

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val _signUpSuccess = MutableSharedFlow<Boolean>()
    val signUpSuccess = _signUpSuccess.asSharedFlow()

    fun onEmailChange(newValue: String) {
        email = newValue
    }

    fun onPasswordChange(newValue: String) {
        password = newValue
    }

    fun signUp(isTeacher: Boolean) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            errorMessage = "Email and password cannot be empty"
            return
        }

        isLoading = true
        errorMessage = null

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(trimmedEmail, trimmedPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    if (firebaseUser != null) {
                        val user = User(
                            uid = firebaseUser.uid,
                            email = trimmedEmail,
                            role = if (isTeacher) "teacher" else "student"
                        )
                        FirebaseFirestore.getInstance().collection("users")
                            .document(firebaseUser.uid)
                            .set(user)
                            .addOnCompleteListener { firestoreTask ->
                                isLoading = false
                                if (firestoreTask.isSuccessful) {
                                    viewModelScope.launch {
                                        _signUpSuccess.emit(true)
                                    }
                                } else {
                                    errorMessage = firestoreTask.exception?.message ?: "Failed to save user role"
                                }
                            }
                    } else {
                        isLoading = false
                        errorMessage = "User creation failed"
                    }
                } else {
                    isLoading = false
                    errorMessage = task.exception?.message ?: "Registration failed"
                }
            }
    }
}
