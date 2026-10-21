package com.example.chess.domain.chess

data class PgnGame(
    val headers: Map<String, String>,
    val moves: List<String>,
    val raw: String,
) {
    val white: String get() = headers["White"] ?: "White"
    val black: String get() = headers["Black"] ?: "Black"
    val result: String get() = headers["Result"] ?: "*"
    val opening: String get() = headers["Opening"] ?: headers["ECO"] ?: OpeningBook.detect(moves)
}

object Pgn {
    fun parseMany(text: String): List<PgnGame> {
        val games = mutableListOf<PgnGame>()
        val chunks = text.trim().split(Regex("(?=\\[Event )"))
        for (chunk in chunks) {
            if (chunk.isBlank()) continue
            runCatching { parse(chunk) }.onSuccess(games::add)
        }
        return games.ifEmpty { listOf(parse(text)) }
    }

    fun parse(text: String): PgnGame {
        val headers = linkedMapOf<String, String>()
        val headerRegex = Regex("""\[(\w+)\s+"(.*)"]""")
        headerRegex.findAll(text).forEach { match ->
            headers[match.groupValues[1]] = match.groupValues[2]
        }
        val movetext = text.substringAfterLast("]\n", text)
            .replace(Regex("\\{[^}]*}"), " ")
            .replace(Regex(";.*"), " ")
            .replace(Regex("\\d+\\.+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val tokens = movetext.split(" ").filter { token ->
            token.isNotBlank() && token !in setOf("1-0", "0-1", "1/2-1/2", "*")
        }
        return PgnGame(headers, tokens, text.trim())
    }

    fun replay(game: PgnGame): List<Position> {
        var position = Fen.parse(game.headers["FEN"] ?: Fen.START)
        val path = mutableListOf(position)
        for (san in game.moves) {
            val move = San.parse(position, san)
            position = position.apply(move)
            path += position
        }
        return path
    }

    fun export(
        headers: Map<String, String>,
        sans: List<String>,
        result: String = "*",
    ): String = buildString {
        headers.forEach { (k, v) -> appendLine("""[$k "$v"]""") }
        if (headers.isNotEmpty()) appendLine()
        sans.forEachIndexed { index, san ->
            if (index % 2 == 0) append("${index / 2 + 1}. ")
            append(san)
            append(' ')
        }
        append(result)
    }
}

object OpeningBook {
    private val names = listOf(
        listOf("e4", "e5", "Nf3", "Nc6", "Bb5") to "Ruy Lopez",
        listOf("e4", "e5", "Nf3", "Nc6", "Bc4") to "Italian Game",
        listOf("e4", "c5") to "Sicilian Defence",
        listOf("e4", "e6") to "French Defence",
        listOf("e4", "c6") to "Caro-Kann Defence",
        listOf("d4", "d5", "c4") to "Queen's Gambit",
        listOf("d4", "Nf6", "c4", "g6") to "King's Indian Defence",
        listOf("d4", "Nf6", "c4", "e6") to "Nimzo/Queen's Indian",
        listOf("e4", "e5", "Nf3", "Nf6") to "Petrov Defence",
        listOf("c4") to "English Opening",
        listOf("Nf3") to "Reti Opening",
        listOf("e4") to "King's Pawn",
        listOf("d4") to "Queen's Pawn",
    )

    fun detect(moves: List<String>): String {
        val cleaned = moves.map { it.trim().removeSuffix("+").removeSuffix("#") }
        return names.firstOrNull { (prefix, _) ->
            cleaned.size >= prefix.size && cleaned.take(prefix.size) == prefix
        }?.second ?: "Unknown opening"
    }
}
