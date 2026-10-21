package com.example.chess.data.firestore

import com.example.chess.domain.model.ChessSnapshot
import com.example.chess.domain.model.CoachNote
import com.example.chess.domain.model.EngineJob
import com.example.chess.domain.model.GameAnalysis
import com.example.chess.domain.model.GameRecord
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.OpeningStat
import com.example.chess.domain.model.PuzzleProgress
import com.example.chess.domain.model.RatingPoint
import com.example.chess.domain.model.TrainingSession
import com.example.chess.domain.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChessStore @Inject constructor(
    private val json: Json,
) {
    val configured: Boolean
        get() = runCatching { FirebaseApp.getInstance(); true }.getOrDefault(false)

    private fun db(): FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private fun uid(): String? = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    private fun userDoc() = uid()?.let { db()?.collection("users")?.document(it) }

    suspend fun pull(): ChessSnapshot? {
        val root = userDoc() ?: return null
        val profileSnap = root.get().await()
        val profile = profileSnap.data?.let(::decodeProfile)
        return ChessSnapshot(
            profile = profile,
            games = col(root, "games", GameRecord.serializer()),
            analyses = col(root, "analysis", GameAnalysis.serializer()),
            mistakes = col(root, "mistakes", Mistake.serializer()),
            ratings = col(root, "ratings", RatingPoint.serializer()),
            openings = col(root, "opening_stats", OpeningStat.serializer()),
            puzzles = col(root, "puzzle_progress", PuzzleProgress.serializer()),
            sessions = col(root, "training_sessions", TrainingSession.serializer()),
            jobs = col(root, "engine_jobs", EngineJob.serializer()),
            coachNotes = col(root, "coach_notes", CoachNote.serializer()),
        )
    }

    suspend fun pushProfile(profile: UserProfile) {
        userDoc()?.set(toMap(json.encodeToJsonElement(UserProfile.serializer(), profile)), SetOptions.merge())?.await()
    }

    suspend fun upsertGame(game: GameRecord) = put("games", game.id, json.encodeToJsonElement(GameRecord.serializer(), game))
    suspend fun upsertAnalysis(analysis: GameAnalysis) = put("analysis", analysis.gameId, json.encodeToJsonElement(GameAnalysis.serializer(), analysis))
    suspend fun upsertMistake(mistake: Mistake) = put("mistakes", mistake.id, json.encodeToJsonElement(Mistake.serializer(), mistake))
    suspend fun upsertRating(point: RatingPoint) = put("ratings", point.id, json.encodeToJsonElement(RatingPoint.serializer(), point))
    suspend fun upsertOpening(stat: OpeningStat) = put("opening_stats", stat.name, json.encodeToJsonElement(OpeningStat.serializer(), stat))
    suspend fun upsertPuzzle(progress: PuzzleProgress) = put("puzzle_progress", progress.puzzleId, json.encodeToJsonElement(PuzzleProgress.serializer(), progress))
    suspend fun upsertSession(session: TrainingSession) = put("training_sessions", session.id, json.encodeToJsonElement(TrainingSession.serializer(), session))
    suspend fun upsertJob(job: EngineJob) = put("engine_jobs", job.id, json.encodeToJsonElement(EngineJob.serializer(), job))
    suspend fun upsertCoach(note: CoachNote) = put("coach_notes", note.id, json.encodeToJsonElement(CoachNote.serializer(), note))

    private suspend fun <T> col(
        root: com.google.firebase.firestore.DocumentReference,
        name: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        return root.collection(name).get().await().documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            runCatching { json.decodeFromJsonElement(serializer, toJson(data)) }.getOrNull()
        }
    }

    private suspend fun put(collection: String, id: String, element: JsonElement) {
        userDoc()?.collection(collection)?.document(id)?.set(toMap(element), SetOptions.merge())?.await()
    }

    private fun decodeProfile(data: Map<String, Any>): UserProfile =
        json.decodeFromJsonElement(UserProfile.serializer(), toJson(data))

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })
        is List<*> -> JsonArray(value.map { toJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun toMap(element: JsonElement): Map<String, Any?> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapValues { unwrap(it.value) }
    }

    private fun unwrap(value: JsonElement): Any? = when (value) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.content == "true" || value.content == "false" -> value.content.toBoolean()
            value.content.toLongOrNull() != null -> value.content.toLong()
            value.content.toDoubleOrNull() != null -> value.content.toDouble()
            else -> value.content
        }
        is JsonArray -> value.map { unwrap(it) }
        is JsonObject -> value.mapValues { unwrap(it.value) }
    }
}
