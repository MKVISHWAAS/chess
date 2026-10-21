package com.example.chess.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalJsonStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "chessos_cache.json")
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<LocalSnapshot> = _snapshot

    fun current(): LocalSnapshot = _snapshot.value

    suspend fun update(transform: (LocalSnapshot) -> LocalSnapshot) {
        mutex.withLock {
            val next = transform(_snapshot.value)
            _snapshot.value = next
            file.writeText(json.encodeToString(LocalSnapshot.serializer(), next))
        }
    }

    private fun load(): LocalSnapshot {
        if (!file.exists()) return LocalSnapshot()
        return runCatching {
            json.decodeFromString(LocalSnapshot.serializer(), file.readText())
        }.getOrDefault(LocalSnapshot())
    }
}
