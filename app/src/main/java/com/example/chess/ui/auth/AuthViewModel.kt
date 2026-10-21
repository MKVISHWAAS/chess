package com.example.chess.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.BuildConfig
import com.example.chess.data.auth.AuthRepository
import com.example.chess.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    val state = auth.authState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val googleEnabled: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun signIn(email: String, password: String) = launch { auth.signInEmail(email, password) }
    fun register(email: String, password: String, name: String) = launch { auth.registerEmail(email, password, name) }
    fun google(idToken: String) = launch { auth.signInGoogle(idToken) }
    fun guest() = launch { auth.signInGuest() }
    fun signOut() = launch { auth.signOut() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _error.value = it.message }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
