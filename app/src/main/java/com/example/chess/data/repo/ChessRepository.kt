package com.example.chess.data.repo

import com.example.chess.data.ai.GeminiCoach
import com.example.chess.data.firestore.FirestoreChessStore
import com.example.chess.data.local.LocalChessStore
import com.example.chess.data.remote.ChessComApi
import com.example.chess.data.remote.LichessApi
import com.example.chess.data.srs.SpacedRepetition
import com.example.chess.domain.analysis.GameAnalyzer
import com.example.chess.domain.chess.OpeningBook
import com.example.chess.domain.chess.Pgn
import com.example.chess.domain.engine.SearchEngineClient
import com.example.chess.domain.model.ChessSnapshot
import com.example.chess.domain.model.CoachNote
import com.example.chess.domain.model.DashboardStats
import com.example.chess.domain.model.EngineJob
import com.example.chess.domain.model.GameRecord
import com.example.chess.domain.model.GameSource
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.OpeningStat
import com.example.chess.domain.model.PuzzleProgress
import com.example.chess.domain.model.RatingPoint
import com.example.chess.domain.model.TrainingSession
import com.example.chess.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChessRepository @Inject constructor(
    private val local: LocalChessStore,
    private val remote: FirestoreChessStore,
    private val chessCom: ChessComApi,
    private val lichess: LichessApi,
    private val analyzer: GameAnalyzer,
    private val engine: SearchEngineClient,
    private val coach: GeminiCoach,
) {
    val snapshot: Flow<ChessSnapshot> = local.snapshot
    val dashboard: Flow<DashboardStats> = snapshot.map { snap ->
        val now = System.currentTimeMillis()
        DashboardStats(
            games = snap.games.size,
            mistakesDue = snap.mistakes.count { it.dueAt <= now },
            avgAcpl = snap.analyses.map { it.acpl }.average().takeIf { snap.analyses.isNotEmpty() } ?: 0.0,
            lastRating = snap.ratings.maxByOrNull { it.recordedAt }?.rating,
            sessionsDue = snap.sessions.count { !it.completed && it.scheduledAt <= now },
            recentGames = snap.games.sortedByDescending { it.playedAt }.take(5),
            ratingSeries = snap.ratings.sortedBy { it.recordedAt }.takeLast(24),
        )
    }

    suspend fun current(): ChessSnapshot = local.snapshot.first()

    suspend fun saveProfile(profile: UserProfile) {
        update { it.copy(profile = profile) }
        runCatching { remote.pushProfile(profile) }
    }

    suspend fun upsertGame(game: GameRecord) {
        update { snap -> snap.copy(games = snap.games.filterNot { it.id == game.id } + game) }
        runCatching { remote.upsertGame(game) }
    }

    suspend fun importPgn(text: String, source: GameSource, platform: String = ""): List<GameRecord> {
        val parsed = Pgn.parseMany(text)
        val records = parsed.map { game ->
            GameRecord(
                id = UUID.randomUUID().toString(),
                source = source,
                white = game.white,
                black = game.black,
                result = game.result,
                pgn = game.raw,
                opening = game.opening,
                platform = platform,
            )
        }
        records.forEach { upsertGame(it) }
        refreshOpenings()
        return records
    }

    suspend fun importChessCom(username: String): Int {
        val games = chessCom.recentGames(username)
        var count = 0
        games.forEach { dto ->
            if (dto.pgn.isNotBlank()) {
                importPgn(dto.pgn, GameSource.CHESS_COM, "Chess.com")
                count++
            }
        }
        val ratings = chessCom.stats(username)
        ratings.forEach { (time, rating) ->
            addRating(RatingPoint(UUID.randomUUID().toString(), "Chess.com", time, rating, System.currentTimeMillis()))
        }
        return count
    }

    suspend fun importLichess(username: String, token: String? = null): Int {
        val pgn = lichess.recentGamesPgn(username, token)
        val games = importPgn(pgn, GameSource.LICHESS, "Lichess")
        lichess.ratings(username).forEach { (time, rating) ->
            addRating(RatingPoint(UUID.randomUUID().toString(), "Lichess", time, rating, System.currentTimeMillis()))
        }
        return games.size
    }

    suspend fun addRating(point: RatingPoint) {
        update { it.copy(ratings = it.ratings + point) }
        runCatching { remote.upsertRating(point) }
    }

    suspend fun analyzeGame(gameId: String) {
        val snap = current()
        val game = snap.games.first { it.id == gameId }
        val profile = snap.profile
        val job = EngineJob(UUID.randomUUID().toString(), gameId, "running")
        update { it.copy(jobs = it.jobs + job) }
        runCatching { remote.upsertJob(job) }
        val pgnGame = Pgn.parse(game.pgn.ifBlank { Pgn.export(mapOf("White" to game.white, "Black" to game.black), emptyList(), game.result) })
        val (analysis, mistakes) = analyzer.analyzePgn(
            gameId,
            pgnGame,
            profile?.engineDepth ?: 6,
            (profile?.engineMoveTimeMs ?: 400).toLong(),
        )
        val updatedGame = game.copy(analyzed = true, acpl = analysis.acpl, opening = game.opening.ifBlank { OpeningBook.detect(pgnGame.moves) })
        update { current ->
            current.copy(
                games = current.games.map { if (it.id == gameId) updatedGame else it },
                analyses = current.analyses.filterNot { it.gameId == gameId } + analysis,
                mistakes = current.mistakes + mistakes,
                jobs = current.jobs.map { if (it.id == job.id) it.copy(status = "done") else it },
            )
        }
        runCatching { remote.upsertGame(updatedGame) }
        runCatching { remote.upsertAnalysis(analysis) }
        mistakes.forEach { runCatching { remote.upsertMistake(it) } }
        refreshOpenings()
    }

    suspend fun analyzeFen(fen: String) = engine.search(
        com.example.chess.domain.chess.Fen.parse(fen),
        current().profile?.engineDepth ?: 8,
        (current().profile?.engineMoveTimeMs ?: 800).toLong(),
    )

    suspend fun reviewMistake(id: String, quality: Int) {
        val updated = current().mistakes.map { if (it.id == id) SpacedRepetition.review(it, quality) else it }
        update { it.copy(mistakes = updated) }
        updated.firstOrNull { it.id == id }?.let { runCatching { remote.upsertMistake(it) } }
    }

    suspend fun savePuzzle(progress: PuzzleProgress) {
        update { it.copy(puzzles = it.puzzles.filterNot { p -> p.puzzleId == progress.puzzleId } + progress) }
        runCatching { remote.upsertPuzzle(progress) }
    }

    suspend fun saveSession(session: TrainingSession) {
        update { it.copy(sessions = it.sessions.filterNot { s -> s.id == session.id } + session) }
        runCatching { remote.upsertSession(session) }
    }

    suspend fun pullCloud() {
        val cloud = runCatching { remote.pull() }.getOrNull() ?: return
        val localSnap = current()
        update {
            ChessSnapshot(
                profile = cloud.profile ?: localSnap.profile,
                games = mergeBy(localSnap.games, cloud.games) { it.id },
                analyses = mergeBy(localSnap.analyses, cloud.analyses) { it.gameId },
                mistakes = mergeBy(localSnap.mistakes, cloud.mistakes) { it.id },
                ratings = mergeBy(localSnap.ratings, cloud.ratings) { it.id },
                openings = mergeBy(localSnap.openings, cloud.openings) { it.name },
                puzzles = mergeBy(localSnap.puzzles, cloud.puzzles) { it.puzzleId },
                sessions = mergeBy(localSnap.sessions, cloud.sessions) { it.id },
                jobs = mergeBy(localSnap.jobs, cloud.jobs) { it.id },
                coachNotes = mergeBy(localSnap.coachNotes, cloud.coachNotes) { it.id },
            )
        }
    }

    suspend fun explainMistake(mistake: Mistake): String {
        val text = coach.explainBlunder(mistake.fen, mistake.san, mistake.bestPv, mistake.kind.name)
        val updated = mistake.copy(explanation = text)
        update { it.copy(mistakes = it.mistakes.map { m -> if (m.id == mistake.id) updated else m }) }
        runCatching { remote.upsertMistake(updated) }
        return text
    }

    suspend fun coachSummary(game: GameRecord): String {
        val analysis = current().analyses.firstOrNull { it.gameId == game.id }
        val body = coach.summarizeGame(game.pgn, analysis?.acpl ?: game.acpl, game.opening)
        val note = CoachNote(UUID.randomUUID().toString(), "Game ${game.white} vs ${game.black}", body)
        update { it.copy(coachNotes = it.coachNotes + note) }
        runCatching { remote.upsertCoach(note) }
        return body
    }

    suspend fun recommendTraining(): String {
        val snap = current()
        val themes = snap.mistakes.groupBy { it.kind }.map { "${it.key} x${it.value.size}" }
        val body = coach.trainingRecommendations(themes, snap.analyses.map { it.acpl }.average().takeIf { snap.analyses.isNotEmpty() } ?: 0.0)
        val note = CoachNote(UUID.randomUUID().toString(), "Training plan", body)
        update { it.copy(coachNotes = it.coachNotes + note) }
        runCatching { remote.upsertCoach(note) }
        return body
    }

    suspend fun dailyPuzzle() = lichess.dailyPuzzle()
    suspend fun nextPuzzle() = lichess.nextPuzzle()
    suspend fun explorer(fen: String) = lichess.explorer(fen)

    fun searchGames(query: String, snapshot: ChessSnapshot): List<GameRecord> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return snapshot.games.sortedByDescending { it.playedAt }
        return snapshot.games.filter {
            it.white.lowercase().contains(q) ||
                it.black.lowercase().contains(q) ||
                it.opening.lowercase().contains(q) ||
                it.result.contains(q) ||
                it.platform.lowercase().contains(q)
        }.sortedByDescending { it.playedAt }
    }

    private suspend fun refreshOpenings() {
        val games = current().games
        val grouped = games.groupBy { it.opening.ifBlank { "Unknown" } }.map { (name, list) ->
            OpeningStat(
                name = name,
                games = list.size,
                wins = list.count { it.result == "1-0" },
                draws = list.count { it.result == "1/2-1/2" },
                losses = list.count { it.result == "0-1" },
            )
        }
        update { it.copy(openings = grouped) }
        grouped.forEach { runCatching { remote.upsertOpening(it) } }
    }

    private suspend fun update(transform: (ChessSnapshot) -> ChessSnapshot) {
        local.save(transform(current()))
    }

    private fun <T> mergeBy(a: List<T>, b: List<T>, key: (T) -> String): List<T> {
        val map = LinkedHashMap<String, T>()
        a.forEach { map[key(it)] = it }
        b.forEach { map[key(it)] = it }
        return map.values.toList()
    }
}
