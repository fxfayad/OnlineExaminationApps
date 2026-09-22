package com.myapps.onlineexaminationapps.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapps.onlineexaminationapps.firebase.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    init {
        checkAuth()
    }

    private fun checkAuth() {
        val user = repository.getCurrentUser()
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            viewModelScope.launch {
                val role = repository.getUserRole(user.uid)
                val finalRole = role ?: "student"
                _authState.value = AuthState.Authenticated(finalRole)
            }
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val role: String) : AuthState()
    object Unauthenticated : AuthState()
}
