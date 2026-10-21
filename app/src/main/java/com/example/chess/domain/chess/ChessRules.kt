package com.example.chess.domain.chess

import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.GameResult
import com.example.chess.domain.model.Side
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side as LibSide
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import java.io.File
import java.util.UUID

data class LegalMove(
    val from: Square,
    val to: Square,
    val promotion: Piece? = null,
    val san: String,
    val uci: String,
)

data class ParsedGame(
    val white: String,
    val black: String,
    val result: GameResult,
    val pgn: String,
    val opening: String,
    val eco: String,
    val timeControl: String,
    val playedAt: Long,
    val whiteRating: Int?,
    val blackRating: Int?,
    val sans: List<String>,
    val startFen: Fen = Fen(Board.STARTING_POSITION_FEN ?: START_FEN),
)

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

class ChessRules @javax.inject.Inject constructor() {
    fun startBoard(): Board = Board().also { it.loadFromFen(START_FEN) }

    fun boardFromFen(fen: Fen): Board = Board().also { it.loadFromFen(fen.value) }

    fun fenOf(board: Board): Fen = Fen(board.fen)

    fun sideToMove(board: Board): Side =
        if (board.sideToMove == LibSide.WHITE) Side.WHITE else Side.BLACK

    fun pieceAt(board: Board, square: Square): Piece = board.getPiece(square)

    fun legalMoves(board: Board): List<LegalMove> {
        return board.legalMoves().map { move ->
            LegalMove(
                from = move.from,
                to = move.to,
                promotion = move.promotion.takeIf { it != Piece.NONE },
                san = sanFor(board, move),
                uci = move.toString().lowercase(),
            )
        }
    }

    fun legalMovesFrom(board: Board, from: Square): List<LegalMove> =
        legalMoves(board).filter { it.from == from }

    fun applyMove(board: Board, move: LegalMove): Board {
        val next = board.clone()
        val libMove = Move(move.from, move.to, move.promotion ?: Piece.NONE)
        next.doMove(libMove)
        return next
    }

    fun applyUci(board: Board, uci: String): Board {
        val next = board.clone()
        val from = Square.fromValue(uci.substring(0, 2).uppercase())
        val to = Square.fromValue(uci.substring(2, 4).uppercase())
        val promo = if (uci.length > 4) promotionPiece(uci[4], next.sideToMove) else Piece.NONE
        next.doMove(Move(from, to, promo))
        return next
    }

    fun applySan(board: Board, san: String): Board {
        val next = board.clone()
        val normalized = san.replace("+", "").replace("#", "").replace("x", "")
        val match = legalMoves(next).firstOrNull { candidate ->
            val c = candidate.san.replace("+", "").replace("#", "")
            c == san || c.replace("x", "") == normalized || candidate.uci == san.lowercase()
        } ?: error("Illegal SAN: $san in ${next.fen}")
        val libMove = Move(match.from, match.to, match.promotion ?: Piece.NONE)
        next.doMove(libMove)
        return next
    }

    fun applySanInPlace(board: Board, san: String): LegalMove {
        val match = legalMoves(board).firstOrNull { candidate ->
            val c = candidate.san.replace("+", "").replace("#", "")
            val target = san.replace("+", "").replace("#", "")
            c == san || c == target || c.replace("x", "") == target.replace("x", "")
        } ?: error("Illegal SAN: $san in ${board.fen}")
        board.doMove(Move(match.from, match.to, match.promotion ?: Piece.NONE))
        return match
    }

    private fun sanFor(board: Board, move: Move): String {
        val list = MoveList(board.fen)
        list.add(move)
        val sanLine = list.toSan()
        return sanLine.substringAfterLast('.').trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
            ?: move.toString().lowercase()
    }

    fun isGameOver(board: Board): Boolean = board.isMated || board.isDraw || board.isStaleMate

    fun resultOf(board: Board): GameResult = when {
        board.isMated && board.sideToMove == LibSide.WHITE -> GameResult.BLACK_WIN
        board.isMated -> GameResult.WHITE_WIN
        board.isDraw || board.isStaleMate -> GameResult.DRAW
        else -> GameResult.UNKNOWN
    }

    fun parsePgn(pgn: String): ParsedGame {
        val tags = pgnTags(pgn)
        val sans = extractSans(pgn)
        return ParsedGame(
            white = tags["White"] ?: "White",
            black = tags["Black"] ?: "Black",
            result = parseResult(tags["Result"]),
            pgn = pgn.trim(),
            opening = tags["Opening"] ?: detectOpening(sans),
            eco = tags["ECO"] ?: "",
            timeControl = tags["TimeControl"] ?: tags["Event"] ?: "",
            playedAt = parsePgnDate(tags["UTCDate"] ?: tags["Date"]),
            whiteRating = tags["WhiteElo"]?.toIntOrNull(),
            blackRating = tags["BlackElo"]?.toIntOrNull(),
            sans = sans,
        )
    }

    fun pgnFromMoves(white: String, black: String, sans: List<String>, result: GameResult): String {
        val resultTag = when (result) {
            GameResult.WHITE_WIN -> "1-0"
            GameResult.BLACK_WIN -> "0-1"
            GameResult.DRAW -> "1/2-1/2"
            GameResult.UNKNOWN -> "*"
        }
        val moves = buildString {
            sans.forEachIndexed { index, san ->
                if (index % 2 == 0) append("${index / 2 + 1}. ")
                append(san).append(' ')
            }
            append(resultTag)
        }
        return """
            [Event "ChessOS"]
            [White "$white"]
            [Black "$black"]
            [Result "$resultTag"]

            $moves
        """.trimIndent()
    }

    fun replaySans(sans: List<String>, start: Fen = Fen(START_FEN)): List<Pair<Fen, LegalMove>> {
        val board = boardFromFen(start)
        return sans.map { san ->
            val before = fenOf(board)
            val played = applySanInPlace(board, san)
            before to played
        }
    }

    fun detectOpening(sans: List<String>): String = OpeningBook.nameFor(sans)

    private fun promotionPiece(code: Char, side: LibSide): Piece = when (code.lowercaseChar()) {
        'q' -> if (side == LibSide.WHITE) Piece.WHITE_QUEEN else Piece.BLACK_QUEEN
        'r' -> if (side == LibSide.WHITE) Piece.WHITE_ROOK else Piece.BLACK_ROOK
        'b' -> if (side == LibSide.WHITE) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
        'n' -> if (side == LibSide.WHITE) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
        else -> Piece.NONE
    }

    companion object {
        fun pgnTags(pgn: String): Map<String, String> {
            val regex = Regex("""\[(\w+)\s+"([^"]*)"]""")
            return regex.findAll(pgn).associate { it.groupValues[1] to it.groupValues[2] }
        }

        fun extractSans(pgn: String): List<String> {
            val body = pgn.replace(Regex("""\[.*?]\s*"""), "")
                .replace(Regex("""\{[^}]*}"""), " ")
                .replace(Regex("""\([^)]*\)"""), " ")
                .replace(Regex("""\$\d+"""), " ")
            return body.split(Regex("\\s+"))
                .map { it.trim() }
                .filter { token ->
                    token.isNotBlank() &&
                        !token.matches(Regex("""\d+\.+""")) &&
                        token !in setOf("1-0", "0-1", "1/2-1/2", "*")
                }
        }

        fun parseResult(raw: String?): GameResult = when (raw) {
            "1-0" -> GameResult.WHITE_WIN
            "0-1" -> GameResult.BLACK_WIN
            "1/2-1/2" -> GameResult.DRAW
            else -> GameResult.UNKNOWN
        }

        fun parsePgnDate(raw: String?): Long {
            if (raw.isNullOrBlank() || raw.contains('?')) return System.currentTimeMillis()
            return runCatching {
                val parts = raw.split('.', '-', '/')
                val y = parts[0].toInt()
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                java.util.GregorianCalendar(y, m - 1, d).timeInMillis
            }.getOrDefault(System.currentTimeMillis())
        }
    }
}

object OpeningBook {
    private val lines = listOf(
        listOf("e4", "e5", "Nf3", "Nc6", "Bb5") to ("C60" to "Ruy Lopez"),
        listOf("e4", "e5", "Nf3", "Nc6", "Bc4") to ("C50" to "Italian Game"),
        listOf("e4", "c5") to ("B20" to "Sicilian Defence"),
        listOf("e4", "e6") to ("C00" to "French Defence"),
        listOf("e4", "c6") to ("B10" to "Caro-Kann"),
        listOf("d4", "d5", "c4") to ("D06" to "Queen's Gambit"),
        listOf("d4", "Nf6", "c4", "g6") to ("E60" to "King's Indian"),
        listOf("d4", "Nf6", "c4", "e6") to ("E00" to "Indian Defence"),
        listOf("Nf3", "d5") to ("A06" to "Reti Opening"),
        listOf("c4") to ("A10" to "English Opening"),
        listOf("e4", "e5") to ("C20" to "Open Game"),
        listOf("d4", "d5") to ("D00" to "Queen's Pawn"),
    )

    fun nameFor(sans: List<String>): String {
        val clean = sans.map { it.replace("+", "").replace("#", "") }
        return lines.firstOrNull { (prefix, _) ->
            clean.take(prefix.size) == prefix
        }?.second?.second ?: "Unknown Opening"
    }

    fun ecoFor(sans: List<String>): String {
        val clean = sans.map { it.replace("+", "").replace("#", "") }
        return lines.firstOrNull { (prefix, _) ->
            clean.take(prefix.size) == prefix
        }?.second?.first ?: ""
    }
}

fun newId(): String = UUID.randomUUID().toString()

fun writeTempPgn(pgn: String): File =
    File.createTempFile("chessos", ".pgn").apply { writeText(pgn) }

fun loadPgnHolder(pgn: String): PgnHolder {
    val file = writeTempPgn(pgn)
    return PgnHolder(file.absolutePath).also { it.loadPgn() }
}
