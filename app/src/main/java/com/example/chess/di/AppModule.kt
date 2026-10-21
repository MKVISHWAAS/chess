package com.example.chess.di

import android.content.Context
import androidx.work.WorkManager
import com.example.chess.data.local.LocalChessStore
import com.example.chess.domain.analysis.GameAnalyzer
import com.example.chess.domain.engine.BuiltinSearchEngine
import com.example.chess.domain.engine.CompositeEngine
import com.example.chess.domain.engine.SearchEngineClient
import com.example.chess.domain.engine.StockfishEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun http(json: Json): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.NONE }
        defaultRequest { userAgent("ChessOS/1.0 (Android)") }
    }

    @Provides
    @Singleton
    fun builtin(): BuiltinSearchEngine = BuiltinSearchEngine()

    @Provides
    @Singleton
    fun engine(
        stockfish: StockfishEngine,
        builtin: BuiltinSearchEngine,
        local: LocalChessStore,
    ): SearchEngineClient = CompositeEngine(stockfish, builtin, local)

    @Provides
    @Singleton
    fun analyzer(engine: SearchEngineClient): GameAnalyzer = GameAnalyzer(engine)

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
