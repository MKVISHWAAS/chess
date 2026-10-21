package com.example.chess.domain.analysis

import com.example.chess.data.engine.ChessEngine
import com.example.chess.data.engine.whiteCp
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.OpeningBook
import com.example.chess.domain.chess.newId
import com.example.chess.domain.model.AnalyzedMove
import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.GameAnalysis
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisService @Inject constructor(
    private val rules: ChessRules,
    private val engine: ChessEngine,
) {
    suspend fun analyzePgn(
        gameId: String,
        pgn: String,
        depth: Int,
        moveTimeMs: Long,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): Pair<GameAnalysis, List<Mistake>> = withContext(Dispatchers.Default) {
        val parsed = rules.parsePgn(pgn)
        val board = rules.boardFromFen(parsed.startFen)
        val analyzed = mutableListOf<AnalyzedMove>()
        val mistakes = mutableListOf<Mistake>()
        parsed.sans.forEachIndexed { index, san ->
            onProgress(index + 1, parsed.sans.size)
            val fenBefore = rules.fenOf(board)
            val side = rules.sideToMove(board)
            val best = engine.evaluate(fenBefore, depth, moveTimeMs)
            val played = rules.applySanInPlace(board, san)
            val fenAfter = rules.fenOf(board)
            val playedEval = engine.evaluate(fenAfter, (depth - 2).coerceAtLeast(2), moveTimeMs / 2)
            val bestCp = best.whiteCp()
            val playedCp = playedEval.whiteCp()
            val fromSideBest = if (side == Side.WHITE) bestCp else -bestCp
            val fromSidePlayed = if (side == Side.WHITE) playedCp else -playedCp
            val classification = MoveClassifier.classify(
                bestCp = fromSideBest,
                playedCp = fromSidePlayed,
                ply = index,
                isBook = OpeningBook.nameFor(parsed.sans.take(index + 1)) != "Unknown Opening",
            )
            val move = AnalyzedMove(
                ply = index,
                san = played.san,
                fenBefore = fenBefore,
                fenAfter = fenAfter,
                uci = played.uci,
                evalCp = fromSidePlayed,
                bestEvalCp = fromSideBest,
                pv = best.pvSan,
                classification = classification,
                bestMoveSan = best.pvSan.substringBefore(' ').removePrefix("${index / 2 + 1}."),
            )
            analyzed += move
            if (MoveClassifier.isMistake(classification)) {
                mistakes += Mistake(
                    id = newId(),
                    gameId = gameId,
                    fen = fenBefore,
                    playedSan = played.san,
                    bestSan = move.bestMoveSan,
                    classification = classification,
                    ply = index,
                    dueAt = System.currentTimeMillis(),
                )
            }
        }
        val whiteLosses = analyzed.filterIndexed { i, _ -> i % 2 == 0 }.map {
            (it.bestEvalCp ?: 0) - (it.evalCp ?: 0)
        }
        val blackLosses = analyzed.filterIndexed { i, _ -> i % 2 == 1 }.map {
            (it.bestEvalCp ?: 0) - (it.evalCp ?: 0)
        }
        GameAnalysis(
            gameId = gameId,
            moves = analyzed,
            acplWhite = MoveClassifier.acpl(whiteLosses),
            acplBlack = MoveClassifier.acpl(blackLosses),
            accuracyWhite = MoveClassifier.accuracyFromLosses(whiteLosses),
            accuracyBlack = MoveClassifier.accuracyFromLosses(blackLosses),
        ) to mistakes
    }

    suspend fun analyzeFen(fen: Fen, depth: Int, moveTimeMs: Long) =
        engine.evaluate(fen, depth, moveTimeMs)
}
