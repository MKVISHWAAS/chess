package com.example.chess.data.ai

import com.example.chess.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiCoach @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    val enabled: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    suspend fun explainBlunder(fen: String, played: String, bestPv: String, kind: String): String =
        generate(
            """
            You are a chess coach. Explain this $kind in 3 short sentences.
            FEN: $fen
            Played move: $played
            Engine principal variation: $bestPv
            Do not give a numeric evaluation. Teach the idea.
            """.trimIndent(),
        )

    suspend fun trainingRecommendations(themes: List<String>, acpl: Double): String =
        generate(
            """
            Create a 5-day chess training plan for a player whose recent mistake themes are ${themes.joinToString()}.
            Average centipawn loss: $acpl.
            Use short bullet points. No engine evals.
            """.trimIndent(),
        )

    suspend fun summarizeGame(pgn: String, acpl: Double, opening: String): String =
        generate(
            """
            Summarize this chess game for the player in 4 sentences.
            Opening: $opening
            ACPL: $acpl
            PGN: ${pgn.take(2500)}
            Focus on plans and turning points, not raw eval.
            """.trimIndent(),
        )

    private suspend fun generate(prompt: String): String {
        if (!enabled) {
            return "Add GEMINI_API_KEY to local.properties to enable ChessOS coaching."
        }
        val payload = """
            {"contents":[{"parts":[{"text":${json.encodeToString(kotlinx.serialization.serializer<String>(), prompt)}}]}]}
        """.trimIndent()
        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}",
        ) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            setBody(payload)
        }.bodyAsText()
        return runCatching {
            json.parseToJsonElement(response)
                .jsonObject["candidates"]!!
                .jsonArray.first()
                .jsonObject["content"]!!
                .jsonObject["parts"]!!
                .jsonArray.first()
                .jsonObject["text"]!!
                .jsonPrimitive.content
        }.getOrElse { "Coach unavailable: ${it.message}" }
    }
}
