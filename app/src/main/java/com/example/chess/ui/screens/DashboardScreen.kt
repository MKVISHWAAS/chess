package com.example.chess.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.analysis.round1
import com.example.chess.ui.components.SectionTitle
import com.example.chess.ui.components.Sparkline
import com.example.chess.ui.components.StatCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ChessRepository,
) : ViewModel() {
    val dashboard = repository.dashboard
    val notes = repository.snapshot

    fun recommend(onDone: (String) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { repository.recommendTraining() }.getOrElse { it.message ?: "Coach error" })
        }
    }
}

@Composable
fun DashboardScreen(
    onAnalyze: () -> Unit,
    onMistakes: () -> Unit,
    onGame: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val stats by vm.dashboard.collectAsStateWithLifecycle(com.example.chess.domain.model.DashboardStats())
    val snap by vm.notes.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Dashboard")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Games", "${stats.games}", Modifier.weight(1f))
            StatCard("ACPL", "${stats.avgAcpl.round1()}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Due mistakes", "${stats.mistakesDue}", Modifier.weight(1f))
            StatCard("Rating", "${stats.lastRating ?: "—"}", Modifier.weight(1f))
        }
        SectionTitle("Rating")
        Sparkline(stats.ratingSeries.map { it.rating.toFloat() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onAnalyze) { Text("FEN / PGN board") }
            Button(onMistakes) { Text("Review mistakes") }
        }
        Button({ vm.recommend {} }) { Text("Ask coach for a plan") }
        SectionTitle("Recent games")
        stats.recentGames.forEach { game ->
            ListItem(
                headlineContent = { Text("${game.white} vs ${game.black}") },
                supportingContent = { Text("${game.opening}  ${game.result}") },
                modifier = Modifier.clickable { onGame(game.id) },
            )
        }
        if (snap.coachNotes.isNotEmpty()) {
            SectionTitle("Coach notes")
            snap.coachNotes.takeLast(3).reversed().forEach {
                Text(it.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(it.body)
            }
        }
    }
}
