package com.example.chess.domain.chess

@JvmInline
value class Square(val index: Int) {
    val file: Int get() = index and 7
    val rank: Int get() = index shr 3
    val algebraic: String get() = "${'a' + file}${rank + 1}"

    companion object {
        fun of(file: Int, rank: Int): Square = Square(rank * 8 + file)

        fun parse(name: String): Square {
            require(name.length == 2)
            val file = name[0].lowercaseChar() - 'a'
            val rank = name[1] - '1'
            require(file in 0..7 && rank in 0..7)
            return of(file, rank)
        }
    }
}

enum class Side {
    WHITE, BLACK;

    val other: Side get() = if (this == WHITE) BLACK else WHITE
    val isWhite: Boolean get() = this == WHITE
}

enum class PieceType {
    PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
}

data class Piece(val type: PieceType, val side: Side) {
    val fen: Char
        get() {
            val c = when (type) {
                PieceType.PAWN -> 'p'
                PieceType.KNIGHT -> 'n'
                PieceType.BISHOP -> 'b'
                PieceType.ROOK -> 'r'
                PieceType.QUEEN -> 'q'
                PieceType.KING -> 'k'
            }
            return if (side.isWhite) c.uppercaseChar() else c
        }

    val glyph: String
        get() = when (side to type) {
            Side.WHITE to PieceType.KING -> "♔"
            Side.WHITE to PieceType.QUEEN -> "♕"
            Side.WHITE to PieceType.ROOK -> "♖"
            Side.WHITE to PieceType.BISHOP -> "♗"
            Side.WHITE to PieceType.KNIGHT -> "♘"
            Side.WHITE to PieceType.PAWN -> "♙"
            Side.BLACK to PieceType.KING -> "♚"
            Side.BLACK to PieceType.QUEEN -> "♛"
            Side.BLACK to PieceType.ROOK -> "♜"
            Side.BLACK to PieceType.BISHOP -> "♝"
            Side.BLACK to PieceType.KNIGHT -> "♞"
            else -> "♟"
        }

    companion object {
        fun fromFen(c: Char): Piece? {
            val type = when (c.lowercaseChar()) {
                'p' -> PieceType.PAWN
                'n' -> PieceType.KNIGHT
                'b' -> PieceType.BISHOP
                'r' -> PieceType.ROOK
                'q' -> PieceType.QUEEN
                'k' -> PieceType.KING
                else -> return null
            }
            return Piece(type, if (c.isUpperCase()) Side.WHITE else Side.BLACK)
        }
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCapture: Boolean = false,
    val isCastle: Boolean = false,
    val isEnPassant: Boolean = false,
) {
    val uci: String
        get() = buildString {
            append(from.algebraic)
            append(to.algebraic)
            when (promotion) {
                PieceType.QUEEN -> append('q')
                PieceType.ROOK -> append('r')
                PieceType.BISHOP -> append('b')
                PieceType.KNIGHT -> append('n')
                else -> Unit
            }
        }
}

data class CastlingRights(
    val whiteKing: Boolean = true,
    val whiteQueen: Boolean = true,
    val blackKing: Boolean = true,
    val blackQueen: Boolean = true,
) {
    fun fen(): String {
        val s = buildString {
            if (whiteKing) append('K')
            if (whiteQueen) append('Q')
            if (blackKing) append('k')
            if (blackQueen) append('q')
        }
        return s.ifEmpty { "-" }
    }

    companion object {
        fun parse(token: String): CastlingRights {
            if (token == "-") return CastlingRights(false, false, false, false)
            return CastlingRights(
                whiteKing = 'K' in token,
                whiteQueen = 'Q' in token,
                blackKing = 'k' in token,
                blackQueen = 'q' in token,
            )
        }
    }
}
