package com.example.chess.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChessComGameDto(
    val pgn: String = "",
    val url: String = "",
    @SerialName("end_time") val endTime: Long = 0,
    val white: ChessComPlayerDto = ChessComPlayerDto(),
    val black: ChessComPlayerDto = ChessComPlayerDto(),
)

@Serializable
data class ChessComPlayerDto(
    val username: String = "",
    val result: String = "",
    val rating: Int = 0,
)

@Serializable
data class LichessPuzzleDto(
    val puzzle: LichessPuzzleInner = LichessPuzzleInner(),
    val game: LichessPuzzleGame = LichessPuzzleGame(),
)

@Serializable
data class LichessPuzzleInner(
    val id: String = "",
    val rating: Int = 1200,
    val solution: List<String> = emptyList(),
    val initialPly: Int = 0,
)

@Serializable
data class LichessPuzzleGame(
    val pgn: String = "",
)

@Serializable
data class OpeningExplorerMove(
    val san: String,
    val white: Int,
    val draws: Int,
    val black: Int,
    val averageRating: Int = 0,
)

@Singleton
class ChessComApi @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun stats(username: String): List<Pair<String, Int>> {
        val text = client.get("https://api.chess.com/pub/player/${username.trim()}/stats").bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        return listOf("chess_rapid", "chess_blitz", "chess_bullet", "chess_daily").mapNotNull { key ->
            val rating = root[key]?.jsonObject
                ?.get("last")?.jsonObject
                ?.get("rating")?.jsonPrimitive?.intOrNull
            rating?.let { key.removePrefix("chess_") to it }
        }
    }

    suspend fun recentGames(username: String): List<ChessComGameDto> {
        val archives = json.parseToJsonElement(
            client.get("https://api.chess.com/pub/player/${username.trim()}/games/archives").bodyAsText(),
        ).jsonObject
        val urls = archives["archives"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val last = urls.lastOrNull() ?: return emptyList()
        val payload = json.parseToJsonElement(client.get(last).bodyAsText()).jsonObject
        return payload["games"]?.jsonArray?.map {
            json.decodeFromJsonElement(ChessComGameDto.serializer(), it)
        }.orEmpty().reversed()
    }
}

@Singleton
class LichessApi @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun ratings(username: String): List<Pair<String, Int>> {
        val text = client.get("https://lichess.org/api/user/${username.trim()}").bodyAsText()
        val perfs = json.parseToJsonElement(text).jsonObject["perfs"]?.jsonObject ?: return emptyList()
        return listOf("blitz", "rapid", "bullet", "classical").mapNotNull { key ->
            perfs[key]?.jsonObject?.get("rating")?.jsonPrimitive?.intOrNull?.let { key to it }
        }
    }

    suspend fun recentGamesPgn(username: String, token: String? = null): String {
        return client.get("https://lichess.org/api/games/user/${username.trim()}") {
            url {
                parameters.append("max", "20")
                parameters.append("pgnInJson", "false")
                parameters.append("moves", "true")
                parameters.append("opening", "true")
            }
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            header("Accept", "application/x-chess-pgn")
        }.bodyAsText()
    }

    suspend fun dailyPuzzle(): LichessPuzzleDto =
        json.decodeFromString(client.get("https://lichess.org/api/puzzle/daily").bodyAsText())

    suspend fun nextPuzzle(): LichessPuzzleDto =
        json.decodeFromString(client.get("https://lichess.org/api/puzzle/next").bodyAsText())

    suspend fun explorer(fen: String): List<OpeningExplorerMove> {
        val text = client.get("https://lichess.org/api/explorer/lichess") {
            url { parameters.append("fen", fen) }
        }.bodyAsText()
        val moves = json.parseToJsonElement(text).jsonObject["moves"]?.jsonArray ?: return emptyList()
        return moves.map {
            val obj = it.jsonObject
            OpeningExplorerMove(
                san = obj["san"]?.jsonPrimitive?.contentOrNull ?: "",
                white = obj["white"]?.jsonPrimitive?.intOrNull ?: 0,
                draws = obj["draws"]?.jsonPrimitive?.intOrNull ?: 0,
                black = obj["black"]?.jsonPrimitive?.intOrNull ?: 0,
                averageRating = obj["averageRating"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }
}
