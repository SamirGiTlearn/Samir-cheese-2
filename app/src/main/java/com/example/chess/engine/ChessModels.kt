package com.example.chess.engine

enum class PieceColor {
    WHITE,
    BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE

    fun fenChar(): Char = if (this == WHITE) 'w' else 'b'

    companion object {
        fun fromString(raw: String?): PieceColor =
            if (raw.equals("BLACK", ignoreCase = true) || raw.equals("b", ignoreCase = true)) BLACK else WHITE
    }
}

enum class PieceType(val fenSymbol: Char, val sanLetter: String, val materialValue: Int) {
    KING('k', "K", 0),
    QUEEN('q', "Q", 9),
    ROOK('r', "R", 5),
    BISHOP('b', "B", 3),
    KNIGHT('n', "N", 3),
    PAWN('p', "", 1);

    companion object {
        fun fromUciChar(c: Char): PieceType? = when (c.lowercaseChar()) {
            'q' -> QUEEN
            'r' -> ROOK
            'b' -> BISHOP
            'n' -> KNIGHT
            else -> null
        }

        fun fromFenChar(c: Char): PieceType? = when (c.lowercaseChar()) {
            'k' -> KING
            'q' -> QUEEN
            'r' -> ROOK
            'b' -> BISHOP
            'n' -> KNIGHT
            'p' -> PAWN
            else -> null
        }
    }
}

data class Piece(
    val type: PieceType,
    val color: PieceColor
) {
    fun toFenChar(): Char =
        if (color == PieceColor.WHITE) type.fenSymbol.uppercaseChar() else type.fenSymbol.lowercaseChar()

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromFenChar(c) ?: return null
            val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
            return Piece(type, color)
        }
    }
}

/**
 * Board square where file is 0..7 (a..h) and rank is 0..7 (1..8).
 */
data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7 && rank in 0..7) { "Square out of bounds: file=$file, rank=$rank" }
    }

    val index: Int get() = rank * 8 + file

    fun toAlgebraic(): String = "${'a' + file}${'1' + rank}"

    fun isLightSquare(): Boolean = (file + rank) % 2 != 0

    companion object {
        fun fromAlgebraic(notation: String): Square? {
            if (notation.length != 2) return null
            val f = notation[0].lowercaseChar() - 'a'
            val r = notation[1] - '1'
            if (f !in 0..7 || r !in 0..7) return null
            return Square(f, r)
        }

        fun fromIndex(index: Int): Square = Square(index % 8, index / 8)
    }
}

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    fun toFenString(): String {
        val sb = StringBuilder()
        if (whiteKingSide) sb.append('K')
        if (whiteQueenSide) sb.append('Q')
        if (blackKingSide) sb.append('k')
        if (blackQueenSide) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        fun fromFenString(fen: String): CastlingRights {
            if (fen == "-") return CastlingRights(false, false, false, false)
            return CastlingRights(
                whiteKingSide = 'K' in fen,
                whiteQueenSide = 'Q' in fen,
                blackKingSide = 'k' in fen,
                blackQueenSide = 'q' in fen
            )
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
    val isEnPassant: Boolean = false,
    val isCapture: Boolean = false
) {
    fun toUci(): String {
        val promo = promotion?.fenSymbol?.lowercaseChar()?.toString().orEmpty()
        return "${from.toAlgebraic()}${to.toAlgebraic()}$promo"
    }
}

enum class GameTermination(val label: String) {
    NONE(""),
    CHECKMATE("Checkmate"),
    STALEMATE("Stalemate"),
    THREEFOLD_REPETITION("Threefold Repetition"),
    FIFTY_MOVE_RULE("Fifty-Move Rule"),
    INSUFFICIENT_MATERIAL("Insufficient Material"),
    RESIGNATION("Resignation"),
    TIMEOUT("Time Out"),
    DRAW_AGREEMENT("Draw by Agreement")
}

sealed interface GameStatus {
    data class Ongoing(val inCheck: Boolean) : GameStatus
    data class Finished(val winner: PieceColor?, val termination: GameTermination) : GameStatus
}

data class ChessPosition(
    val board: List<Piece?>,
    val turn: PieceColor,
    val castlingRights: CastlingRights,
    val enPassantTarget: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    fun pieceAt(square: Square): Piece? = board[square.index]
    fun pieceAt(file: Int, rank: Int): Piece? =
        if (file in 0..7 && rank in 0..7) board[rank * 8 + file] else null
}

data class MoveRecord(
    val moveNumber: Int,
    val color: PieceColor,
    val uci: String,
    val san: String,
    val fenAfter: String
)

data class CapturedSummary(
    val capturedByWhite: List<PieceType>,
    val capturedByBlack: List<PieceType>,
    val whiteAdvantage: Int
)
