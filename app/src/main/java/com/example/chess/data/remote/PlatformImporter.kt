package com.example.chess.data.remote

import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.chess.OpeningBook
import com.example.chess.domain.chess.newId
import com.example.chess.domain.model.GameSource
import com.example.chess.domain.model.RatingPoint
import com.example.chess.domain.model.StoredGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformImporter @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val rules: ChessRules,
) {
    suspend fun importChessCom(username: String, limit: Int = 12): Pair<List<StoredGame>, List<RatingPoint>> =
        withContext(Dispatchers.IO) {
            val archives = get("https://api.chess.com/pub/player/${username.trim()}/games/archives")
            val urls = archives["archives"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().takeLast(2)
            val games = urls.flatMap { url ->
                get(url)["games"]?.jsonArray.orEmpty().mapNotNull { el ->
                    val obj = el.jsonObject
                    val pgn = obj["pgn"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    parsedGame(pgn, GameSource.CHESSCOM)
                }
            }.takeLast(limit)

            val stats = get("https://api.chess.com/pub/player/${username.trim()}/stats")
            val ratings = listOf("chess_blitz", "chess_rapid", "chess_bullet", "chess_daily").mapNotNull { key ->
                val rating = stats[key]?.jsonObject?.get("last")?.jsonObject?.get("rating")?.jsonPrimitive?.intOrNull
                    ?: return@mapNotNull null
                RatingPoint(newId(), GameSource.CHESSCOM, key.removePrefix("chess_"), rating, System.currentTimeMillis())
            }
            games to ratings
        }

    suspend fun importLichess(username: String, token: String?, limit: Int = 12): Pair<List<StoredGame>, List<RatingPoint>> =
        withContext(Dispatchers.IO) {
            val pgn = getText(
                "https://lichess.org/api/games/user/${username.trim()}?max=$limit&pgnInJson=false&clocks=false",
                token,
            )
            val games = splitPgn(pgn).map { parsedGame(it, GameSource.LICHESS) }
            val user = get("https://lichess.org/api/user/${username.trim()}", token)
            val perfs = user["perfs"]?.jsonObject
            val ratings = listOf("blitz", "rapid", "bullet", "classical").mapNotNull { key ->
                val rating = perfs?.get(key)?.jsonObject?.get("rating")?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                RatingPoint(newId(), GameSource.LICHESS, key, rating, System.currentTimeMillis())
            }
            games to ratings
        }

    suspend fun lichessOpening(fen: String): String = withContext(Dispatchers.IO) {
        val obj = get("https://explorer.lichess.ovh/lichess?fen=${java.net.URLEncoder.encode(fen, Charsets.UTF_8)}&moves=8")
        val moves = obj["moves"]?.jsonArray.orEmpty().take(6).joinToString("\n") { el ->
            val m = el.jsonObject
            val san = m["san"]?.jsonPrimitive?.content.orEmpty()
            val white = m["white"]?.jsonPrimitive?.intOrNull ?: 0
            val draws = m["draws"]?.jsonPrimitive?.intOrNull ?: 0
            val black = m["black"]?.jsonPrimitive?.intOrNull ?: 0
            "$san  W$white D$draws B$black"
        }
        val opening = obj["opening"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        buildString {
            if (opening != null) appendLine(opening)
            append(moves.ifBlank { "No explorer hits for this position." })
        }
    }

    suspend fun dailyPuzzle(): LichessPuzzle = withContext(Dispatchers.IO) {
        val obj = get("https://lichess.org/api/puzzle/daily")
        parsePuzzle(obj)
    }

    suspend fun nextPuzzle(): LichessPuzzle = withContext(Dispatchers.IO) {
        val obj = get("https://lichess.org/api/puzzle/next")
        parsePuzzle(obj)
    }

    private fun parsePuzzle(obj: JsonObject): LichessPuzzle {
        val puzzle = obj["puzzle"]?.jsonObject ?: obj
        val game = obj["game"]?.jsonObject
        val pgn = game?.get("pgn")?.jsonPrimitive?.contentOrNull.orEmpty()
        val solution = puzzle["solution"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val id = puzzle["id"]?.jsonPrimitive?.content ?: newId()
        val rating = puzzle["rating"]?.jsonPrimitive?.intOrNull ?: 1500
        val fen = puzzle["fen"]?.jsonPrimitive?.contentOrNull
            ?: runCatching {
                val board = rules.startBoard()
                ChessRules.extractSans(pgn).forEach { rules.applySanInPlace(board, it) }
                board.fen
            }.getOrDefault(com.example.chess.domain.chess.START_FEN)
        return LichessPuzzle(id, fen, solution, rating, pgn)
    }

    private fun parsedGame(pgn: String, source: GameSource): StoredGame {
        val parsed = rules.parsePgn(pgn)
        return StoredGame(
            id = newId(),
            source = source,
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
    }

    private fun splitPgn(blob: String): List<String> {
        val chunks = blob.split(Regex("(?=\\[Event )")).map { it.trim() }.filter { it.isNotBlank() }
        return chunks.ifEmpty { listOf(blob).filter { it.isNotBlank() } }
    }

    private fun get(url: String, token: String? = null): JsonObject {
        val body = getText(url, token)
        return json.parseToJsonElement(body).jsonObject
    }

    private fun getText(url: String, token: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ChessOS/1.0")
            .header("Accept", "application/json, application/x-chess-pgn")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body.string()
        }
    }
}

data class LichessPuzzle(
    val id: String,
    val fen: String,
    val solutionUci: List<String>,
    val rating: Int,
    val pgn: String,
)
