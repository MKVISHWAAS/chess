package com.example.chess.domain.engine

import com.example.chess.domain.chess.GameStatus
import com.example.chess.domain.chess.Move
import com.example.chess.domain.chess.Position
import kotlin.math.max
import kotlin.math.min

class BuiltinSearchEngine {
    fun search(position: Position, depth: Int, moveTimeMs: Long): SearchResult {
        val deadline = System.currentTimeMillis() + moveTimeMs
        var best: Move? = null
        var eval = Evaluator.evaluate(position)
        var lastPv = emptyList<String>()
        var reached = 1
        val moves = position.legalMoves()
        if (moves.isEmpty()) {
            val terminal = when (position.status()) {
                GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> -20_000
                else -> 0
            }
            return SearchResult(null, terminal, emptyList(), 0, "builtin")
        }
        for (d in 1..depth) {
            if (System.currentTimeMillis() > deadline) break
            var alpha = Int.MIN_VALUE / 4
            val beta = Int.MAX_VALUE / 4
            var localBest: Move? = null
            var ordered = if (best != null) listOf(best!!) + moves.filter { it != best } else moves
            for (move in ordered) {
                if (System.currentTimeMillis() > deadline) break
                val child = position.apply(move)
                val score = -negamax(child, d - 1, -beta, -alpha, deadline)
                if (score > alpha) {
                    alpha = score
                    localBest = move
                }
            }
            if (localBest != null) {
                best = localBest
                eval = alpha
                reached = d
                lastPv = principalVariation(position, localBest, d)
            }
        }
        return SearchResult(best?.uci, eval, lastPv, reached, "builtin")
    }

    private fun principalVariation(root: Position, first: Move, depth: Int): List<String> {
        val pv = mutableListOf(first.uci)
        var pos = root.apply(first)
        repeat(depth - 1) {
            val move = pos.legalMoves().maxByOrNull { Evaluator.evaluate(pos.apply(it)) } ?: return pv
            pv += move.uci
            pos = pos.apply(move)
        }
        return pv
    }

    private fun negamax(position: Position, depth: Int, alpha0: Int, beta0: Int, deadline: Long): Int {
        if (System.currentTimeMillis() > deadline) return Evaluator.evaluate(position)
        val moves = position.legalMoves()
        if (moves.isEmpty()) {
            return when (position.status()) {
                GameStatus.WHITE_WINS, GameStatus.BLACK_WINS -> -20_000 + (8 - depth)
                else -> 0
            }
        }
        if (depth <= 0) return Evaluator.evaluate(position)
        var alpha = alpha0
        val beta = beta0
        var best = Int.MIN_VALUE / 4
        for (move in moves) {
            val score = -negamax(position.apply(move), depth - 1, -beta, -alpha, deadline)
            best = max(best, score)
            alpha = max(alpha, score)
            if (alpha >= beta) break
        }
        return best
    }
}

fun skillToTime(skill: Int): Long = (80L + skill.coerceIn(1, 20) * 60L)
fun skillToDepth(skill: Int): Int = min(8, 2 + skill.coerceIn(1, 20) / 3)
