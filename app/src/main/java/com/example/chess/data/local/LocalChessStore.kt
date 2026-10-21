package com.example.chess.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.chess.domain.model.ChessSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chessStore by preferencesDataStore("chessos")

@Singleton
class LocalChessStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val dataStore = context.chessStore
    private val key = stringPreferencesKey("snapshot")

    val snapshot: Flow<ChessSnapshot> = dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map ChessSnapshot()
        runCatching { json.decodeFromString(ChessSnapshot.serializer(), raw) }.getOrDefault(ChessSnapshot())
    }

    suspend fun save(snapshot: ChessSnapshot) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(ChessSnapshot.serializer(), snapshot)
        }
    }
}
