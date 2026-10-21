package com.example.chess.data.engine

import android.content.Context
import com.example.chess.domain.chess.ChessRules
import com.example.chess.domain.model.EngineEval
import com.example.chess.domain.model.Fen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class StockfishEngine(
    private val context: Context,
    private val rules: ChessRules,
) : ChessEngine {
    override val name: String = "Stockfish NNUE"
    private val mutex = Mutex()
    private var process: Process? = null
    private var reader: BufferedReader? = null

    fun isAvailable(): Boolean = resolveBinary() != null

    override suspend fun evaluate(fen: Fen, depth: Int, moveTimeMs: Long): EngineEval =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureProcess()
                send("ucinewgame")
                send("position fen ${fen.value}")
                send("go depth ${depth.coerceAtLeast(8)} movetime $moveTimeMs")
                readEval(fen)
            }
        }

    override suspend fun bestMove(fen: Fen, skillLevel: Int, moveTimeMs: Long): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureProcess()
                send("setoption name Skill Level value ${skillLevel.coerceIn(0, 20)}")
                send("ucinewgame")
                send("position fen ${fen.value}")
                send("go movetime $moveTimeMs")
                readEval(fen).bestMoveUci
            }
        }

    private fun ensureProcess() {
        if (process?.isAlive == true) return
        val binary = resolveBinary() ?: error("Stockfish binary not installed")
        if (!binary.canExecute()) binary.setExecutable(true)
        val started = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .directory(binary.parentFile)
            .start()
        process = started
        reader = BufferedReader(InputStreamReader(started.inputStream, StandardCharsets.UTF_8))
        send("uci")
        waitFor("uciok")
        send("isready")
        waitFor("readyok")
        send("setoption name Use NNUE value true")
    }

    private fun send(command: String) {
        val out = process?.outputStream ?: return
        out.write((command + "\n").toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun waitFor(token: String) {
        val input = reader ?: return
        while (true) {
            val line = input.readLine() ?: break
            if (line.contains(token)) break
        }
    }

    private fun readEval(fen: Fen): EngineEval {
        val input = reader ?: error("Engine not started")
        var depth = 0
        var cp: Int? = null
        var mate: Int? = null
        var pv = emptyList<String>()
        var best = ""
        while (true) {
            val line = input.readLine() ?: break
            if (line.startsWith("info ") && line.contains(" pv ")) {
                val parts = line.split(" ")
                depth = parts.intAfter("depth") ?: depth
                if (parts.contains("cp")) cp = parts.intAfter("cp")
                if (parts.contains("mate")) mate = parts.intAfter("mate")
                pv = parts.drop(parts.indexOf("pv") + 1)
            }
            if (line.startsWith("bestmove")) {
                best = line.split(" ").getOrNull(1).orEmpty()
                break
            }
        }
        val pvSan = rules.pvToSan(fen, pv)
        return EngineEval(depth, cp, mate, pv, pvSan, best)
    }

    private fun resolveBinary(): File? {
        val extracted = File(context.filesDir, "stockfish")
        if (extracted.exists()) return extracted
        val assetNames = listOf("stockfish", "stockfish.bin", "engine/stockfish")
        val am = context.assets
        val names = am.list("")?.toList().orEmpty() + am.list("engine")?.map { "engine/$it" }.orEmpty()
        val match = assetNames.firstOrNull { name ->
            names.contains(name) || runCatching { am.open(name).close(); true }.getOrDefault(false)
        } ?: return null
        am.open(match).use { input ->
            extracted.outputStream().use { output -> input.copyTo(output) }
        }
        extracted.setExecutable(true)
        return extracted
    }

    fun shutdown() {
        runCatching {
            send("quit")
            process?.destroy()
        }
        process = null
        reader = null
    }
}

private fun List<String>.intAfter(key: String): Int? {
    val i = indexOf(key)
    if (i < 0) return null
    return getOrNull(i + 1)?.toIntOrNull()
}
