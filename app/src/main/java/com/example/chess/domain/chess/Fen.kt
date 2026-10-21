package com.example.chess.domain.chess

object Fen {
    const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(fen: String): Position {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size >= 4) { "Invalid FEN" }
        val squares = arrayOfNulls<Piece>(64)
        val ranks = parts[0].split("/")
        require(ranks.size == 8) { "Invalid FEN ranks" }
        for (rankFromTop in 0 until 8) {
            val rank = 7 - rankFromTop
            var file = 0
            for (c in ranks[rankFromTop]) {
                when {
                    c.isDigit() -> file += c - '0'
                    else -> {
                        val piece = Piece.fromFen(c) ?: error("Bad piece $c")
                        squares[rank * 8 + file] = piece
                        file++
                    }
                }
            }
        }
        val side = if (parts[1] == "b") Side.BLACK else Side.WHITE
        val castling = CastlingRights.parse(parts[2])
        val ep = parts[3].takeIf { it != "-" }?.let(Square::parse)
        val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
        return Position.fromSquares(squares, side, castling, ep, half, full)
    }

    fun encode(position: Position): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = position.pieceAt(Square.of(file, rank))
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        append(empty)
                        empty = 0
                    }
                    append(piece.fen)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(' ')
        append(if (position.sideToMove.isWhite) 'w' else 'b')
        append(' ')
        append(position.castling.fen())
        append(' ')
        append(position.enPassant?.algebraic ?: "-")
        append(' ')
        append(position.halfmove)
        append(' ')
        append(position.fullmove)
    }
}

fun String.toPosition(): Position = Fen.parse(this)
