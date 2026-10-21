package com.example.chess.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chess.data.repo.ChessRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AnalyzeGameWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ChessRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val gameId = inputData.getString(KEY_GAME_ID) ?: return Result.failure()
        return runCatching {
            repository.analyzeGame(gameId)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val KEY_GAME_ID = "gameId"
    }
}
