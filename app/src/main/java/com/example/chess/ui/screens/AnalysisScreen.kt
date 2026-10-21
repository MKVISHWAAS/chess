package com.example.chess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.chess.Fen
import com.example.chess.domain.chess.Move
import com.example.chess.domain.chess.PieceType
import com.example.chess.domain.chess.Position
import com.example.chess.domain.chess.San
import com.example.chess.domain.chess.Square
import com.example.chess.domain.engine.SearchResult
import com.example.chess.domain.engine.formatEval
import com.example.chess.ui.board.ChessBoard
import com.example.chess.ui.components.SectionTitle
import com.example.chess.ui.components.Sparkline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: ChessRepository,
) : ViewModel() {
    var fenInput by mutableStateOf(Fen.START)
    var pgnInput by mutableStateOf("")
    var position by mutableStateOf(Position.start())
    var selected by mutableStateOf<Square?>(null)
    var lastMove by mutableStateOf<Move?>(null)
    var evals by mutableStateOf(listOf(0f))
        private set
    var result by mutableStateOf<SearchResult?>(null)
        private set
    var sans by mutableStateOf(listOf<String>())
        private set
    var message by mutableStateOf<String?>(null)

    val legal: Set<Square>
        get() = selected?.let { from -> position.legalMoves().filter { it.from == from }.map { it.to }.toSet() }.orEmpty()

    fun loadFen(fen: String) {
        runCatching {
            position = Fen.parse(fen)
            fenInput = position.fen()
            selected = null
            lastMove = null
            sans = emptyList()
        }.onFailure { message = it.message }
    }

    fun loadPgn(text: String) {
        runCatching {
            val game = com.example.chess.domain.chess.Pgn.parse(text)
            val path = com.example.chess.domain.chess.Pgn.replay(game)
            position = path.last()
            fenInput = position.fen()
            sans = game.moves
        }.onFailure { message = it.message }
    }

    fun onSquare(square: Square) {
        val from = selected
        if (from != null) {
            val move = position.legalMoves().firstOrNull {
                it.from == from && it.to == square && (it.promotion == null || it.promotion == PieceType.QUEEN)
            }
            if (move != null) {
                sans = sans + San.of(position, move)
                position = position.apply(move)
                lastMove = move
                fenInput = position.fen()
                selected = null
                return
            }
        }
        selected = square.takeIf { position.pieceAt(it)?.side == position.sideToMove }
    }

    fun evaluate() {
        viewModelScope.launch {
            result = repository.analyzeFen(position.fen())
            evals = evals + (result?.evalCp ?: 0) / 100f
            message = "Backend ${result?.backend}"
        }
    }

    fun reset() = loadFen(Fen.START)
}

@Composable
fun AnalysisScreen(
    initialFen: String,
    gameId: String,
    vm: AnalysisViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(initialFen) {
        if (initialFen.isNotBlank()) vm.loadFen(initialFen)
    }
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Interactive analysis")
        ChessBoard(
            position = vm.position,
            selected = vm.selected,
            legalTargets = vm.legal,
            lastMove = vm.lastMove,
            onSquare = vm::onSquare,
        )
        Text("Eval ${vm.result?.evalCp?.formatEval() ?: "—"}  PV ${vm.result?.pv?.joinToString(" ").orEmpty()}")
        Sparkline(vm.evals)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::evaluate) { Text("Engine") }
            Button(vm::reset) { Text("Start") }
            Button({ clipboard.setText(AnnotatedString(vm.position.fen())) }) { Text("Copy FEN") }
            Button({
                clipboard.setText(
                    AnnotatedString(
                        com.example.chess.domain.chess.Pgn.export(
                            mapOf("Event" to "ChessOS analysis"),
                            vm.sans,
                        ),
                    ),
                )
            }) { Text("Copy PGN") }
        }
        OutlinedTextField(vm.fenInput, { vm.fenInput = it }, label = { Text("FEN") }, modifier = Modifier.fillMaxWidth())
        Button({ vm.loadFen(vm.fenInput) }) { Text("Load FEN") }
        OutlinedTextField(vm.pgnInput, { vm.pgnInput = it }, label = { Text("PGN") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button({ vm.loadPgn(vm.pgnInput) }) { Text("Load PGN") }
        Text("Moves: ${vm.sans.joinToString(" ")}")
        vm.message?.let { Text(it) }
        if (gameId.isNotBlank()) Text("Linked game $gameId")
    }
}
