package com.example.chess.domain.chess

class Position private constructor(
    private val squares: Array<Piece?>,
    var sideToMove: Side,
    var castling: CastlingRights,
    var enPassant: Square?,
    var halfmove: Int,
    var fullmove: Int,
) {
    fun pieceAt(square: Square): Piece? = squares[square.index]

    fun clone(): Position = Position(
        squares = squares.copyOf(),
        sideToMove = sideToMove,
        castling = castling,
        enPassant = enPassant,
        halfmove = halfmove,
        fullmove = fullmove,
    )

    fun fen(): String = Fen.encode(this)

    fun occupancy(): List<Pair<Square, Piece>> = buildList {
        for (i in 0 until 64) {
            val piece = squares[i] ?: continue
            add(Square(i) to piece)
        }
    }

    fun kingSquare(side: Side): Square? {
        for (i in 0 until 64) {
            val p = squares[i] ?: continue
            if (p.side == side && p.type == PieceType.KING) return Square(i)
        }
        return null
    }

    fun isAttacked(square: Square, by: Side): Boolean {
        val file = square.file
        val rank = square.rank
        val pawnDir = if (by.isWhite) 1 else -1
        for (df in intArrayOf(-1, 1)) {
            val f = file + df
            val r = rank - pawnDir
            if (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null && p.side == by && p.type == PieceType.PAWN) return true
            }
        }
        for (d in KNIGHT_DELTAS) {
            val f = file + d.first
            val r = rank + d.second
            if (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null && p.side == by && p.type == PieceType.KNIGHT) return true
            }
        }
        for (d in KING_DELTAS) {
            val f = file + d.first
            val r = rank + d.second
            if (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null && p.side == by && p.type == PieceType.KING) return true
            }
        }
        if (rayAttacked(file, rank, by, BISHOP_RAYS, setOf(PieceType.BISHOP, PieceType.QUEEN))) return true
        if (rayAttacked(file, rank, by, ROOK_RAYS, setOf(PieceType.ROOK, PieceType.QUEEN))) return true
        return false
    }

    fun inCheck(side: Side = sideToMove): Boolean {
        val king = kingSquare(side) ?: return false
        return isAttacked(king, side.other)
    }

    fun legalMoves(): List<Move> {
        val pseudo = pseudoMoves()
        return pseudo.filter { move ->
            val next = clone()
            next.applyUnchecked(move)
            !next.inCheck(sideToMove)
        }
    }

    fun apply(move: Move): Position {
        val next = clone()
        next.applyUnchecked(move)
        next.sideToMove = sideToMove.other
        if (!sideToMove.isWhite) next.fullmove += 1
        return next
    }

    fun applyUci(uci: String): Position {
        val move = legalMoves().firstOrNull { it.uci == uci }
            ?: error("Illegal move $uci")
        return apply(move)
    }

    fun status(): GameStatus {
        val moves = legalMoves()
        if (moves.isNotEmpty()) {
            return if (halfmove >= 100) GameStatus.DRAW else GameStatus.ONGOING
        }
        return if (inCheck()) {
            if (sideToMove.isWhite) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS
        } else {
            GameStatus.DRAW
        }
    }

    internal fun applyUnchecked(move: Move) {
        val moving = squares[move.from.index] ?: return
        val captured = squares[move.to.index]
        squares[move.from.index] = null
        var resetHalf = moving.type == PieceType.PAWN || captured != null || move.isEnPassant
        if (move.isEnPassant) {
            val capRank = if (moving.side.isWhite) move.to.rank - 1 else move.to.rank + 1
            squares[capRank * 8 + move.to.file] = null
            resetHalf = true
        }
        val placed = if (move.promotion != null) Piece(move.promotion, moving.side) else moving
        squares[move.to.index] = placed
        if (move.isCastle) {
            when (move.to.index) {
                6 -> {
                    squares[7] = null
                    squares[5] = Piece(PieceType.ROOK, Side.WHITE)
                }
                2 -> {
                    squares[0] = null
                    squares[3] = Piece(PieceType.ROOK, Side.WHITE)
                }
                62 -> {
                    squares[63] = null
                    squares[61] = Piece(PieceType.ROOK, Side.BLACK)
                }
                58 -> {
                    squares[56] = null
                    squares[59] = Piece(PieceType.ROOK, Side.BLACK)
                }
            }
        }
        var wk = castling.whiteKing
        var wq = castling.whiteQueen
        var bk = castling.blackKing
        var bq = castling.blackQueen
        if (moving.type == PieceType.KING && moving.side.isWhite) {
            wk = false; wq = false
        }
        if (moving.type == PieceType.KING && !moving.side.isWhite) {
            bk = false; bq = false
        }
        if (move.from.index == 0 || move.to.index == 0) wq = false
        if (move.from.index == 7 || move.to.index == 7) wk = false
        if (move.from.index == 56 || move.to.index == 56) bq = false
        if (move.from.index == 63 || move.to.index == 63) bk = false
        castling = CastlingRights(wk, wq, bk, bq)
        enPassant = if (moving.type == PieceType.PAWN && kotlin.math.abs(move.to.rank - move.from.rank) == 2) {
            Square.of(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else {
            null
        }
        halfmove = if (resetHalf) 0 else halfmove + 1
    }

    private fun pseudoMoves(): List<Move> = buildList {
        for (i in 0 until 64) {
            val piece = squares[i] ?: continue
            if (piece.side != sideToMove) continue
            val from = Square(i)
            when (piece.type) {
                PieceType.PAWN -> addAll(pawnMoves(from, piece.side))
                PieceType.KNIGHT -> addLeaps(from, KNIGHT_DELTAS, piece.side)
                PieceType.KING -> {
                    addLeaps(from, KING_DELTAS, piece.side)
                    addCastles(from, piece.side)
                }
                PieceType.BISHOP -> addRays(from, piece.side, BISHOP_RAYS)
                PieceType.ROOK -> addRays(from, piece.side, ROOK_RAYS)
                PieceType.QUEEN -> {
                    addRays(from, piece.side, BISHOP_RAYS)
                    addRays(from, piece.side, ROOK_RAYS)
                }
            }
        }
    }

    private fun MutableList<Move>.addLeaps(from: Square, deltas: Array<Pair<Int, Int>>, side: Side) {
        for (d in deltas) {
            val f = from.file + d.first
            val r = from.rank + d.second
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square.of(f, r)
            val target = squares[to.index]
            if (target == null) {
                add(Move(from, to))
            } else if (target.side != side) {
                add(Move(from, to, isCapture = true))
            }
        }
    }

    private fun MutableList<Move>.addRays(from: Square, side: Side, rays: Array<Pair<Int, Int>>) {
        for (d in rays) {
            var f = from.file + d.first
            var r = from.rank + d.second
            while (f in 0..7 && r in 0..7) {
                val to = Square.of(f, r)
                val target = squares[to.index]
                if (target == null) {
                    add(Move(from, to))
                } else {
                    if (target.side != side) add(Move(from, to, isCapture = true))
                    break
                }
                f += d.first
                r += d.second
            }
        }
    }

    private fun pawnMoves(from: Square, side: Side): List<Move> = buildList {
        val dir = if (side.isWhite) 1 else -1
        val startRank = if (side.isWhite) 1 else 6
        val promoRank = if (side.isWhite) 7 else 0
        val one = from.rank + dir
        if (one in 0..7 && squares[one * 8 + from.file] == null) {
            addPawn(from, Square.of(from.file, one), promoRank, capture = false)
            val two = from.rank + 2 * dir
            if (from.rank == startRank && squares[two * 8 + from.file] == null) {
                add(Move(from, Square.of(from.file, two)))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val f = from.file + df
            val r = from.rank + dir
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square.of(f, r)
            val target = squares[to.index]
            if (target != null && target.side != side) {
                addPawn(from, to, promoRank, capture = true)
            } else if (enPassant == to) {
                add(Move(from, to, isCapture = true, isEnPassant = true))
            }
        }
    }

    private fun MutableList<Move>.addPawn(from: Square, to: Square, promoRank: Int, capture: Boolean) {
        if (to.rank == promoRank) {
            for (promo in PROMOTIONS) {
                add(Move(from, to, promotion = promo, isCapture = capture))
            }
        } else {
            add(Move(from, to, isCapture = capture))
        }
    }

    private fun MutableList<Move>.addCastles(from: Square, side: Side) {
        if (inCheck(side)) return
        if (side.isWhite && from.index == 4) {
            if (castling.whiteKing && squares[5] == null && squares[6] == null &&
                !isAttacked(Square(5), Side.BLACK) && !isAttacked(Square(6), Side.BLACK)
            ) {
                add(Move(from, Square(6), isCastle = true))
            }
            if (castling.whiteQueen && squares[1] == null && squares[2] == null && squares[3] == null &&
                !isAttacked(Square(3), Side.BLACK) && !isAttacked(Square(2), Side.BLACK)
            ) {
                add(Move(from, Square(2), isCastle = true))
            }
        }
        if (!side.isWhite && from.index == 60) {
            if (castling.blackKing && squares[61] == null && squares[62] == null &&
                !isAttacked(Square(61), Side.WHITE) && !isAttacked(Square(62), Side.WHITE)
            ) {
                add(Move(from, Square(62), isCastle = true))
            }
            if (castling.blackQueen && squares[57] == null && squares[58] == null && squares[59] == null &&
                !isAttacked(Square(59), Side.WHITE) && !isAttacked(Square(58), Side.WHITE)
            ) {
                add(Move(from, Square(58), isCastle = true))
            }
        }
    }

    private fun rayAttacked(
        file: Int,
        rank: Int,
        by: Side,
        rays: Array<Pair<Int, Int>>,
        types: Set<PieceType>,
    ): Boolean {
        for (d in rays) {
            var f = file + d.first
            var r = rank + d.second
            while (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null) {
                    if (p.side == by && p.type in types) return true
                    break
                }
                f += d.first
                r += d.second
            }
        }
        return false
    }

    companion object {
        private val KNIGHT_DELTAS = arrayOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2,
        )
        private val KING_DELTAS = arrayOf(
            1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
        )
        private val BISHOP_RAYS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ROOK_RAYS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val PROMOTIONS = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        fun empty(): Position = Position(
            squares = arrayOfNulls(64),
            sideToMove = Side.WHITE,
            castling = CastlingRights(false, false, false, false),
            enPassant = null,
            halfmove = 0,
            fullmove = 1,
        )

        fun start(): Position = Fen.parse(Fen.START)

        fun fromFen(fen: String): Position = Fen.parse(fen)

        fun fromSquares(
            squares: Array<Piece?>,
            sideToMove: Side,
            castling: CastlingRights,
            enPassant: Square?,
            halfmove: Int,
            fullmove: Int,
        ): Position = Position(squares, sideToMove, castling, enPassant, halfmove, fullmove)
    }
}

enum class GameStatus { ONGOING, WHITE_WINS, BLACK_WINS, DRAW }
