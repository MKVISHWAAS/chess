package com.example.chess.domain.training

data class EndgameDrill(
    val id: String,
    val title: String,
    val fen: String,
    val goal: String,
)

object EndgameCatalog {
    val drills = listOf(
        EndgameDrill("kqk", "King and Queen vs King", "8/8/8/4k3/8/8/8/4K2Q w - - 0 1", "Force mate with queen and king."),
        EndgameDrill("krk", "King and Rook vs King", "8/8/8/4k3/8/8/8/4K2R w - - 0 1", "Box the king and mate."),
        EndgameDrill("opp", "Opposition", "8/8/8/4k3/8/4K3/8/8 w - - 0 1", "Take the opposition and outflank."),
        EndgameDrill("lucena", "Lucena position", "1K1k4/1P6/8/8/8/8/r7/5R2 w - - 0 1", "Build a bridge and queen."),
        EndgameDrill("philidor", "Philidor", "4k3/4p3/8/8/8/8/4P3/4K3 w - - 0 1", "Hold the draw with the short side."),
        EndgameDrill("bnk", "Bishop and Knight mate", "8/8/8/8/8/2k5/8/2K1NB2 w - - 0 1", "Drive the king to a bishop-color corner."),
    )
}
