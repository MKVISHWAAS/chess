package com.example.chess.domain.srs

import com.example.chess.domain.model.Mistake

object SpacedRepetition {
    fun review(mistake: Mistake, remembered: Boolean, now: Long = System.currentTimeMillis()): Mistake {
        val quality = if (remembered) 4 else 1
        var ease = (mistake.ease + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f)))
            .coerceAtLeast(1.3f)
        val reps = if (remembered) mistake.repetitions + 1 else 0
        val interval = when {
            !remembered -> 1
            reps == 1 -> 1
            reps == 2 -> 3
            else -> (mistake.intervalDays * ease).toInt().coerceAtLeast(1)
        }
        return mistake.copy(
            ease = ease,
            repetitions = reps,
            intervalDays = interval,
            dueAt = now + interval * 24L * 60L * 60L * 1000L,
        )
    }
}
