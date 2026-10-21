package com.example.chess.domain.analysis

import com.example.chess.domain.model.AnalyzedMove
import com.example.chess.domain.model.MoveClassification
import kotlin.math.abs

object MoveClassifier {
    fun classify(bestCp: Int, playedCp: Int, ply: Int, isBook: Boolean): MoveClassification {
        if (isBook && ply < 12) return MoveClassification.BOOK
        val loss = (bestCp - playedCp).coerceAtLeast(0)
        return when {
            loss >= 300 -> MoveClassification.BLUNDER
            loss >= 150 -> MoveClassification.MISTAKE
            loss >= 60 -> MoveClassification.INACCURACY
            loss <= 10 -> MoveClassification.BEST
            loss <= 25 -> MoveClassification.EXCELLENT
            else -> MoveClassification.GOOD
        }
    }

    fun accuracyFromLosses(losses: List<Int>): Double {
        if (losses.isEmpty()) return 100.0
        val mean = losses.map { abs(it).coerceAtMost(1000) }.average()
        return (100.0 - mean / 12.0).coerceIn(0.0, 100.0)
    }

    fun acpl(losses: List<Int>): Int {
        if (losses.isEmpty()) return 0
        return losses.map { abs(it).coerceAtMost(1000) }.average().toInt()
    }

    fun isMistake(classification: MoveClassification): Boolean =
        classification == MoveClassification.INACCURACY ||
            classification == MoveClassification.MISTAKE ||
            classification == MoveClassification.BLUNDER
}

fun AnalyzedMove.centipawnLoss(): Int {
    val best = bestEvalCp ?: return 0
    val played = evalCp ?: return 0
    return (best - played).coerceAtLeast(0)
}
