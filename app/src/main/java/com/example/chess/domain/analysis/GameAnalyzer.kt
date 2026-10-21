package com.example.chess.domain.analysis

import com.example.chess.domain.chess.Pgn
import com.example.chess.domain.chess.PgnGame
import com.example.chess.domain.chess.San
import com.example.chess.domain.engine.SearchEngineClient
import com.example.chess.domain.engine.classifyDrop
import com.example.chess.domain.engine.whitePerspective
import com.example.chess.domain.model.AnalyzedMove
import com.example.chess.domain.model.GameAnalysis
import com.example.chess.domain.model.Mistake
import java.util.UUID
import kotlin.math.roundToInt

class GameAnalyzer(
    private val engine: SearchEngineClient,
) {
    suspend fun analyzePgn(gameId: String, pgnGame: PgnGame, depth: Int, moveTimeMs: Long): Pair<GameAnalysis, List<Mistake>> {
        var position = com.example.chess.domain.chess.Fen.parse(pgnGame.headers["FEN"] ?: com.example.chess.domain.chess.Fen.START)
        val moves = mutableListOf<AnalyzedMove>()
        val mistakes = mutableListOf<Mistake>()
        var lossSum = 0
        pgnGame.moves.forEachIndexed { ply, san ->
            val played = San.parse(position, san)
            val best = engine.search(position, depth, moveTimeMs)
            val after = position.apply(played)
            val playedEval = engine.search(after, (depth - 1).coerceAtLeast(1), moveTimeMs / 2)
            val bestCp = best.evalCp.whitePerspective(position.sideToMove)
            val playedCp = (-playedEval.evalCp).whitePerspective(position.sideToMove)
            val drop = abs(bestCp - playedCp)
            val kind = classifyDrop(drop)
            val analyzed = AnalyzedMove(
                ply = ply,
                san = san,
                fenBefore = position.fen(),
                fenAfter = after.fen(),
                evalCp = playedCp,
                bestEvalCp = bestCp,
                pv = best.pv.joinToString(" "),
                classification = kind,
            )
            moves += analyzed
            lossSum += drop
            if (kind != null) {
                mistakes += Mistake(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId,
                    fen = position.fen(),
                    san = san,
                    kind = kind,
                    evalDropCp = drop,
                    bestPv = best.pv.joinToString(" "),
                )
            }
            position = after
        }
        val acpl = if (moves.isEmpty()) 0.0 else lossSum.toDouble() / moves.size
        return GameAnalysis(gameId, moves, acpl) to mistakes
    }

    suspend fun analyzeFen(fen: String, depth: Int, moveTimeMs: Long) =
        engine.search(com.example.chess.domain.chess.Fen.parse(fen), depth, moveTimeMs)
}

fun MistakeKind.label(): String = when (this) {
    MistakeKind.BLUNDER -> "Blunder"
    MistakeKind.MISTAKE -> "Mistake"
    MistakeKind.INACCURACY -> "Inaccuracy"
}

fun Double.round1(): Double = (this * 10).roundToInt() / 10.0
