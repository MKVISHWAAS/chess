package com.example.chess.data.engine

import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.START_FEN
import com.example.chess.domain.model.EngineEval
import com.example.chess.domain.model.Fen
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BuiltinEngine(
    private val rules: ChessRules,
) : ChessEngine {
    override val name: String = "ChessOS NNUE-style (builtin)"

    override suspend fun evaluate(fen: Fen, depth: Int, moveTimeMs: Long): EngineEval =
        withContext(Dispatchers.Default) {
            search(fen, depth.coerceIn(2, 6), moveTimeMs)
        }

    override suspend fun bestMove(fen: Fen, skillLevel: Int, moveTimeMs: Long): String =
        withContext(Dispatchers.Default) {
            val eval = search(fen, (2 + skillLevel / 4).coerceIn(2, 5), moveTimeMs)
            val legal = rules.legalMoves(rules.boardFromFen(fen))
            if (skillLevel < 4 && legal.isNotEmpty() && Random.nextFloat() < 0.35f) {
                legal.random().uci
            } else {
                eval.bestMoveUci.ifBlank { legal.firstOrNull()?.uci.orEmpty() }
            }
        }

    private fun search(fen: Fen, depth: Int, moveTimeMs: Long): EngineEval {
        val board = rules.boardFromFen(fen)
        val deadline = System.currentTimeMillis() + moveTimeMs
        var best = EngineEval(depth = 0, scoreCp = staticEval(board), mate = null, pvUci = emptyList(), pvSan = "", bestMoveUci = "")
        for (d in 1..depth) {
            if (System.currentTimeMillis() > deadline) break
            val result = alphaBeta(board, d, Int.MIN_VALUE / 4, Int.MAX_VALUE / 4, deadline, mutableListOf())
            if (result.bestMoveUci.isNotBlank()) best = result.copy(depth = d)
        }
        val san = rules.pvToSan(fen, best.pvUci)
        return best.copy(pvSan = san)
    }

    private fun alphaBeta(
        board: Board,
        depth: Int,
        alpha0: Int,
        beta0: Int,
        deadline: Long,
        pv: MutableList<String>,
    ): EngineEval {
        if (System.currentTimeMillis() > deadline || depth == 0 || rules.isGameOver(board)) {
            val score = sideScore(board)
            return EngineEval(depth, score, mateScore(board), emptyList(), "", "")
        }
        var alpha = alpha0
        val beta = beta0
        var bestScore = Int.MIN_VALUE / 8
        var bestMove = ""
        var bestPv = emptyList<String>()
        val moves = board.legalMoves().sortedByDescending { captureBonus(board, it) }
        for (move in moves) {
            board.doMove(move)
            val childPv = mutableListOf<String>()
            val child = alphaBeta(board, depth - 1, -beta, -alpha, deadline, childPv)
            board.undoMove()
            val score = -(child.scoreCp ?: 0)
            if (score > bestScore) {
                bestScore = score
                bestMove = move.toString().lowercase()
                bestPv = listOf(bestMove) + childPv
            }
            alpha = max(alpha, score)
            if (alpha >= beta) break
        }
        pv.clear()
        pv.addAll(bestPv)
        return EngineEval(
            depth = depth,
            scoreCp = bestScore,
            mate = null,
            pvUci = bestPv,
            pvSan = "",
            bestMoveUci = bestMove,
        )
    }

    private fun sideScore(board: Board): Int {
        val white = staticEval(board)
        return if (board.sideToMove == Side.WHITE) white else -white
    }

    private fun mateScore(board: Board): Int? = when {
        board.isMated -> if (board.sideToMove == Side.WHITE) -1 else 1
        else -> null
    }

    private fun captureBonus(board: Board, move: Move): Int {
        val captured = board.getPiece(move.to)
        return pieceValue(captured)
    }

    companion object {
        private val PST_PAWN = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0,
        )

        fun staticEval(board: Board): Int {
            var score = 0
            for (sq in com.github.bhlangonijr.chesslib.Square.entries) {
                if (sq == com.github.bhlangonijr.chesslib.Square.NONE) continue
                val piece = board.getPiece(sq)
                if (piece == Piece.NONE) continue
                val value = pieceValue(piece) + pst(piece, sq.ordinal)
                score += if (piece.pieceSide == Side.WHITE) value else -value
            }
            return score
        }

        private fun pst(piece: Piece, square: Int): Int {
            val idx = if (piece.pieceSide == Side.WHITE) 63 - square else square
            return when (piece.pieceType) {
                com.github.bhlangonijr.chesslib.PieceType.PAWN -> PST_PAWN.getOrElse(idx) { 0 } / 5
                else -> 0
            }
        }

        fun pieceValue(piece: Piece): Int = when (piece.pieceType) {
            com.github.bhlangonijr.chesslib.PieceType.PAWN -> 100
            com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> 320
            com.github.bhlangonijr.chesslib.PieceType.BISHOP -> 330
            com.github.bhlangonijr.chesslib.PieceType.ROOK -> 500
            com.github.bhlangonijr.chesslib.PieceType.QUEEN -> 900
            com.github.bhlangonijr.chesslib.PieceType.KING -> 20_000
            else -> 0
        }
    }
}

fun unusedStartFen(): String = START_FEN
