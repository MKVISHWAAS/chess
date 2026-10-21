package com.example.chess.domain.chess

object San {
    fun of(position: Position, move: Move): String {
        if (move.isCastle) {
            return if (move.to.file == 6) "O-O" else "O-O-O"
        }
        val piece = position.pieceAt(move.from) ?: return move.uci
        val next = position.apply(move)
        val check = when {
            next.status() == GameStatus.WHITE_WINS || next.status() == GameStatus.BLACK_WINS -> "#"
            next.inCheck() -> "+"
            else -> ""
        }
        if (piece.type == PieceType.PAWN) {
            val body = buildString {
                if (move.isCapture || move.isEnPassant) {
                    append('a' + move.from.file)
                    append('x')
                }
                append(move.to.algebraic)
                if (move.promotion != null) {
                    append('=')
                    append(letter(move.promotion).uppercaseChar())
                }
            }
            return body + check
        }
        val letter = letter(piece.type).uppercaseChar()
        val others = position.legalMoves().filter { other ->
            other != move &&
                position.pieceAt(other.from)?.type == piece.type &&
                position.pieceAt(other.from)?.side == piece.side &&
                other.to == move.to
        }
        val disambiguate = when {
            others.isEmpty() -> ""
            others.none { it.from.file == move.from.file } -> "${'a' + move.from.file}"
            others.none { it.from.rank == move.from.rank } -> "${move.from.rank + 1}"
            else -> move.from.algebraic
        }
        return buildString {
            append(letter)
            append(disambiguate)
            if (move.isCapture) append('x')
            append(move.to.algebraic)
            append(check)
        }
    }

    fun parse(position: Position, sanRaw: String): Move {
        val san = sanRaw.trim().removeSuffix("!").removeSuffix("?").removeSuffix("+").removeSuffix("#")
            .replace("x", "")
        if (san == "O-O" || san == "0-0") {
            return position.legalMoves().first { it.isCastle && it.to.file == 6 }
        }
        if (san == "O-O-O" || san == "0-0-0") {
            return position.legalMoves().first { it.isCastle && it.to.file == 2 }
        }
        val promotion = if ("=" in san) {
            when (san.substringAfter("=").first().lowercaseChar()) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> null
            }
        } else {
            null
        }
        val destToken = san.substringBefore("=").takeLast(2)
        val dest = Square.parse(destToken)
        val pieceType = when (val c = san.first()) {
            'N' -> PieceType.KNIGHT
            'B' -> PieceType.BISHOP
            'R' -> PieceType.ROOK
            'Q' -> PieceType.QUEEN
            'K' -> PieceType.KING
            else -> PieceType.PAWN
        }
        val prefix = san.substringBefore("=").dropLast(2).dropWhile { it.isUpperCase() && it != 'O' }
        val fileHint = prefix.firstOrNull { it in 'a'..'h' }?.let { it - 'a' }
        val rankHint = prefix.firstOrNull { it in '1'..'8' }?.let { it - '1' }
        return position.legalMoves().first { move ->
            move.to == dest &&
                position.pieceAt(move.from)?.type == pieceType &&
                (promotion == null || move.promotion == promotion) &&
                (fileHint == null || move.from.file == fileHint) &&
                (rankHint == null || move.from.rank == rankHint)
        }
    }

    private fun letter(type: PieceType): Char = when (type) {
        PieceType.PAWN -> 'P'
        PieceType.KNIGHT -> 'n'
        PieceType.BISHOP -> 'b'
        PieceType.ROOK -> 'r'
        PieceType.QUEEN -> 'q'
        PieceType.KING -> 'k'
    }
}
