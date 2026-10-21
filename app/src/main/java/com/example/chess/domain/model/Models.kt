package com.example.chess.domain.model

import kotlinx.serialization.Serializable

enum class GameSource { CHESS_COM, LICHESS, MANUAL, PGN_UPLOAD }
enum class MistakeKind { BLUNDER, MISTAKE, INACCURACY }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class EngineBackend { STOCKFISH, BUILTIN }

@Serializable
data class UserProfile(
    val uid: String,
    val email: String = "",
    val displayName: String = "",
    val chessComUsername: String = "",
    val lichessUsername: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val engineDepth: Int = 8,
    val engineMoveTimeMs: Int = 800,
    val engineBackend: EngineBackend = EngineBackend.STOCKFISH,
    val skillLevel: Int = 8,
)

@Serializable
data class GameRecord(
    val id: String,
    val source: GameSource = GameSource.MANUAL,
    val white: String = "White",
    val black: String = "Black",
    val result: String = "*",
    val pgn: String = "",
    val fen: String = "",
    val opening: String = "",
    val playedAt: Long = System.currentTimeMillis(),
    val analyzed: Boolean = false,
    val acpl: Double = 0.0,
    val platform: String = "",
)

@Serializable
data class AnalyzedMove(
    val ply: Int,
    val san: String,
    val fenBefore: String,
    val fenAfter: String,
    val evalCp: Int,
    val bestEvalCp: Int,
    val pv: String,
    val classification: MistakeKind? = null,
)

@Serializable
data class GameAnalysis(
    val gameId: String,
    val moves: List<AnalyzedMove> = emptyList(),
    val acpl: Double = 0.0,
    val summary: String = "",
)

@Serializable
data class Mistake(
    val id: String,
    val gameId: String,
    val fen: String,
    val san: String,
    val kind: MistakeKind,
    val evalDropCp: Int,
    val bestPv: String,
    val dueAt: Long = System.currentTimeMillis(),
    val intervalDays: Int = 1,
    val ease: Double = 2.5,
    val reviews: Int = 0,
    val explanation: String = "",
)

@Serializable
data class RatingPoint(
    val id: String,
    val platform: String,
    val timeClass: String,
    val rating: Int,
    val recordedAt: Long,
)

@Serializable
data class OpeningStat(
    val name: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
)

@Serializable
data class PuzzleProgress(
    val puzzleId: String,
    val fen: String,
    val rating: Int = 1200,
    val solved: Boolean = false,
    val attempts: Int = 0,
    val lastPlayedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TrainingSession(
    val id: String,
    val title: String,
    val focus: String,
    val scheduledAt: Long,
    val completed: Boolean = false,
    val notes: String = "",
)

@Serializable
data class EngineJob(
    val id: String,
    val gameId: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val message: String = "",
)

@Serializable
data class CoachNote(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ChessSnapshot(
    val profile: UserProfile? = null,
    val games: List<GameRecord> = emptyList(),
    val analyses: List<GameAnalysis> = emptyList(),
    val mistakes: List<Mistake> = emptyList(),
    val ratings: List<RatingPoint> = emptyList(),
    val openings: List<OpeningStat> = emptyList(),
    val puzzles: List<PuzzleProgress> = emptyList(),
    val sessions: List<TrainingSession> = emptyList(),
    val jobs: List<EngineJob> = emptyList(),
    val coachNotes: List<CoachNote> = emptyList(),
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val profile: UserProfile) : AuthState
}

data class DashboardStats(
    val games: Int = 0,
    val mistakesDue: Int = 0,
    val avgAcpl: Double = 0.0,
    val lastRating: Int? = null,
    val sessionsDue: Int = 0,
    val recentGames: List<GameRecord> = emptyList(),
    val ratingSeries: List<RatingPoint> = emptyList(),
)
