package com.example.chess.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.ui.board.ChessBoardView

@Composable
fun PlayScreen(vm: PlayViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ui.status)
        ChessBoardView(
            board = ui.board,
            flipped = ui.flipped,
            selected = ui.selected,
            legal = ui.legal,
            lastFrom = ui.lastFrom,
            lastTo = ui.lastTo,
            onSquare = vm::tap,
        )
        Text(ui.sans.joinToString(" "))
        Button(onClick = vm::newGame) { Text("New game") }
    }
}
