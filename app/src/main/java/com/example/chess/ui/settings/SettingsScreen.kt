package com.example.chess.ui.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.domain.model.ThemePreference

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Profile & engine")
        Text("Active engine: ${vm.engineName}")
        OutlinedTextField(
            profile.displayName,
            { value -> vm.update { it.copy(displayName = value) } },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            profile.chessComUsername,
            { value -> vm.update { it.copy(chessComUsername = value) } },
            label = { Text("Chess.com username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            profile.lichessUsername,
            { value -> vm.update { it.copy(lichessUsername = value) } },
            label = { Text("Lichess username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            profile.lichessToken,
            { value -> vm.update { it.copy(lichessToken = value) } },
            label = { Text("Lichess token (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Theme")
        ThemePreference.entries.forEach { theme ->
            FilterChip(profile.theme == theme, { vm.setTheme(theme) }, label = { Text(theme.name) })
        }
        Text("Engine depth ${profile.engineDepth}")
        Slider(
            value = profile.engineDepth.toFloat(),
            onValueChange = { value -> vm.update { it.copy(engineDepth = value.toInt()) } },
            valueRange = 4f..18f,
        )
        Text("Move time ${profile.engineMoveTimeMs} ms")
        Slider(
            value = profile.engineMoveTimeMs.toFloat(),
            onValueChange = { value -> vm.update { it.copy(engineMoveTimeMs = value.toLong()) } },
            valueRange = 200f..3000f,
        )
        Text("Skill ${profile.skillLevel}")
        Slider(
            value = profile.skillLevel.toFloat(),
            onValueChange = { value -> vm.update { it.copy(skillLevel = value.toInt()) } },
            valueRange = 0f..20f,
        )
        Button(onClick = vm::signOut) { Text("Sign out") }
        Text("Drop app/google-services.json to enable Firebase Auth + Firestore sync. GEMINI_API_KEY in local.properties enables coaching.")
    }
}
