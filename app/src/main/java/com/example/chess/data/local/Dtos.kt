package com.example.chess.data.local

import com.example.chess.domain.model.AnalyzedMove
import com.example.chess.domain.model.DashboardSnapshot
import com.example.chess.domain.model.EngineJob
import com.example.chess.domain.model.Fen
import com.example.chess.domain.model.GameAnalysis
import com.example.chess.domain.model.GameResult
import com.example.chess.domain.model.GameSource
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.MoveClassification
import com.example.chess.domain.model.OpeningStat
import com.example.chess.domain.model.PuzzleProgress
import com.example.chess.domain.model.RatingPoint
import com.example.chess.domain.model.StoredGame
import com.example.chess.domain.model.ThemePreference
import com.example.chess.domain.model.TrainingSession
import com.example.chess.domain.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class LocalSnapshot(
    val profile: UserProfileDto? = null,
    val games: List<GameDto> = emptyList(),
    val analyses: List<AnalysisDto> = emptyList(),
    val mistakes: List<MistakeDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
    val openings: List<OpeningDto> = emptyList(),
    val puzzles: List<PuzzleDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val jobs: List<JobDto> = emptyList(),
    val coachingSummary: String = "",
)

@Serializable
data class UserProfileDto(
    val uid: String,
    val email: String? = null,
    val displayName: String,
    val chessComUsername: String = "",
    val lichessUsername: String = "",
    val lichessToken: String = "",
    val theme: String = ThemePreference.SYSTEM.name,
    val engineDepth: Int = 12,
    val engineMoveTimeMs: Long = 800,
    val skillLevel: Int = 8,
)

@Serializable
data class GameDto(
    val id: String,
    val source: String,
    val white: String,
    val black: String,
    val result: String,
    val pgn: String,
    val opening: String = "",
    val eco: String = "",
    val timeControl: String = "",
    val playedAt: Long = 0,
    val whiteRating: Int? = null,
    val blackRating: Int? = null,
    val analyzed: Boolean = false,
    val acpl: Int? = null,
)

@Serializable
data class MoveDto(
    val ply: Int,
    val san: String,
    val fenBefore: String,
    val fenAfter: String,
    val uci: String,
    val evalCp: Int? = null,
    val bestEvalCp: Int? = null,
    val pv: String = "",
    val classification: String,
    val bestMoveSan: String = "",
)

@Serializable
data class AnalysisDto(
    val gameId: String,
    val moves: List<MoveDto>,
    val acplWhite: Int,
    val acplBlack: Int,
    val accuracyWhite: Double,
    val accuracyBlack: Double,
)

@Serializable
data class MistakeDto(
    val id: String,
    val gameId: String,
    val fen: String,
    val playedSan: String,
    val bestSan: String,
    val classification: String,
    val ply: Int,
    val dueAt: Long,
    val ease: Float = 2.5f,
    val intervalDays: Int = 1,
    val repetitions: Int = 0,
    val coaching: String = "",
)

@Serializable
data class RatingDto(
    val id: String,
    val platform: String,
    val timeClass: String,
    val rating: Int,
    val recordedAt: Long,
)

@Serializable
data class OpeningDto(
    val eco: String,
    val name: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
)

@Serializable
data class PuzzleDto(
    val puzzleId: String,
    val solved: Boolean,
    val attempts: Int,
    val lastAttemptAt: Long,
    val rating: Int = 0,
)

@Serializable
data class SessionDto(
    val id: String,
    val title: String,
    val kind: String,
    val scheduledAt: Long,
    val completed: Boolean = false,
    val notes: String = "",
)

@Serializable
data class JobDto(
    val id: String,
    val gameId: String,
    val status: String,
    val progress: Int,
    val createdAt: Long,
)

fun UserProfile.toDto() = UserProfileDto(
    uid, email, displayName, chessComUsername, lichessUsername, lichessToken,
    theme.name, engineDepth, engineMoveTimeMs, skillLevel,
)

fun UserProfileDto.toDomain() = UserProfile(
    uid, email, displayName, chessComUsername, lichessUsername, lichessToken,
    ThemePreference.valueOf(theme), engineDepth, engineMoveTimeMs, skillLevel,
)

fun StoredGame.toDto() = GameDto(
    id, source.name, white, black, result.name, pgn, opening, eco, timeControl,
    playedAt, whiteRating, blackRating, analyzed, acpl,
)

fun GameDto.toDomain() = StoredGame(
    id, GameSource.valueOf(source), white, black, GameResult.valueOf(result),
    pgn, opening, eco, timeControl, playedAt, whiteRating, blackRating, analyzed, acpl,
)

fun AnalyzedMove.toDto() = MoveDto(
    ply, san, fenBefore.value, fenAfter.value, uci, evalCp, bestEvalCp, pv, classification.name, bestMoveSan,
)

fun MoveDto.toDomain() = AnalyzedMove(
    ply, san, Fen(fenBefore), Fen(fenAfter), uci, evalCp, bestEvalCp, pv,
    MoveClassification.valueOf(classification), bestMoveSan,
)

fun GameAnalysis.toDto() = AnalysisDto(gameId, moves.map { it.toDto() }, acplWhite, acplBlack, accuracyWhite, accuracyBlack)
fun AnalysisDto.toDomain() = GameAnalysis(gameId, moves.map { it.toDomain() }, acplWhite, acplBlack, accuracyWhite, accuracyBlack)

fun Mistake.toDto() = MistakeDto(
    id, gameId, fen.value, playedSan, bestSan, classification.name, ply, dueAt, ease, intervalDays, repetitions, coaching,
)
fun MistakeDto.toDomain() = Mistake(
    id, gameId, Fen(fen), playedSan, bestSan, MoveClassification.valueOf(classification),
    ply, dueAt, ease, intervalDays, repetitions, coaching,
)

fun RatingPoint.toDto() = RatingDto(id, platform.name, timeClass, rating, recordedAt)
fun RatingDto.toDomain() = RatingPoint(id, GameSource.valueOf(platform), timeClass, rating, recordedAt)

fun OpeningStat.toDto() = OpeningDto(eco, name, games, wins, draws, losses)
fun OpeningDto.toDomain() = OpeningStat(eco, name, games, wins, draws, losses)

fun PuzzleProgress.toDto() = PuzzleDto(puzzleId, solved, attempts, lastAttemptAt, rating)
fun PuzzleDto.toDomain() = PuzzleProgress(puzzleId, solved, attempts, lastAttemptAt, rating)

fun TrainingSession.toDto() = SessionDto(id, title, kind, scheduledAt, completed, notes)
fun SessionDto.toDomain() = TrainingSession(id, title, kind, scheduledAt, completed, notes)

fun EngineJob.toDto() = JobDto(id, gameId, status, progress, createdAt)
fun JobDto.toDomain() = EngineJob(id, gameId, status, progress, createdAt)

fun LocalSnapshot.dashboard(): DashboardSnapshot {
    val sortedRatings = ratings.sortedBy { it.recordedAt }
    return DashboardSnapshot(
        gamesPlayed = games.size,
        analyzedGames = games.count { it.analyzed },
        dueMistakes = mistakes.count { it.dueAt <= System.currentTimeMillis() },
        latestRating = sortedRatings.lastOrNull()?.rating,
        ratingDelta = sortedRatings.takeLast(2).let { if (it.size == 2) it[1].rating - it[0].rating else null },
        recentGames = games.sortedByDescending { it.playedAt }.take(8).map { it.toDomain() },
        openingWinRate = openings.firstOrNull()?.let { it.wins.toDouble() / it.games.coerceAtLeast(1) },
        coachingSummary = coachingSummary,
    )
}
