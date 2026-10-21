package com.example.chess.domain.engine

import com.example.chess.domain.chess.PieceType
import com.example.chess.domain.chess.Position
import com.example.chess.domain.chess.Side
import kotlin.math.abs

object Evaluator {
    private val pawnPst = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val knightPst = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )

    fun evaluate(position: Position): Int {
        var score = 0
        for ((square, piece) in position.occupancy()) {
            val value = when (piece.type) {
                PieceType.PAWN -> 100
                PieceType.KNIGHT -> 320
                PieceType.BISHOP -> 330
                PieceType.ROOK -> 500
                PieceType.QUEEN -> 900
                PieceType.KING -> 0
            }
            val index = if (piece.side.isWhite) square.index.mirror() else square.index
            val pst = when (piece.type) {
                PieceType.PAWN -> pawnPst[index]
                PieceType.KNIGHT, PieceType.BISHOP -> knightPst[index]
                else -> 0
            }
            val signed = if (piece.side.isWhite) value + pst else -(value + pst)
            score += signed
        }
        score += position.legalMoves().size / 2
        return if (position.sideToMove.isWhite) score else -score
    }

    private fun Int.mirror(): Int {
        val file = this and 7
        val rank = this shr 3
        return (7 - rank) * 8 + file
    }
}

data class SearchResult(
    val bestMoveUci: String?,
    val evalCp: Int,
    val pv: List<String>,
    val depth: Int,
    val backend: String,
)

fun classifyDrop(dropCp: Int): com.example.chess.domain.model.MistakeKind? = when {
    dropCp >= 200 -> com.example.chess.domain.model.MistakeKind.BLUNDER
    dropCp >= 100 -> com.example.chess.domain.model.MistakeKind.MISTAKE
    dropCp >= 50 -> com.example.chess.domain.model.MistakeKind.INACCURACY
    else -> null
}

fun Int.whitePerspective(side: Side): Int = if (side.isWhite) this else -this

fun Int.formatEval(): String {
    val pawns = this / 100.0
    val sign = if (this > 0) "+" else ""
    return "$sign${"%.2f".format(pawns)}"
}

fun evalBar(cp: Int): Float {
    val clamped = cp.coerceIn(-800, 800)
    return (clamped + 800) / 1600f
}

fun absDrop(before: Int, after: Int): Int = abs(before - after)
