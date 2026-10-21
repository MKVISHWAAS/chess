package com.example.chess.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chess.domain.chess.ChessRules
import com.example.chess.ui.board.ChessBoardView
import com.example.chess.ui.components.EvalGraph

@Composable
fun AnalysisScreen(vm: AnalysisViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ui.game?.let { "${it.white} vs ${it.black} · ${it.opening}" } ?: "Analysis")
        ChessBoardView(
            board = ui.board,
            flipped = false,
            selected = null,
            legal = emptyList(),
            onSquare = {},
        )
        EvalGraph(vm.evalSeries())
        Text(ui.engineLine)
        vm.currentMove()?.let { Text("${it.san} · ${it.classification} · PV ${it.pv}") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { vm.goTo(ui.ply - 1) }) { Text("Prev") }
            Button(onClick = { vm.goTo(ui.ply + 1) }) { Text("Next") }
            Button(onClick = vm::analyzeNow, enabled = !ui.busy) { Text(if (ui.busy) "Analyzing…" else "Analyze") }
            Button(onClick = vm::summarize) { Text("AI summary") }
        }
        ui.analysis?.let {
            Text("Accuracy W ${"%.1f".format(it.accuracyWhite)}  B ${"%.1f".format(it.accuracyBlack)}")
            Text("ACPL W ${it.acplWhite}  B ${it.acplBlack}")
        }
        if (ui.summary.isNotBlank()) Text(ui.summary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ui.analysis?.moves.orEmpty().forEach { move ->
                FilterChip(
                    selected = move.ply == ui.ply,
                    onClick = { vm.goTo(move.ply) },
                    label = { Text("${move.ply / 2 + 1}${if (move.ply % 2 == 0) "." else "..."} ${move.san}") },
                )
            }
        }
    }
}

@Composable
fun FenLabScreen(vm: FenViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val legal = if (ui.selected != null) ChessRules().legalMovesFrom(ui.board, ui.selected) else emptyList()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("FEN / position lab")
        OutlinedTextField(ui.fen, vm::setFen, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::load) { Text("Load FEN") }
            Button(onClick = vm::analyze) { Text("Engine") }
        }
        ui.error?.let { Text(it) }
        ChessBoardView(ui.board, false, ui.selected, legal, onSquare = vm::tap)
        Text("Eval ${ui.eval}")
        Text(ui.pv)
        Text("Export FEN: ${ui.board.fen}")
    }
}
