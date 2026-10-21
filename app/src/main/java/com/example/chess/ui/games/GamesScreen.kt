package com.example.chess.ui.games

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.domain.model.GameSource
import com.example.chess.domain.model.StoredGame

@Composable
fun GamesScreen(
    vm: GamesViewModel,
    onOpen: (StoredGame) -> Unit,
    onRatings: () -> Unit,
    onExplorer: () -> Unit,
) {
    val games by vm.games.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var pgn by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, {
            query = it
            vm.setQuery(it)
        }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = true, onClick = { vm.setSource(null) }, label = { Text("All") })
            GameSource.entries.forEach { src ->
                FilterChip(false, { vm.setSource(src.name) }, label = { Text(src.name) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::importChessCom) { Text("Chess.com") }
            Button(onClick = vm::importLichess) { Text("Lichess") }
            Button(onClick = onRatings) { Text("Ratings") }
        }
        Button(onClick = onExplorer) { Text("Opening explorer") }
        OutlinedTextField(pgn, { pgn = it }, label = { Text("Paste PGN") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { vm.importPgn(pgn) }) { Text("Import PGN + analyze") }
        status?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(games, key = { it.id }) { game ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(game) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${game.white} vs ${game.black}")
                        Text("${game.source} · ${game.opening} · ACPL ${game.acpl ?: "…"}")
                        Text("Export", modifier = Modifier.clickable {
                            context.startActivity(android.content.Intent.createChooser(vm.shareIntent(game), "Export PGN"))
                        })
                    }
                }
            }
        }
    }
}
