package com.example.chess.domain.endgame

data class EndgameDrill(
    val id: String,
    val title: String,
    val fen: String,
    val goal: String,
)

object EndgameCatalog {
    val drills = listOf(
        EndgameDrill("kqvk", "King and queen vs king", "8/8/8/4k3/8/8/8/4K2Q w - - 0 1", "Checkmate with queen"),
        EndgameDrill("krvk", "King and rook vs king", "8/8/8/4k3/8/8/8/4K2R w - - 0 1", "Checkmate with rook"),
        EndgameDrill("kpk", "King and pawn vs king", "8/8/8/4k3/8/8/4P3/4K3 w - - 0 1", "Promote the pawn"),
        EndgameDrill("lucena", "Lucena position", "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1", "Bridge and promote"),
        EndgameDrill("philidor", "Philidor defence", "8/8/8/4k3/8/4K3/4R3/4r3 b - - 0 1", "Hold the draw"),
        EndgameDrill("opposite", "Opposite bishops", "8/8/3k4/8/2b5/8/4B3/4K3 w - - 0 1", "Hold or convert"),
        EndgameDrill("two-bishops", "Two bishops mate", "8/8/8/4k3/8/8/8/2B1KB2 w - - 0 1", "Mate with bishops"),
        EndgameDrill("bnk", "Bishop and knight mate", "8/8/8/4k3/8/8/8/2B1KN2 w - - 0 1", "Mate with B+N"),
    )
}
