package com.example.chess.ui.games

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.chess.data.remote.PlatformImporter
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.OpeningBook
import com.example.chess.domain.chess.newId
import com.example.chess.domain.model.GameSource
import com.example.chess.domain.model.StoredGame
import com.example.chess.work.AnalyzeGameWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val repository: ChessRepository,
    private val importer: PlatformImporter,
    private val rules: ChessRules,
    private val workManager: WorkManager,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val source = MutableStateFlow<String?>(null)
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    val profile = repository.signedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val games = combine(repository.games, query, source) { games, q, src ->
        games.filter {
            val matches = q.isBlank() ||
                it.white.contains(q, true) ||
                it.black.contains(q, true) ||
                it.opening.contains(q, true)
            matches && (src == null || it.source.name == src)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSource(value: String?) {
        source.value = value
    }

    fun importPgn(pgn: String) {
        viewModelScope.launch {
            runCatching {
                val parsed = rules.parsePgn(pgn)
                val game = StoredGame(
                    id = newId(),
                    source = GameSource.MANUAL,
                    white = parsed.white,
                    black = parsed.black,
                    result = parsed.result,
                    pgn = parsed.pgn,
                    opening = parsed.opening.ifBlank { OpeningBook.nameFor(parsed.sans) },
                    eco = parsed.eco.ifBlank { OpeningBook.ecoFor(parsed.sans) },
                    timeControl = parsed.timeControl,
                    playedAt = parsed.playedAt,
                    whiteRating = parsed.whiteRating,
                    blackRating = parsed.blackRating,
                )
                repository.upsertGame(game)
                enqueue(game)
                _status.value = "Imported ${game.white} vs ${game.black}"
            }.onFailure { _status.value = it.message }
        }
    }

    fun importChessCom() {
        viewModelScope.launch {
            val user = profile.value?.chessComUsername.orEmpty()
            if (user.isBlank()) {
                _status.value = "Set Chess.com username in Profile"
                return@launch
            }
            runCatching {
                val (games, ratings) = importer.importChessCom(user)
                games.forEach {
                    repository.upsertGame(it)
                    enqueue(it)
                }
                repository.addRatings(ratings)
                _status.value = "Imported ${games.size} Chess.com games"
            }.onFailure { _status.value = it.message }
        }
    }

    fun importLichess() {
        viewModelScope.launch {
            val p = profile.value
            val user = p?.lichessUsername.orEmpty()
            if (user.isBlank()) {
                _status.value = "Set Lichess username in Profile"
                return@launch
            }
            runCatching {
                val (games, ratings) = importer.importLichess(user, p?.lichessToken)
                games.forEach {
                    repository.upsertGame(it)
                    enqueue(it)
                }
                repository.addRatings(ratings)
                _status.value = "Imported ${games.size} Lichess games"
            }.onFailure { _status.value = it.message }
        }
    }

    fun shareIntent(game: StoredGame): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, game.pgn)
        }

    private fun enqueue(game: StoredGame) {
        val profile = profile.value ?: return
        val request = OneTimeWorkRequestBuilder<AnalyzeGameWorker>()
            .setInputData(AnalyzeGameWorker.enqueueData(game.id, game.pgn, profile))
            .build()
        workManager.enqueue(request)
    }
}
