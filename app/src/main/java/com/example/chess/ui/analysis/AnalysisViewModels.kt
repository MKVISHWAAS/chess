package com.example.chess.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.ai.GeminiCoach
import com.example.chess.data.engine.ChessEngine
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.analysis.AnalysisService
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.model.AnalyzedMove
import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.GameAnalysis
import com.example.chess.domain.model.StoredGame
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisUi(
    val game: StoredGame? = null,
    val analysis: GameAnalysis? = null,
    val ply: Int = 0,
    val board: Board = Board(),
    val engineLine: String = "",
    val summary: String = "",
    val busy: Boolean = false,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChessRepository,
    private val rules: ChessRules,
    private val analysisService: AnalysisService,
    private val engine: ChessEngine,
    private val coach: GeminiCoach,
) : ViewModel() {
    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val _ui = MutableStateFlow(AnalysisUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val game = repository.games.first().firstOrNull { it.id == gameId }
            val analysis = repository.analysis(gameId).first()
            _ui.value = _ui.value.copy(game = game, analysis = analysis)
            game?.let { goTo(-1) }
        }
    }

    fun goTo(ply: Int) {
        val game = _ui.value.game ?: return
        val parsed = rules.parsePgn(game.pgn)
        val board = rules.boardFromFen(parsed.startFen)
        val target = ply.coerceAtLeast(-1)
        parsed.sans.take((target + 1).coerceAtLeast(0)).forEach { rules.applySanInPlace(board, it) }
        _ui.value = _ui.value.copy(ply = target, board = board)
        viewModelScope.launch {
            val eval = engine.evaluate(rules.fenOf(board), 8, 400)
            _ui.value = _ui.value.copy(engineLine = "${eval.scoreCp ?: eval.mate}  ${eval.pvSan}")
        }
    }

    fun analyzeNow() {
        val game = _ui.value.game ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            runCatching {
                val profile = repository.signedIn.filterNotNull().first()
                val (analysis, mistakes) = analysisService.analyzePgn(
                    game.id, game.pgn, profile.engineDepth, profile.engineMoveTimeMs,
                )
                repository.saveAnalysis(analysis, mistakes, (analysis.acplWhite + analysis.acplBlack) / 2)
                _ui.value = _ui.value.copy(analysis = analysis, busy = false)
            }.onFailure {
                _ui.value = _ui.value.copy(busy = false, engineLine = it.message.orEmpty())
            }
        }
    }

    fun summarize() {
        viewModelScope.launch {
            val game = _ui.value.game ?: return@launch
            val moves = _ui.value.analysis?.moves.orEmpty()
            _ui.value = _ui.value.copy(summary = coach.summarizeGame(game, moves))
        }
    }

    fun currentMove(): AnalyzedMove? = _ui.value.analysis?.moves?.getOrNull(_ui.value.ply)

    fun evalSeries(): List<Int> = _ui.value.analysis?.moves?.map { it.evalCp ?: 0 }.orEmpty()
}

data class FenUi(
    val fen: String = com.example.chess.domain.chess.START_FEN,
    val board: Board = Board(),
    val selected: Square? = null,
    val eval: String = "",
    val pv: String = "",
    val error: String? = null,
)

@HiltViewModel
class FenViewModel @Inject constructor(
    private val rules: ChessRules,
    private val analysisService: AnalysisService,
    private val repository: ChessRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(FenUi())
    val ui = _ui.asStateFlow()

    fun setFen(value: String) {
        _ui.value = _ui.value.copy(fen = value)
    }

    fun load() {
        runCatching {
            val board = rules.boardFromFen(Fen(_ui.value.fen.trim()))
            _ui.value = _ui.value.copy(board = board, error = null, selected = null)
        }.onFailure { _ui.value = _ui.value.copy(error = "Invalid FEN") }
    }

    fun tap(square: Square) {
        val board = _ui.value.board
        val selected = _ui.value.selected
        if (selected == null) {
            _ui.value = _ui.value.copy(selected = square)
            return
        }
        val move = rules.legalMovesFrom(board, selected).firstOrNull { it.to == square }
        if (move != null) {
            val next = rules.applyMove(board, move)
            _ui.value = _ui.value.copy(board = next, selected = null, fen = next.fen)
        } else {
            _ui.value = _ui.value.copy(selected = square)
        }
    }

    fun analyze() {
        viewModelScope.launch {
            val profile = repository.signedIn.filterNotNull().first()
            val eval = analysisService.analyzeFen(Fen(_ui.value.board.fen), profile.engineDepth, profile.engineMoveTimeMs)
            _ui.value = _ui.value.copy(
                eval = listOfNotNull(eval.scoreCp?.let { "$it cp" }, eval.mate?.let { "M$it" }).joinToString(),
                pv = eval.pvSan,
            )
        }
    }
}
