package com.example.chess.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.chess.data.remote.OpeningExplorerMove
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.analysis.label
import com.example.chess.domain.chess.Fen
import com.example.chess.domain.chess.Move
import com.example.chess.domain.chess.PieceType
import com.example.chess.domain.chess.Position
import com.example.chess.domain.chess.Pgn
import com.example.chess.domain.chess.Square
import com.example.chess.domain.endgame.EndgameCatalog
import com.example.chess.domain.engine.SearchEngineClient
import com.example.chess.domain.model.PuzzleProgress
import com.example.chess.domain.model.TrainingSession
import com.example.chess.ui.board.ChessBoard
import com.example.chess.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Composable
fun TrainHomeScreen(
    onPuzzles: () -> Unit,
    onMistakes: () -> Unit,
    onOpenings: () -> Unit,
    onEndgames: () -> Unit,
    onPlanner: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Training")
        Button(onPuzzles, Modifier.fillMaxWidth()) { Text("Puzzle trainer") }
        Button(onMistakes, Modifier.fillMaxWidth()) { Text("Mistake library") }
        Button(onOpenings, Modifier.fillMaxWidth()) { Text("Opening explorer") }
        Button(onEndgames, Modifier.fillMaxWidth()) { Text("Endgame trainer") }
        Button(onPlanner, Modifier.fillMaxWidth()) { Text("Training planner") }
    }
}

@HiltViewModel
class MistakesViewModel @Inject constructor(private val repository: ChessRepository) : ViewModel() {
    val snapshot = repository.snapshot
    fun review(id: String, quality: Int) { viewModelScope.launch { repository.reviewMistake(id, quality) } }
    fun explain(id: String) {
        viewModelScope.launch {
            val mistake = repository.current().mistakes.first { it.id == id }
            repository.explainMistake(mistake)
        }
    }
}

@Composable
fun MistakesScreen(onOpenFen: (String) -> Unit, vm: MistakesViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Mistake library") }
        items(snap.mistakes.sortedBy { it.dueAt }, key = { it.id }) { mistake ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${mistake.kind.label()}  ${mistake.san}  drop ${mistake.evalDropCp}")
                    Text(if (mistake.dueAt <= now) "Due now" else "Due later")
                    if (mistake.explanation.isNotBlank()) Text(mistake.explanation)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onOpenFen(mistake.fen) }) { Text("Board") }
                        Button({ vm.explain(mistake.id) }) { Text("Coach") }
                        Button({ vm.review(mistake.id, 5) }) { Text("Easy") }
                        Button({ vm.review(mistake.id, 3) }) { Text("Hard") }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class PuzzleViewModel @Inject constructor(
    private val repository: ChessRepository,
) : ViewModel() {
    var position by mutableStateOf(Position.start())
    var selected by mutableStateOf<Square?>(null)
    var lastMove by mutableStateOf<Move?>(null)
    var solution by mutableStateOf(listOf<String>())
    var step by mutableStateOf(0)
    var status by mutableStateOf("Load a puzzle")
    var puzzleId by mutableStateOf("")
    var rating by mutableStateOf(1200)

    val legal get() = selected?.let { from -> position.legalMoves().filter { it.from == from }.map { it.to }.toSet() }.orEmpty()

    fun loadDaily() = viewModelScope.launch { runCatching { applyPuzzle(repository.dailyPuzzle()) }.onFailure { status = it.message ?: "Error" } }
    fun loadNext() = viewModelScope.launch { runCatching { applyPuzzle(repository.nextPuzzle()) }.onFailure { status = it.message ?: "Error" } }

    private fun applyPuzzle(dto: com.example.chess.data.remote.LichessPuzzleDto) {
        val game = Pgn.parse(dto.game.pgn)
        val path = Pgn.replay(game)
        val ply = dto.puzzle.initialPly.coerceIn(0, (path.size - 1).coerceAtLeast(0))
        position = path.getOrElse(ply) { path.last() }
        solution = dto.puzzle.solution
        step = 0
        puzzleId = dto.puzzle.id
        rating = dto.puzzle.rating
        selected = null
        lastMove = null
        status = "Find the tactic (${dto.puzzle.rating})"
    }

    fun onSquare(square: Square) {
        val from = selected
        if (from != null) {
            val move = position.legalMoves().firstOrNull {
                it.from == from && it.to == square && (it.promotion == null || it.promotion == PieceType.QUEEN)
            }
            if (move != null) {
                val expected = solution.getOrNull(step)
                if (expected != null && move.uci != expected && move.uci.take(4) != expected.take(4)) {
                    status = "Not the solution"
                    selected = null
                    viewModelScope.launch {
                        repository.savePuzzle(PuzzleProgress(puzzleId, position.fen(), rating, solved = false, attempts = 1))
                    }
                    return
                }
                position = position.apply(move)
                lastMove = move
                step += 1
                selected = null
                val reply = solution.getOrNull(step)
                if (reply != null) {
                    runCatching {
                        val engineMove = position.legalMoves().first { it.uci == reply || it.uci.take(4) == reply.take(4) }
                        position = position.apply(engineMove)
                        lastMove = engineMove
                        step += 1
                    }
                }
                if (step >= solution.size) {
                    status = "Solved"
                    viewModelScope.launch {
                        repository.savePuzzle(PuzzleProgress(puzzleId, position.fen(), rating, solved = true, attempts = 1))
                    }
                } else {
                    status = "Good, keep going"
                }
                return
            }
        }
        selected = square.takeIf { position.pieceAt(it)?.side == position.sideToMove }
    }
}

@Composable
fun PuzzleScreen(vm: PuzzleViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.loadDaily() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Puzzle trainer")
        ChessBoard(vm.position, selected = vm.selected, legalTargets = vm.legal, lastMove = vm.lastMove, onSquare = vm::onSquare)
        Text(vm.status)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::loadDaily) { Text("Daily") }
            Button(vm::loadNext) { Text("Next") }
        }
    }
}

@HiltViewModel
class OpeningsViewModel @Inject constructor(private val repository: ChessRepository) : ViewModel() {
    val snapshot = repository.snapshot
    var fen by mutableStateOf(Fen.START)
    var moves by mutableStateOf(listOf<OpeningExplorerMove>())
    var message by mutableStateOf<String?>(null)
    fun explore() {
        viewModelScope.launch {
            runCatching { moves = repository.explorer(fen) }
                .onFailure { message = it.message }
        }
    }
}

@Composable
fun OpeningsScreen(vm: OpeningsViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Opening explorer")
        snap.openings.forEach { Text("${it.name}: ${it.games} games  ${it.wins}/${it.draws}/${it.losses}") }
        OutlinedTextField(vm.fen, { vm.fen = it }, label = { Text("FEN") }, modifier = Modifier.fillMaxWidth())
        Button(vm::explore) { Text("Lichess explorer") }
        vm.moves.forEach { Text("${it.san}  W${it.white} D${it.draws} B${it.black}") }
        vm.message?.let { Text(it) }
    }
}

@HiltViewModel
class EndgameViewModel @Inject constructor(private val engine: SearchEngineClient) : ViewModel() {
    var drill by mutableStateOf(EndgameCatalog.drills.first())
    var position by mutableStateOf(Fen.parse(drill.fen))
    var selected by mutableStateOf<Square?>(null)
    var lastMove by mutableStateOf<Move?>(null)
    var status by mutableStateOf(drill.goal)
    val legal get() = selected?.let { from -> position.legalMoves().filter { it.from == from }.map { it.to }.toSet() }.orEmpty()

    fun load(id: String) {
        drill = EndgameCatalog.drills.first { it.id == id }
        position = Fen.parse(drill.fen)
        selected = null
        lastMove = null
        status = drill.goal
    }

    fun onSquare(square: Square) {
        val from = selected
        if (from != null) {
            val move = position.legalMoves().firstOrNull { it.from == from && it.to == square }
            if (move != null) {
                position = position.apply(move)
                lastMove = move
                selected = null
                viewModelScope.launch {
                    val reply = engine.search(position, 6, 400)
                    val engineMove = position.legalMoves().firstOrNull { it.uci == reply.bestMoveUci }
                    if (engineMove != null) {
                        position = position.apply(engineMove)
                        lastMove = engineMove
                    }
                    status = when (position.status()) {
                        com.example.chess.domain.chess.GameStatus.WHITE_WINS -> "White mates"
                        com.example.chess.domain.chess.GameStatus.BLACK_WINS -> "Black mates"
                        com.example.chess.domain.chess.GameStatus.DRAW -> "Draw"
                        else -> drill.goal
                    }
                }
                return
            }
        }
        selected = square.takeIf { position.pieceAt(it)?.side == position.sideToMove }
    }
}

@Composable
fun EndgameScreen(vm: EndgameViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Endgame trainer")
        ChessBoard(vm.position, selected = vm.selected, legalTargets = vm.legal, lastMove = vm.lastMove, onSquare = vm::onSquare)
        Text(vm.status)
        EndgameCatalog.drills.forEach { drill ->
            ListItem(
                headlineContent = { Text(drill.title) },
                supportingContent = { Text(drill.goal) },
                modifier = Modifier.clickable { vm.load(drill.id) },
            )
        }
    }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(private val repository: ChessRepository) : ViewModel() {
    val snapshot = repository.snapshot
    var title by mutableStateOf("Tactics hour")
    var focus by mutableStateOf("Blunders")
    fun add() {
        viewModelScope.launch {
            repository.saveSession(
                TrainingSession(UUID.randomUUID().toString(), title, focus, System.currentTimeMillis() + 86_400_000),
            )
        }
    }
    fun complete(id: String) {
        viewModelScope.launch {
            val session = repository.current().sessions.first { it.id == id }
            repository.saveSession(session.copy(completed = true))
        }
    }
    fun recommend() { viewModelScope.launch { repository.recommendTraining() } }
}

@Composable
fun PlannerScreen(vm: PlannerViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Training planner")
        OutlinedTextField(vm.title, { vm.title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.focus, { vm.focus = it }, label = { Text("Focus") }, modifier = Modifier.fillMaxWidth())
        Button(vm::add) { Text("Schedule session") }
        Button(vm::recommend) { Text("Coach recommendations") }
        snap.sessions.sortedBy { it.scheduledAt }.forEach { session ->
            ListItem(
                headlineContent = { Text(session.title) },
                supportingContent = { Text("${session.focus} · ${if (session.completed) "done" else "upcoming"}") },
                trailingContent = { if (!session.completed) Button({ vm.complete(session.id) }) { Text("Done") } },
            )
        }
        snap.coachNotes.takeLast(2).forEach { Text(it.body) }
    }
}
