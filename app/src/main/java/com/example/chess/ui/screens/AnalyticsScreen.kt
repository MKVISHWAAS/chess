package com.example.chess.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.chess.Fen
import com.example.chess.domain.model.GameSource
import com.example.chess.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: ChessRepository,
) : ViewModel() {
    val snapshot = repository.snapshot
    var message by mutableStateOf<String?>(null)
        private set

    fun importChessCom(username: String) = launchMsg {
        val n = repository.importChessCom(username)
        "Imported $n Chess.com games"
    }

    fun importLichess(username: String) = launchMsg {
        val n = repository.importLichess(username)
        "Imported $n Lichess games"
    }

    fun importPgn(text: String) = launchMsg {
        val n = repository.importPgn(text, GameSource.PGN_UPLOAD).size
        "Stored $n games from PGN"
    }

    private fun launchMsg(block: suspend () -> String) {
        viewModelScope.launch {
            message = runCatching { block() }.getOrElse { it.message }
        }
    }
}

@Composable
fun AnalyticsScreen(
    onGame: (String) -> Unit,
    onAnalyzeFen: (String) -> Unit,
    vm: AnalyticsViewModel = hiltViewModel(),
) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    var query by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(snap.profile?.chessComUsername.orEmpty()) }
    var lichess by remember { mutableStateOf(snap.profile?.lichessUsername.orEmpty()) }
    var pgn by remember { mutableStateOf("") }
    var fen by remember { mutableStateOf(Fen.START) }
    val games = snap.games.filter {
        val q = query.lowercase()
        q.isBlank() || it.white.lowercase().contains(q) || it.black.lowercase().contains(q) || it.opening.lowercase().contains(q)
    }.sortedByDescending { it.playedAt }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Import & ratings") }
        item {
            OutlinedTextField(username, { username = it }, label = { Text("Chess.com username") }, modifier = Modifier.fillMaxWidth())
        }
        item { Button({ vm.importChessCom(username) }) { Text("Import Chess.com") } }
        item {
            OutlinedTextField(lichess, { lichess = it }, label = { Text("Lichess username") }, modifier = Modifier.fillMaxWidth())
        }
        item { Button({ vm.importLichess(lichess) }) { Text("Import Lichess") } }
        item {
            OutlinedTextField(pgn, { pgn = it }, label = { Text("Paste PGN") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        }
        item { Button({ vm.importPgn(pgn) }) { Text("Upload PGN") } }
        item {
            OutlinedTextField(fen, { fen = it }, label = { Text("Analyze FEN") }, modifier = Modifier.fillMaxWidth())
        }
        item { Button({ onAnalyzeFen(fen) }) { Text("Open FEN on board") } }
        item { vm.message?.let { Text(it) } }
        item { SectionTitle("Ratings") }
        items(snap.ratings.sortedByDescending { it.recordedAt }) { point ->
            ListItem(
                headlineContent = { Text("${point.platform} ${point.timeClass}") },
                supportingContent = { Text("${point.rating}") },
            )
        }
        item {
            Column {
                SectionTitle("Games")
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("${games.size} shown") })
            }
        }
        items(games, key = { it.id }) { game ->
            ListItem(
                headlineContent = { Text("${game.white} vs ${game.black}") },
                supportingContent = { Text("${game.opening} · ${game.result} · ${game.platform.ifBlank { game.source.name }}") },
                modifier = Modifier.clickable { onGame(game.id) },
            )
        }
    }
}
