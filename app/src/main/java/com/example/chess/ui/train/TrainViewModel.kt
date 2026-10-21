package com.example.chess.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.ai.GeminiCoach
import com.example.chess.data.engine.ChessEngine
import com.example.chess.data.remote.LichessPuzzle
import com.example.chess.data.remote.PlatformImporter
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.LegalMove
import com.example.chess.domain.chess.newId
import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.PuzzleProgress
import com.example.chess.domain.model.TrainingSession
import com.example.chess.domain.srs.SpacedRepetition
import com.example.chess.domain.training.EndgameCatalog
import com.example.chess.domain.training.EndgameDrill
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val importer: PlatformImporter,
    private val rules: ChessRules,
    private val engine: ChessEngine,
    private val coach: GeminiCoach,
) : ViewModel() {
    val mistakes = repository.mistakes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openings = repository.openings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ratings = repository.ratings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _puzzle = MutableStateFlow<LichessPuzzle?>(null)
    val puzzle = _puzzle.asStateFlow()
    private val _puzzleBoard = MutableStateFlow(Board())
    val puzzleBoard = _puzzleBoard.asStateFlow()
    private val _selected = MutableStateFlow<Square?>(null)
    val selected = _selected.asStateFlow()
    private val _legal = MutableStateFlow<List<LegalMove>>(emptyList())
    val legal = _legal.asStateFlow()
    private val _message = MutableStateFlow("Load a puzzle")
    val message = _message.asStateFlow()
    private val _solutionIndex = MutableStateFlow(0)
    private val _explorer = MutableStateFlow("")
    val explorer = _explorer.asStateFlow()
    private val _endgame = MutableStateFlow(EndgameCatalog.drills.first())
    val endgame = _endgame.asStateFlow()
    private val _endBoard = MutableStateFlow(rules.boardFromFen(Fen(EndgameCatalog.drills.first().fen)))
    val endBoard = _endBoard.asStateFlow()
    private val _coaching = MutableStateFlow("")
    val coaching = _coaching.asStateFlow()

    fun loadPuzzle() {
        viewModelScope.launch {
            runCatching {
                val p = runCatching { importer.nextPuzzle() }.getOrElse { importer.dailyPuzzle() }
                _puzzle.value = p
                _puzzleBoard.value = rules.boardFromFen(Fen(p.fen))
                _solutionIndex.value = 0
                _message.value = "Puzzle ${p.id} (${p.rating})"
            }.onFailure { _message.value = it.message.orEmpty() }
        }
    }

    fun tapPuzzle(square: Square) {
        val puzzle = _puzzle.value ?: return
        val board = _puzzleBoard.value
        val selected = _selected.value
        if (selected == null) {
            _selected.value = square
            _legal.value = rules.legalMovesFrom(board, square)
            return
        }
        val move = _legal.value.firstOrNull { it.to == square && it.from == selected } ?: run {
            _selected.value = square
            _legal.value = rules.legalMovesFrom(board, square)
            return
        }
        val expected = puzzle.solutionUci.getOrNull(_solutionIndex.value)?.lowercase()
        if (move.uci != expected) {
            _message.value = "Wrong. Try again."
            viewModelScope.launch {
                repository.savePuzzle(
                    PuzzleProgress(puzzle.id, false, 1, System.currentTimeMillis(), puzzle.rating),
                )
            }
            _selected.value = null
            _legal.value = emptyList()
            return
        }
        val next = rules.applyMove(board, move)
        _puzzleBoard.value = next
        _solutionIndex.value += 1
        _selected.value = null
        _legal.value = emptyList()
        val reply = puzzle.solutionUci.getOrNull(_solutionIndex.value)
        if (reply != null) {
            _puzzleBoard.value = rules.applyUci(next, reply)
            _solutionIndex.value += 1
        }
        if (_solutionIndex.value >= puzzle.solutionUci.size) {
            _message.value = "Solved"
            viewModelScope.launch {
                repository.savePuzzle(PuzzleProgress(puzzle.id, true, 1, System.currentTimeMillis(), puzzle.rating))
            }
        } else {
            _message.value = "Good. Continue."
        }
    }

    fun reviewMistake(mistake: Mistake, remembered: Boolean) {
        viewModelScope.launch {
            repository.saveMistakes(listOf(SpacedRepetition.review(mistake, remembered)))
        }
    }

    fun explain(mistake: Mistake) {
        viewModelScope.launch { _coaching.value = coach.explainBlunder(mistake) }
    }

    fun loadEndgame(drill: EndgameDrill) {
        _endgame.value = drill
        _endBoard.value = rules.boardFromFen(Fen(drill.fen))
    }

    fun tapEndgame(square: Square) {
        val board = _endBoard.value
        val selected = _selected.value
        if (selected == null) {
            _selected.value = square
            _legal.value = rules.legalMovesFrom(board, square)
            return
        }
        val move = _legal.value.firstOrNull { it.to == square }
        if (move != null) {
            viewModelScope.launch {
                val before = Fen(board.fen)
                val best = engine.bestMove(before, 12, 400)
                val next = rules.applyMove(board, move)
                _endBoard.value = next
                _message.value = if (move.uci == best.lowercase()) "Engine agrees" else "Engine prefers $best"
            }
        }
        _selected.value = null
        _legal.value = emptyList()
    }

    fun explore() {
        viewModelScope.launch {
            _explorer.value = importer.lichessOpening(_endBoard.value.fen)
        }
    }

    fun addSession(title: String, kind: String) {
        viewModelScope.launch {
            repository.saveSession(
                TrainingSession(newId(), title, kind, System.currentTimeMillis() + 86_400_000, false, coaching.value),
            )
        }
    }

    fun toggleSession(session: TrainingSession) {
        viewModelScope.launch { repository.saveSession(session.copy(completed = !session.completed)) }
    }
}
