package com.example.chess.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.domain.model.StoredGame

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onGame: (StoredGame) -> Unit,
    onFen: () -> Unit,
    onTrain: () -> Unit,
) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Hello, ${profile?.displayName ?: "player"}", style = MaterialTheme.typography.headlineSmall)
            Text("Engine-backed training desk. Eval is Stockfish/NNUE-style; words come from Gemini when configured.")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Games", "${snap.gamesPlayed}", Modifier.weight(1f))
                StatCard("Analyzed", "${snap.analyzedGames}", Modifier.weight(1f))
                StatCard("Due", "${snap.dueMistakes}", Modifier.weight(1f))
            }
        }
        item {
            StatCard(
                "Rating",
                buildString {
                    append(snap.latestRating?.toString() ?: "—")
                    snap.ratingDelta?.let { append(" (${if (it >= 0) "+" else ""}$it)") }
                },
            )
        }
        item {
            Button(onClick = vm::refreshCoaching) { Text("Refresh AI plan") }
            if (snap.coachingSummary.isNotBlank()) {
                Text(snap.coachingSummary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFen) { Text("FEN lab") }
                Button(onClick = onTrain) { Text("Train") }
            }
        }
        items(snap.recentGames, key = { it.id }) { game ->
            Card(Modifier.fillMaxWidth().clickable { onGame(game) }) {
                Column(Modifier.padding(12.dp)) {
                    Text("${game.white} vs ${game.black}")
                    Text("${game.opening} · ${game.result}")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
