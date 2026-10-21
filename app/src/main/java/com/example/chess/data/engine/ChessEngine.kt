package com.example.chess.data.engine

import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.model.EngineEval
import com.example.chess.domain.model.Fen

interface ChessEngine {
    val name: String
    suspend fun evaluate(fen: Fen, depth: Int, moveTimeMs: Long): EngineEval
    suspend fun bestMove(fen: Fen, skillLevel: Int, moveTimeMs: Long): String
}

class CompositeEngine(
    private val preferred: ChessEngine?,
    private val fallback: ChessEngine,
) : ChessEngine {
    override val name: String get() = preferred?.name ?: fallback.name

    private val active: ChessEngine get() = preferred ?: fallback

    override suspend fun evaluate(fen: Fen, depth: Int, moveTimeMs: Long): EngineEval =
        runCatching { active.evaluate(fen, depth, moveTimeMs) }
            .getOrElse { fallback.evaluate(fen, depth, moveTimeMs) }

    override suspend fun bestMove(fen: Fen, skillLevel: Int, moveTimeMs: Long): String =
        runCatching { active.bestMove(fen, skillLevel, moveTimeMs) }
            .getOrElse { fallback.bestMove(fen, skillLevel, moveTimeMs) }
}

fun EngineEval.whiteCp(): Int = when {
    mate != null -> if (mate > 0) 10_000 - mate else -10_000 - mate
    else -> scoreCp ?: 0
}

fun ChessRules.pvToSan(start: Fen, uciMoves: List<String>): String {
    val board = boardFromFen(start)
    return buildString {
        uciMoves.take(8).forEachIndexed { index, uci ->
            val legal = legalMoves(board).firstOrNull { it.uci == uci.lowercase() } ?: return@forEachIndexed
            if (index % 2 == 0) append("${board.moveCounter}. ")
            append(legal.san).append(' ')
            applyUci(board, uci)
        }
    }.trim()
}
