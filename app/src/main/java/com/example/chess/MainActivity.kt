package com.example.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.data.auth.AuthRepository
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.model.AuthState
import com.example.chess.ui.navigation.ChessOsRoot
import com.example.chess.ui.theme.ChessTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var chessRepository: ChessRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val snap by chessRepository.snapshot.collectAsStateWithLifecycle(
                com.example.chess.domain.model.ChessSnapshot(),
            )
            val auth by authRepository.state.collectAsStateWithLifecycle(AuthState.Loading)
            val signedIn = auth is AuthState.SignedIn || snap.profile != null
            ChessTheme(themeMode = snap.profile?.theme ?: com.example.chess.domain.model.ThemeMode.SYSTEM) {
                ChessOsRoot(signedIn = signedIn)
            }
        }
    }
}
