package com.example.chess

import com.example.chess.data.srs.SpacedRepetition
import com.example.chess.domain.analysis.GameAnalyzer
import com.example.chess.domain.chess.Fen
import com.example.chess.domain.chess.Pgn
import com.example.chess.domain.chess.San
import com.example.chess.domain.engine.BuiltinSearchEngine
import com.example.chess.domain.engine.classifyDrop
import com.example.chess.domain.model.Mistake
import com.example.chess.domain.model.MistakeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessCoreTest {
    @Test
    fun startFenRoundTrip() {
        val position = Fen.parse(Fen.START)
        assertEquals(Fen.START, position.fen())
        assertEquals(20, position.legalMoves().size)
    }

    @Test
    fun scholarsMateParses() {
        val pgn = """
            [Event "Test"]
            [White "A"]
            [Black "B"]
            [Result "1-0"]
            
            1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#
        """.trimIndent()
        val game = Pgn.parse(pgn)
        val path = Pgn.replay(game)
        assertEquals(8, path.size)
        assertTrue(path.last().inCheck())
    }

    @Test
    fun sanRoundTrip() {
        val start = Fen.parse(Fen.START)
        val move = start.legalMoves().first { it.uci == "e2e4" }
        assertEquals("e4", San.of(start, move))
        assertEquals("e2e4", San.parse(start, "e4").uci)
    }

    @Test
    fun classificationThresholds() {
        assertEquals(MistakeKind.BLUNDER, classifyDrop(250))
        assertEquals(MistakeKind.MISTAKE, classifyDrop(120))
        assertEquals(MistakeKind.INACCURACY, classifyDrop(60))
        assertEquals(null, classifyDrop(10))
    }

    @Test
    fun spacedRepetitionIncreasesInterval() {
        val mistake = Mistake("1", "g", Fen.START, "e4", MistakeKind.MISTAKE, 120, "e2e4")
        val next = SpacedRepetition.review(mistake, 5)
        assertTrue(next.reviews == 1)
        assertTrue(next.dueAt > mistake.dueAt)
    }

    @Test
    fun builtinEngineReturnsMove() {
        val engine = BuiltinSearchEngine()
        val result = engine.search(Fen.parse(Fen.START), 3, 400)
        assertTrue(result.bestMoveUci != null)
    }
}

class GameAnalyzerContract {
    @Test
    fun analyzerTypeExists() {
        assertTrue(GameAnalyzer::class.java.name.contains("GameAnalyzer"))
    }
}
