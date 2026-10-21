package com.example.chess.ui.navigation

sealed class Dest(val route: String) {
    data object Login : Dest("login")
    data object Home : Dest("home")
    data object Dashboard : Dest("dashboard")
    data object Games : Dest("games")
    data object Train : Dest("train")
    data object Play : Dest("play")
    data object Profile : Dest("profile")
    data object Analysis : Dest("analysis/{gameId}") {
        fun of(gameId: String) = "analysis/$gameId"
    }
    data object FenLab : Dest("fen")
    data object Explorer : Dest("explorer")
    data object Ratings : Dest("ratings")
    data object Puzzles : Dest("puzzles")
    data object Endgames : Dest("endgames")
    data object Planner : Dest("planner")
    data object Mistakes : Dest("mistakes")
}
