package com.example.chess.domain.engine

import android.content.Context
import com.example.chess.domain.chess.Position
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockfishEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isAvailable: Boolean
        get() = binaryFile()?.exists() == true

    suspend fun search(position: Position, depth: Int, moveTimeMs: Long): SearchResult = withContext(Dispatchers.IO) {
        val binary = binaryFile() ?: error("Stockfish binary not installed")
        val process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()
        try {
            val writer = process.outputStream.bufferedWriter()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            fun send(line: String) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
            send("uci")
            waitFor(reader, "uciok")
            send("isready")
            waitFor(reader, "readyok")
            val nnue = File(context.filesDir, "nnue.bin")
            if (nnue.exists()) send("setoption name EvalFile value ${nnue.absolutePath}")
            send("ucinewgame")
            send("position fen ${position.fen()}")
            send("go depth $depth movetime $moveTimeMs")
            var eval = 0
            var pv = emptyList<String>()
            var best: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("info") && "score cp" in line) {
                    eval = line.substringAfter("score cp ").substringBefore(" ").toIntOrNull() ?: eval
                    if (" pv " in line) {
                        pv = line.substringAfter(" pv ").trim().split(" ")
                    }
                }
                if (line.startsWith("bestmove")) {
                    best = line.substringAfter("bestmove ").substringBefore(" ").takeIf { it != "(none)" }
                    break
                }
            }
            send("quit")
            SearchResult(best, eval, pv, depth, "stockfish")
        } finally {
            process.destroyForcibly()
            process.waitFor(400, TimeUnit.MILLISECONDS)
        }
    }

    fun installFromAssetsIfPresent() {
        val dest = File(context.filesDir, "stockfish")
        if (dest.exists()) return
        runCatching {
            context.assets.open("stockfish").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.setExecutable(true)
        }
        runCatching {
            context.assets.open("nnue.bin").use { input ->
                File(context.filesDir, "nnue.bin").outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun binaryFile(): File? {
        val dest = File(context.filesDir, "stockfish")
        return dest.takeIf { it.exists() && it.canExecute() }
    }

    private fun waitFor(reader: BufferedReader, token: String) {
        while (true) {
            val line = reader.readLine() ?: return
            if (token in line) return
        }
    }
}
