package com.example.chess.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.domain.chess.LegalMove
import com.example.chess.ui.theme.BoardDark
import com.example.chess.ui.theme.BoardLast
import com.example.chess.ui.theme.BoardLight
import com.example.chess.ui.theme.BoardSelected
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square

@Composable
fun ChessBoardView(
    board: Board,
    flipped: Boolean,
    selected: Square?,
    legal: List<LegalMove>,
    lastFrom: Square? = null,
    lastTo: Square? = null,
    onSquare: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    val files = if (flipped) 7 downTo 0 else 0..7
    val ranks = if (flipped) 0..7 else 7 downTo 0
    Column(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        for (rank in ranks) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (file in files) {
                    val square = Square.squareAt(rank * 8 + file)
                    val light = (file + rank) % 2 == 1
                    val dest = legal.any { it.to == square }
                    val bg = when {
                        square == selected -> BoardSelected
                        square == lastFrom || square == lastTo -> BoardLast
                        light -> BoardLight
                        else -> BoardDark
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(bg)
                            .clickable { onSquare(square) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val piece = board.getPiece(square)
                        if (piece != Piece.NONE) {
                            Text(
                                text = pieceGlyph(piece),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (piece.pieceSide == Side.WHITE) Color(0xFFF8F4EA) else Color(0xFF16120C),
                            )
                        } else if (dest) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x66000000))
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun pieceGlyph(piece: Piece): String = when (piece) {
    Piece.WHITE_KING -> "♔"
    Piece.WHITE_QUEEN -> "♕"
    Piece.WHITE_ROOK -> "♖"
    Piece.WHITE_BISHOP -> "♗"
    Piece.WHITE_KNIGHT -> "♘"
    Piece.WHITE_PAWN -> "♙"
    Piece.BLACK_KING -> "♚"
    Piece.BLACK_QUEEN -> "♛"
    Piece.BLACK_ROOK -> "♜"
    Piece.BLACK_BISHOP -> "♝"
    Piece.BLACK_KNIGHT -> "♞"
    Piece.BLACK_PAWN -> "♟"
    else -> ""
}
