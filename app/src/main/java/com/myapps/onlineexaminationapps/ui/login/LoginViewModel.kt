package com.myapps.onlineexaminationapps.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.firebase.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val _loginSuccess = MutableSharedFlow<String>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    fun onEmailChange(newValue: String) {
        email = newValue
    }

    fun onPasswordChange(newValue: String) {
        password = newValue
    }

    fun login() {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            errorMessage = "Email and password cannot be empty"
            return
        }

        isLoading = true
        errorMessage = null

        FirebaseAuth.getInstance().signInWithEmailAndPassword(trimmedEmail, trimmedPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid
                    if (uid != null) {
                        viewModelScope.launch {
                            val role = authRepository.getUserRole(uid)
                            isLoading = false
                            val finalRole = role ?: "student"
                            Log.d("AuthDebug", "Login successful for uid $uid with role: $finalRole")
                            _loginSuccess.emit(finalRole)
                        }
                    } else {
                        isLoading = false
                        errorMessage = "Login failed: User data not found"
                    }
                } else {
                    isLoading = false
                    errorMessage = task.exception?.message ?: "Login failed"
                }
            }
    }
}
