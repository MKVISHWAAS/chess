package com.example.chess.domain.engine

import com.example.chess.data.local.LocalChessStore
import com.example.chess.domain.chess.Position
import com.example.chess.domain.model.EngineBackend
import kotlinx.coroutines.flow.first

interface SearchEngineClient {
    suspend fun search(position: Position, depth: Int, moveTimeMs: Long): SearchResult
    suspend fun backendName(): String
}

class CompositeEngine(
    private val stockfish: StockfishEngine,
    private val builtin: BuiltinSearchEngine,
    private val local: LocalChessStore,
) : SearchEngineClient {
    override suspend fun backendName(): String =
        if (preferStockfish()) "stockfish" else "builtin"

    override suspend fun search(position: Position, depth: Int, moveTimeMs: Long): SearchResult {
        if (preferStockfish()) {
            val result = runCatching { stockfish.search(position, depth, moveTimeMs) }.getOrNull()
            if (result != null) return result
        }
        return builtin.search(position, depth, moveTimeMs)
    }

    private suspend fun preferStockfish(): Boolean {
        val prefer = local.snapshot.first().profile?.engineBackend ?: EngineBackend.STOCKFISH
        return prefer == EngineBackend.STOCKFISH && stockfish.isAvailable
    }
}
