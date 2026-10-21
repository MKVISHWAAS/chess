package com.example.chess.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.analysis.round1
import com.example.chess.ui.components.SectionTitle
import com.example.chess.ui.components.Sparkline
import com.example.chess.work.AnalyzeGameWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    val snapshot = repository.snapshot
    var coach by mutableStateOf<String?>(null)

    fun enqueue(gameId: String) {
        val request = OneTimeWorkRequestBuilder<AnalyzeGameWorker>()
            .setInputData(workDataOf(AnalyzeGameWorker.KEY_GAME_ID to gameId))
            .build()
        workManager.enqueue(request)
    }

    fun analyzeNow(gameId: String) {
        viewModelScope.launch { runCatching { repository.analyzeGame(gameId) } }
    }

    fun summarize(gameId: String) {
        viewModelScope.launch {
            val game = repository.current().games.first { it.id == gameId }
            coach = repository.coachSummary(game)
        }
    }
}

@Composable
fun GameDetailScreen(
    gameId: String,
    onAnalyzeBoard: (String) -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(com.example.chess.domain.model.ChessSnapshot())
    val game = snap.games.firstOrNull { it.id == gameId }
    val analysis = snap.analyses.firstOrNull { it.gameId == gameId }
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (game == null) {
            Text("Game not found")
        } else {
        SectionTitle("${game.white} vs ${game.black}")
        Text("${game.opening} · ${game.result} · ACPL ${game.acpl.round1()}")
        Button({ vm.analyzeNow(game.id) }) { Text("Analyze now") }
        Button({ vm.enqueue(game.id) }) { Text("Queue background analysis") }
        Button({ vm.summarize(game.id) }) { Text("Coach summary") }
        Button({ clipboard.setText(AnnotatedString(game.pgn.ifBlank { game.fen })) }) { Text("Export PGN/FEN") }
        analysis?.let {
            Sparkline(it.moves.map { move -> move.evalCp / 100f })
            it.moves.forEach { move ->
                Text("${move.ply + 1}. ${move.san}  ${move.evalCp}  ${move.classification ?: ""}")
            }
            it.moves.firstOrNull()?.let { first ->
                Button({ onAnalyzeBoard(first.fenBefore) }) { Text("Open on board") }
            }
        }
        vm.coach?.let { Text(it) }
        Text(game.pgn)
        }
    }
}
