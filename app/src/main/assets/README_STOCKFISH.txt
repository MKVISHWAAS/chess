Place a Stockfish Android PIE binary named `stockfish` in this assets folder
(and optionally `nnue.bin`) to enable on-device NNUE evaluation.

Until then ChessOS uses the builtin Kotlin alpha-beta search for analysis and play.
The UCI wrapper will copy the binary to app files and execute it when present.
