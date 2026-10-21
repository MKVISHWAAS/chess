package com.example.chess.ui.train

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.domain.training.EndgameCatalog
import com.example.chess.ui.board.ChessBoardView
import com.example.chess.ui.components.EvalGraph

@Composable
fun TrainHubScreen(
    onMistakes: () -> Unit,
    onPuzzles: () -> Unit,
    onEndgames: () -> Unit,
    onPlanner: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Training")
        Button(onClick = onMistakes, modifier = Modifier.fillMaxWidth()) { Text("Mistake library + SRS") }
        Button(onClick = onPuzzles, modifier = Modifier.fillMaxWidth()) { Text("Puzzle trainer") }
        Button(onClick = onEndgames, modifier = Modifier.fillMaxWidth()) { Text("Endgame trainer") }
        Button(onClick = onPlanner, modifier = Modifier.fillMaxWidth()) { Text("Training planner") }
    }
}

@Composable
fun MistakesScreen(vm: TrainViewModel) {
    val mistakes by vm.mistakes.collectAsStateWithLifecycle()
    val coaching by vm.coaching.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mistakes, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${m.classification}  ${m.playedSan} → ${m.bestSan}")
                    Text(m.fen.value, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.reviewMistake(m, true) }) { Text("Got it") }
                        Button(onClick = { vm.reviewMistake(m, false) }) { Text("Again") }
                        Button(onClick = { vm.explain(m) }) { Text("Coach") }
                    }
                }
            }
        }
        if (coaching.isNotBlank()) item { Text(coaching) }
    }
}

@Composable
fun PuzzleScreen(vm: TrainViewModel) {
    val puzzle by vm.puzzle.collectAsStateWithLifecycle()
    val board by vm.puzzleBoard.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val legal by vm.legal.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message)
        ChessBoardView(board, false, selected, legal, onSquare = vm::tapPuzzle)
        Button(onClick = vm::loadPuzzle) { Text(if (puzzle == null) "Load puzzle" else "Next puzzle") }
    }
}

@Composable
fun EndgameScreen(vm: TrainViewModel) {
    val drill by vm.endgame.collectAsStateWithLifecycle()
    val board by vm.endBoard.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val legal by vm.legal.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(drill.title)
        Text(drill.goal)
        ChessBoardView(board, false, selected, legal, onSquare = vm::tapEndgame)
        Text(message)
        EndgameCatalog.drills.forEach { item ->
            Text(item.title, modifier = Modifier.clickable { vm.loadEndgame(item) }.padding(8.dp))
        }
    }
}

@Composable
fun PlannerScreen(vm: TrainViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("Tactics block") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Session") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addSession(title, "mixed") }) { Text("Schedule") }
        sessions.forEach { s ->
            Card(Modifier.fillMaxWidth().clickable { vm.toggleSession(s) }.padding(4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${if (s.completed) "✓ " else ""}${s.title}")
                    Text(s.kind)
                }
            }
        }
    }
}

@Composable
fun RatingsScreen(vm: TrainViewModel) {
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ratings")
        EvalGraph(ratings.map { it.rating - 1500 })
        ratings.forEach { Text("${it.platform} ${it.timeClass}: ${it.rating}") }
    }
}

@Composable
fun ExplorerScreen(vm: TrainViewModel) {
    val explorer by vm.explorer.collectAsStateWithLifecycle()
    val openings by vm.openings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Opening explorer")
        Button(onClick = vm::explore) { Text("Lichess explorer for current endgame board FEN") }
        Text(explorer)
        openings.forEach { Text("${it.eco} ${it.name}: ${it.wins}-${it.draws}-${it.losses}") }
    }
}
