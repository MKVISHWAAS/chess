package com.example.chess.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.auth.AuthRepository
import com.example.chess.data.engine.ChessEngine
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.model.ThemePreference
import com.example.chess.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val auth: AuthRepository,
    engine: ChessEngine,
) : ViewModel() {
    val profile = repository.signedIn.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile("", null, "Player"))
    val engineName: String = engine.name

    fun update(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            val current = profile.value
            if (current.uid.isBlank()) return@launch
            repository.saveProfile(transform(current))
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun setTheme(theme: ThemePreference) = update { it.copy(theme = theme) }
}
