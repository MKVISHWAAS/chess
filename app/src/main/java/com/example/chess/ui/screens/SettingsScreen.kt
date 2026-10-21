package com.example.chess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.chess.data.auth.AuthRepository
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.engine.StockfishEngine
import com.example.chess.domain.model.EngineBackend
import com.example.chess.domain.model.ThemeMode
import com.example.chess.domain.model.UserProfile
import com.example.chess.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val auth: AuthRepository,
    private val stockfish: StockfishEngine,
) : ViewModel() {
    val snapshot = repository.snapshot
    val stockfishReady get() = stockfish.isAvailable
    var message by mutableStateOf<String?>(null)

    fun save(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            message = "Saved"
        }
    }

    fun signOut() = auth.signOut()
}

@Composable
fun SettingsScreen(onSignedOut: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    val profile = snap.profile ?: UserProfile(uid = "local")
    var name by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.displayName) }
    var chessCom by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.chessComUsername) }
    var lichess by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.lichessUsername) }
    var depth by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.engineDepth.toFloat()) }
    var timeMs by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.engineMoveTimeMs.toFloat()) }
    var skill by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.skillLevel.toFloat()) }
    var theme by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.theme) }
    var backend by androidx.compose.runtime.remember(profile) { mutableStateOf(profile.engineBackend) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Profile & settings")
        Text(profile.email.ifBlank { "Offline profile" })
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(chessCom, { chessCom = it }, label = { Text("Chess.com") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lichess, { lichess = it }, label = { Text("Lichess") }, modifier = Modifier.fillMaxWidth())
        Text("Theme")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach {
                FilterChip(selected = theme == it, onClick = { theme = it }, label = { Text(it.name) })
            }
        }
        Text("Engine ${if (vm.stockfishReady) "Stockfish binary found" else "using builtin Kotlin search (add assets/stockfish for NNUE)"}")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineBackend.entries.forEach {
                FilterChip(selected = backend == it, onClick = { backend = it }, label = { Text(it.name) })
            }
        }
        Text("Depth ${depth.toInt()}")
        Slider(depth, { depth = it }, valueRange = 2f..12f)
        Text("Move time ${timeMs.toInt()} ms")
        Slider(timeMs, { timeMs = it }, valueRange = 100f..3000f)
        Text("Play skill ${skill.toInt()}")
        Slider(skill, { skill = it }, valueRange = 1f..20f)
        Button({
            vm.save(
                profile.copy(
                    displayName = name,
                    chessComUsername = chessCom,
                    lichessUsername = lichess,
                    theme = theme,
                    engineDepth = depth.toInt(),
                    engineMoveTimeMs = timeMs.toInt(),
                    engineBackend = backend,
                    skillLevel = skill.toInt(),
                ),
            )
        }) { Text("Save") }
        vm.message?.let { Text(it) }
        Button({
            vm.signOut()
            onSignedOut()
        }) { Text("Sign out") }
    }
}
