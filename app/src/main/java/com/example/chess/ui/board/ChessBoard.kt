package com.example.chess.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.chess.domain.chess.Move
import com.example.chess.domain.chess.Position
import com.example.chess.domain.chess.Square
import com.example.chess.ui.theme.BoardDark
import com.example.chess.ui.theme.BoardLast
import com.example.chess.ui.theme.BoardLight
import com.example.chess.ui.theme.BoardSelected

@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    selected: Square? = null,
    legalTargets: Set<Square> = emptySet(),
    lastMove: Move? = null,
    onSquare: (Square) -> Unit = {},
    enabled: Boolean = true,
) {
    val ranks = if (flipped) 0..7 else 7 downTo 0
    val files = if (flipped) 7 downTo 0 else 0..7
    Column(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        for (rank in ranks) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (file in files) {
                    val square = Square.of(file, rank)
                    val light = (file + rank) % 2 == 1
                    val bg = when {
                        selected == square -> BoardSelected
                        lastMove != null && (lastMove.from == square || lastMove.to == square) -> BoardLast
                        light -> BoardLight
                        else -> BoardDark
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(bg)
                            .then(if (enabled) Modifier.clickable { onSquare(square) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        val piece = position.pieceAt(square)
                        if (piece != null) {
                            Text(piece.glyph, fontSize = 28.sp, textAlign = TextAlign.Center)
                        } else if (square in legalTargets) {
                            Box(
                                Modifier
                                    .fillMaxSize(0.28f)
                                    .background(BoardSelected.copy(alpha = 0.75f), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
