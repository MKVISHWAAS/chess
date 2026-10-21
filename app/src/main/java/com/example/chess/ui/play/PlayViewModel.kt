package com.example.chess.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.engine.ChessEngine
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.LegalMove
import com.example.chess.domain.chess.START_FEN
import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.GameResult
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayUi(
    val board: Board = Board(),
    val selected: Square? = null,
    val legal: List<LegalMove> = emptyList(),
    val sans: List<String> = emptyList(),
    val status: String = "You play White vs engine",
    val flipped: Boolean = false,
    val lastFrom: Square? = null,
    val lastTo: Square? = null,
    val thinking: Boolean = false,
)

@HiltViewModel
class PlayViewModel @Inject constructor(
    private val rules: ChessRules,
    private val engine: ChessEngine,
    private val repository: ChessRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(PlayUi())
    val ui = _ui.asStateFlow()

    fun tap(square: Square) {
        val state = _ui.value
        if (state.thinking || rules.isGameOver(state.board)) return
        val selected = state.selected
        if (selected == null) {
            val legal = rules.legalMovesFrom(state.board, square)
            _ui.value = state.copy(selected = square, legal = legal)
            return
        }
        val move = state.legal.firstOrNull { it.to == square && it.from == selected }
        if (move == null) {
            _ui.value = state.copy(selected = square, legal = rules.legalMovesFrom(state.board, square))
            return
        }
        val next = rules.applyMove(state.board, move)
        _ui.value = state.copy(
            board = next,
            selected = null,
            legal = emptyList(),
            sans = state.sans + move.san,
            lastFrom = move.from,
            lastTo = move.to,
        )
        if (rules.isGameOver(next)) {
            _ui.value = _ui.value.copy(status = rules.resultOf(next).name)
            persist()
            return
        }
        think()
    }

    fun newGame() {
        _ui.value = PlayUi(board = rules.startBoard())
    }

    private fun think() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(thinking = true, status = "Engine thinking…")
            val profile = repository.signedIn.filterNotNull().first()
            val uci = engine.bestMove(Fen(_ui.value.board.fen), profile.skillLevel, profile.engineMoveTimeMs)
            val board = _ui.value.board
            val move = rules.legalMoves(board).firstOrNull { it.uci == uci.lowercase() }
            if (move != null) {
                val next = rules.applyMove(board, move)
                _ui.value = _ui.value.copy(
                    board = next,
                    sans = _ui.value.sans + move.san,
                    thinking = false,
                    status = if (rules.isGameOver(next)) rules.resultOf(next).name else "Your move",
                    lastFrom = move.from,
                    lastTo = move.to,
                )
                if (rules.isGameOver(next)) persist()
            } else {
                _ui.value = _ui.value.copy(thinking = false, status = "Engine move $uci not legal")
            }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            val pgn = rules.pgnFromMoves("You", engine.name, _ui.value.sans, rules.resultOf(_ui.value.board))
            repository.upsertGame(
                com.example.chess.domain.model.StoredGame(
                    id = com.example.chess.domain.chess.newId(),
                    source = com.example.chess.domain.model.GameSource.ENGINE,
                    white = "You",
                    black = engine.name,
                    result = rules.resultOf(_ui.value.board),
                    pgn = pgn,
                    opening = rules.detectOpening(_ui.value.sans),
                    playedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}

private val unusedFen = START_FEN
private val unusedResult = GameResult.UNKNOWN
