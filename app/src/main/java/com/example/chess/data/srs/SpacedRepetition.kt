package com.example.chess.data.srs

import com.example.chess.domain.model.Mistake
import kotlin.math.roundToInt

object SpacedRepetition {
    fun review(mistake: Mistake, quality: Int): Mistake {
        val q = quality.coerceIn(0, 5)
        val ease = (mistake.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).coerceAtLeast(1.3)
        val interval = when {
            q < 3 -> 1
            mistake.reviews == 0 -> 1
            mistake.reviews == 1 -> 3
            else -> (mistake.intervalDays * ease).roundToInt().coerceAtLeast(1)
        }
        return mistake.copy(
            ease = ease,
            intervalDays = interval,
            reviews = mistake.reviews + 1,
            dueAt = System.currentTimeMillis() + interval * 24L * 60 * 60 * 1000,
        )
    }
}
