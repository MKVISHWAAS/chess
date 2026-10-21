package com.example.chess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.domain.chess.GameStatus
import com.example.chess.domain.chess.Move
import com.example.chess.domain.chess.PieceType
import com.example.chess.domain.chess.Position
import com.example.chess.domain.chess.San
import com.example.chess.domain.chess.Side
import com.example.chess.domain.chess.Square
import com.example.chess.domain.engine.SearchEngineClient
import com.example.chess.domain.engine.skillToDepth
import com.example.chess.domain.engine.skillToTime
import com.example.chess.ui.board.ChessBoard
import com.example.chess.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayViewModel @Inject constructor(
    private val engine: SearchEngineClient,
) : ViewModel() {
    var position by mutableStateOf(Position.start())
    var selected by mutableStateOf<Square?>(null)
    var lastMove by mutableStateOf<Move?>(null)
    var skill by mutableFloatStateOf(8f)
    var userIsWhite by mutableStateOf(true)
    var status by mutableStateOf("Your move")
    var thinking by mutableStateOf(false)
    var sans by mutableStateOf(listOf<String>())

    val legal: Set<Square>
        get() = selected?.let { from -> position.legalMoves().filter { it.from == from }.map { it.to }.toSet() }.orEmpty()

    fun onSquare(square: Square) {
        val humanSide = if (userIsWhite) Side.WHITE else Side.BLACK
        if (position.sideToMove != humanSide || thinking) return
        val from = selected
        if (from != null) {
            val move = position.legalMoves().firstOrNull {
                it.from == from && it.to == square && (it.promotion == null || it.promotion == PieceType.QUEEN)
            }
            if (move != null) {
                play(move)
                selected = null
                engineMove()
                return
            }
        }
        selected = square.takeIf { position.pieceAt(it)?.side == humanSide }
    }

    fun newGame(asWhite: Boolean) {
        userIsWhite = asWhite
        position = Position.start()
        lastMove = null
        sans = emptyList()
        status = "Your move"
        if (!asWhite) engineMove()
    }

    private fun play(move: Move) {
        sans = sans + San.of(position, move)
        position = position.apply(move)
        lastMove = move
        status = when (position.status()) {
            GameStatus.WHITE_WINS -> "White wins"
            GameStatus.BLACK_WINS -> "Black wins"
            GameStatus.DRAW -> "Draw"
            GameStatus.ONGOING -> status
        }
    }

    private fun engineMove() {
        if (position.status() != GameStatus.ONGOING) return
        val humanSide = if (userIsWhite) Side.WHITE else Side.BLACK
        if (position.sideToMove == humanSide) return
        thinking = true
        status = "Engine thinking…"
        viewModelScope.launch {
            val result = engine.search(position, skillToDepth(skill.toInt()), skillToTime(skill.toInt()))
            val move = position.legalMoves().firstOrNull { it.uci == result.bestMoveUci }
            if (move != null) play(move)
            thinking = false
            if (position.status() == GameStatus.ONGOING) status = "Your move (${result.backend})"
        }
    }
}

@Composable
fun PlayScreen(vm: PlayViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Play vs computer")
        ChessBoard(
            position = vm.position,
            flipped = !vm.userIsWhite,
            selected = vm.selected,
            legalTargets = vm.legal,
            lastMove = vm.lastMove,
            onSquare = vm::onSquare,
            enabled = !vm.thinking,
        )
        Text(vm.status)
        Text("Skill ${vm.skill.toInt()}")
        Slider(vm.skill, { vm.skill = it }, valueRange = 1f..20f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.newGame(true) }) { Text("Play White") }
            Button({ vm.newGame(false) }) { Text("Play Black") }
        }
        Text(vm.sans.joinToString(" "))
    }
}
