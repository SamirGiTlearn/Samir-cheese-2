package com.example.chess.engine

import kotlin.math.abs

/**
 * Pure Kotlin official FIDE chess rules engine shared by Offline mode, Online mode,
 * Firestore transaction validation, and PGN/FEN Game Replay.
 */
object ChessEngine {

    const val INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun initialPosition(): ChessPosition = fromFen(INITIAL_FEN)

    fun fromFen(fen: String): ChessPosition {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size >= 4) { "Invalid FEN: $fen" }

        val board = MutableList<Piece?>(64) { null }
        val ranks = parts[0].split('/')
        require(ranks.size == 8) { "Invalid FEN board ranks: ${parts[0]}" }

        for (fenRankIdx in 0 until 8) {
            val rank = 7 - fenRankIdx
            var file = 0
            for (ch in ranks[fenRankIdx]) {
                if (ch.isDigit()) {
                    file += ch.digitToInt()
                } else {
                    val piece = Piece.fromFenChar(ch)
                        ?: throw IllegalArgumentException("Invalid FEN piece char: $ch")
                    require(file in 0..7) { "FEN rank overflow at $fenRankIdx" }
                    board[rank * 8 + file] = piece
                    file++
                }
            }
            require(file == 8) { "FEN rank incomplete at $fenRankIdx" }
        }

        val turn = if (parts[1] == "b") PieceColor.BLACK else PieceColor.WHITE
        val castling = CastlingRights.fromFenString(parts[2])
        val enPassant = if (parts[3] == "-") null else Square.fromAlgebraic(parts[3])
        val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmove = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        return ChessPosition(
            board = board,
            turn = turn,
            castlingRights = castling,
            enPassantTarget = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove
        )
    }

    fun toFen(position: ChessPosition): String {
        val boardStr = buildString {
            for (rank in 7 downTo 0) {
                var empty = 0
                for (file in 0..7) {
                    val piece = position.pieceAt(file, rank)
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece.toFenChar())
                    }
                }
                if (empty > 0) append(empty)
                if (rank > 0) append('/')
            }
        }
        val epStr = position.enPassantTarget?.toAlgebraic() ?: "-"
        return "$boardStr ${position.turn.fenChar()} ${position.castlingRights.toFenString()} $epStr ${position.halfmoveClock} ${position.fullmoveNumber}"
    }

    /**
     * Repetition key ignores halfmove/fullmove counters and only keeps en passant target
     * if a legal en passant capture actually exists.
     */
    fun repetitionKey(position: ChessPosition): String {
        val fenParts = toFen(position).split(' ')
        val hasLegalEp = position.enPassantTarget != null &&
            legalMoves(position).any { it.isEnPassant }
        val epPart = if (hasLegalEp) fenParts[3] else "-"
        return "${fenParts[0]} ${fenParts[1]} ${fenParts[2]} $epPart"
    }

    fun findKing(position: ChessPosition, color: PieceColor): Square? {
        for (i in 0 until 64) {
            val p = position.board[i]
            if (p != null && p.color == color && p.type == PieceType.KING) {
                return Square.fromIndex(i)
            }
        }
        return null
    }

    fun isSquareAttacked(position: ChessPosition, target: Square, byColor: PieceColor): Boolean {
        // Pawn attacks
        val pawnDir = if (byColor == PieceColor.WHITE) -1 else 1
        val pawnRank = target.rank + pawnDir
        if (pawnRank in 0..7) {
            for (df in listOf(-1, 1)) {
                val pf = target.file + df
                if (pf in 0..7) {
                    val p = position.pieceAt(pf, pawnRank)
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knight attacks
        val knightOffsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((df, dr) in knightOffsets) {
            val f = target.file + df
            val r = target.rank + dr
            val p = position.pieceAt(f, r)
            if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
        }

        // King attacks
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val p = position.pieceAt(target.file + df, target.rank + dr)
                if (p != null && p.color == byColor && p.type == PieceType.KING) return true
            }
        }

        // Orthogonal rays (Rook / Queen)
        val orthoDirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((df, dr) in orthoDirs) {
            var f = target.file + df
            var r = target.rank + dr
            while (f in 0..7 && r in 0..7) {
                val p = position.pieceAt(f, r)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }

        // Diagonal rays (Bishop / Queen)
        val diagDirs = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((df, dr) in diagDirs) {
            var f = target.file + df
            var r = target.rank + dr
            while (f in 0..7 && r in 0..7) {
                val p = position.pieceAt(f, r)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += df
                r += dr
            }
        }

        return false
    }

    fun isInCheck(position: ChessPosition, color: PieceColor): Boolean {
        val kingSq = findKing(position, color) ?: return false
        return isSquareAttacked(position, kingSq, color.opposite())
    }

    fun legalMovesFrom(position: ChessPosition, from: Square): List<ChessMove> =
        legalMoves(position).filter { it.from == from }

    fun legalMoves(position: ChessPosition): List<ChessMove> {
        val pseudo = generatePseudoLegalMoves(position)
        val color = position.turn
        return pseudo.filter { move ->
            val next = applyMoveUnchecked(position, move)
            !isInCheck(next, color)
        }
    }

    private fun generatePseudoLegalMoves(position: ChessPosition): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val color = position.turn

        for (index in 0 until 64) {
            val piece = position.board[index] ?: continue
            if (piece.color != color) continue
            val from = Square.fromIndex(index)

            when (piece.type) {
                PieceType.PAWN -> addPawnMoves(position, from, color, moves)
                PieceType.KNIGHT -> addJumpMoves(
                    position, from, color,
                    listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1),
                    moves
                )
                PieceType.BISHOP -> addSlideMoves(
                    position, from, color,
                    listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1),
                    moves
                )
                PieceType.ROOK -> addSlideMoves(
                    position, from, color,
                    listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1),
                    moves
                )
                PieceType.QUEEN -> addSlideMoves(
                    position, from, color,
                    listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1),
                    moves
                )
                PieceType.KING -> addKingMoves(position, from, color, moves)
            }
        }
        return moves
    }

    private fun addPawnMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        out: MutableList<ChessMove>
    ) {
        val dir = if (color == PieceColor.WHITE) 1 else -1
        val startRank = if (color == PieceColor.WHITE) 1 else 6
        val promoRank = if (color == PieceColor.WHITE) 7 else 0
        val promoTypes = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        val oneForwardRank = from.rank + dir
        if (oneForwardRank in 0..7 && position.pieceAt(from.file, oneForwardRank) == null) {
            val to = Square(from.file, oneForwardRank)
            if (oneForwardRank == promoRank) {
                promoTypes.forEach { pt -> out.add(ChessMove(from, to, promotion = pt)) }
            } else {
                out.add(ChessMove(from, to))
                val twoForwardRank = from.rank + 2 * dir
                if (from.rank == startRank && position.pieceAt(from.file, twoForwardRank) == null) {
                    out.add(ChessMove(from, Square(from.file, twoForwardRank)))
                }
            }
        }

        for (df in listOf(-1, 1)) {
            val tf = from.file + df
            val tr = from.rank + dir
            if (tf !in 0..7 || tr !in 0..7) continue
            val targetSq = Square(tf, tr)
            val targetPiece = position.pieceAt(targetSq)
            if (targetPiece != null && targetPiece.color != color) {
                if (tr == promoRank) {
                    promoTypes.forEach { pt ->
                        out.add(ChessMove(from, targetSq, promotion = pt, isCapture = true))
                    }
                } else {
                    out.add(ChessMove(from, targetSq, isCapture = true))
                }
            } else if (position.enPassantTarget == targetSq) {
                out.add(ChessMove(from, targetSq, isEnPassant = true, isCapture = true))
            }
        }
    }

    private fun addJumpMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        offsets: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        for ((df, dr) in offsets) {
            val tf = from.file + df
            val tr = from.rank + dr
            if (tf !in 0..7 || tr !in 0..7) continue
            val targetPiece = position.pieceAt(tf, tr)
            if (targetPiece == null) {
                out.add(ChessMove(from, Square(tf, tr)))
            } else if (targetPiece.color != color) {
                out.add(ChessMove(from, Square(tf, tr), isCapture = true))
            }
        }
    }

    private fun addSlideMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        dirs: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        for ((df, dr) in dirs) {
            var tf = from.file + df
            var tr = from.rank + dr
            while (tf in 0..7 && tr in 0..7) {
                val targetPiece = position.pieceAt(tf, tr)
                if (targetPiece == null) {
                    out.add(ChessMove(from, Square(tf, tr)))
                } else {
                    if (targetPiece.color != color) {
                        out.add(ChessMove(from, Square(tf, tr), isCapture = true))
                    }
                    break
                }
                tf += df
                tr += dr
            }
        }
    }

    private fun addKingMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        out: MutableList<ChessMove>
    ) {
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val tf = from.file + df
                val tr = from.rank + dr
                if (tf !in 0..7 || tr !in 0..7) continue
                val targetPiece = position.pieceAt(tf, tr)
                if (targetPiece == null) {
                    out.add(ChessMove(from, Square(tf, tr)))
                } else if (targetPiece.color != color) {
                    out.add(ChessMove(from, Square(tf, tr), isCapture = true))
                }
            }
        }

        // Castling
        val homeRank = if (color == PieceColor.WHITE) 0 else 7
        if (from.file == 4 && from.rank == homeRank && !isInCheck(position, color)) {
            val opp = color.opposite()
            val canKingSide = if (color == PieceColor.WHITE) position.castlingRights.whiteKingSide
            else position.castlingRights.blackKingSide
            if (canKingSide) {
                val rook = position.pieceAt(7, homeRank)
                if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                    position.pieceAt(5, homeRank) == null &&
                    position.pieceAt(6, homeRank) == null &&
                    !isSquareAttacked(position, Square(5, homeRank), opp) &&
                    !isSquareAttacked(position, Square(6, homeRank), opp)
                ) {
                    out.add(ChessMove(from, Square(6, homeRank), isCastleKingSide = true))
                }
            }

            val canQueenSide = if (color == PieceColor.WHITE) position.castlingRights.whiteQueenSide
            else position.castlingRights.blackQueenSide
            if (canQueenSide) {
                val rook = position.pieceAt(0, homeRank)
                if (rook != null && rook.color == color && rook.type == PieceType.ROOK &&
                    position.pieceAt(3, homeRank) == null &&
                    position.pieceAt(2, homeRank) == null &&
                    position.pieceAt(1, homeRank) == null &&
                    !isSquareAttacked(position, Square(3, homeRank), opp) &&
                    !isSquareAttacked(position, Square(2, homeRank), opp)
                ) {
                    out.add(ChessMove(from, Square(2, homeRank), isCastleQueenSide = true))
                }
            }
        }
    }

    /**
     * Validates and applies a move from [from] -> [to] (with optional [promotion]).
     * Returns null if the move is illegal.
     */
    fun tryApplyMove(
        position: ChessPosition,
        from: Square,
        to: Square,
        promotion: PieceType? = null
    ): ChessPosition? {
        val legal = findMatchingLegalMove(position, from, to, promotion) ?: return null
        return applyMoveUnchecked(position, legal)
    }

    fun tryApplyUciMove(position: ChessPosition, uci: String): Pair<ChessMove, ChessPosition>? {
        if (uci.length < 4) return null
        val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
        val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
        val promo = if (uci.length >= 5) PieceType.fromUciChar(uci[4]) else null
        val legal = findMatchingLegalMove(position, from, to, promo) ?: return null
        return legal to applyMoveUnchecked(position, legal)
    }

    fun findMatchingLegalMove(
        position: ChessPosition,
        from: Square,
        to: Square,
        promotion: PieceType? = null
    ): ChessMove? {
        val candidates = legalMovesFrom(position, from).filter { it.to == to }
        if (candidates.isEmpty()) return null
        return if (promotion != null) {
            candidates.firstOrNull { it.promotion == promotion }
        } else {
            candidates.firstOrNull { it.promotion == null } ?: candidates.firstOrNull()
        }
    }

    fun needsPromotionSelection(position: ChessPosition, from: Square, to: Square): Boolean =
        legalMovesFrom(position, from).any { it.to == to && it.promotion != null }

    fun applyMoveUnchecked(position: ChessPosition, move: ChessMove): ChessPosition {
        val movingPiece = position.pieceAt(move.from)
            ?: throw IllegalStateException("No piece on ${move.from.toAlgebraic()}")

        val nextBoard = position.board.toMutableList()
        val capturedPiece = nextBoard[move.to.index]

        nextBoard[move.from.index] = null

        // En passant capture removes enemy pawn on same rank as `from` and file as `to`
        if (move.isEnPassant) {
            val capturedPawnSq = Square(move.to.file, move.from.rank)
            nextBoard[capturedPawnSq.index] = null
        }

        // Castling moves the rook as well
        if (move.isCastleKingSide) {
            val rookFrom = Square(7, move.from.rank)
            val rookTo = Square(5, move.from.rank)
            nextBoard[rookTo.index] = nextBoard[rookFrom.index]
            nextBoard[rookFrom.index] = null
        } else if (move.isCastleQueenSide) {
            val rookFrom = Square(0, move.from.rank)
            val rookTo = Square(3, move.from.rank)
            nextBoard[rookTo.index] = nextBoard[rookFrom.index]
            nextBoard[rookFrom.index] = null
        }

        // Place moved or promoted piece
        nextBoard[move.to.index] = if (move.promotion != null) {
            Piece(move.promotion, movingPiece.color)
        } else {
            movingPiece
        }

        // Update castling rights
        var rights = position.castlingRights
        if (movingPiece.type == PieceType.KING) {
            rights = if (movingPiece.color == PieceColor.WHITE) {
                rights.copy(whiteKingSide = false, whiteQueenSide = false)
            } else {
                rights.copy(blackKingSide = false, blackQueenSide = false)
            }
        }
        // Moving or capturing on rook home squares revokes castling rights
        fun revokeRookRights(sq: Square) {
            when (sq) {
                Square(7, 0) -> rights = rights.copy(whiteKingSide = false)
                Square(0, 0) -> rights = rights.copy(whiteQueenSide = false)
                Square(7, 7) -> rights = rights.copy(blackKingSide = false)
                Square(0, 7) -> rights = rights.copy(blackQueenSide = false)
            }
        }
        revokeRookRights(move.from)
        revokeRookRights(move.to)

        // Update en passant target
        val nextEp = if (movingPiece.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else {
            null
        }

        val isPawnMoveOrCapture = movingPiece.type == PieceType.PAWN || capturedPiece != null || move.isEnPassant
        val nextHalfmove = if (isPawnMoveOrCapture) 0 else position.halfmoveClock + 1
        val nextFullmove = if (position.turn == PieceColor.BLACK) position.fullmoveNumber + 1 else position.fullmoveNumber

        return ChessPosition(
            board = nextBoard,
            turn = position.turn.opposite(),
            castlingRights = rights,
            enPassantTarget = nextEp,
            halfmoveClock = nextHalfmove,
            fullmoveNumber = nextFullmove
        )
    }

    fun computeSan(
        before: ChessPosition,
        move: ChessMove,
        after: ChessPosition = applyMoveUnchecked(before, move)
    ): String {
        val movingPiece = before.pieceAt(move.from) ?: return move.toUci()
        val baseSan = when {
            move.isCastleKingSide -> "O-O"
            move.isCastleQueenSide -> "O-O-O"
            movingPiece.type == PieceType.PAWN -> {
                val capturePrefix = if (move.isCapture) "${'a' + move.from.file}x" else ""
                val promoSuffix = if (move.promotion != null) "=${move.promotion.sanLetter}" else ""
                "$capturePrefix${move.to.toAlgebraic()}$promoSuffix"
            }
            else -> {
                val sameTypePeers = legalMoves(before).filter { other ->
                    other.from != move.from &&
                        other.to == move.to &&
                        before.pieceAt(other.from)?.type == movingPiece.type
                }
                val disambig = if (sameTypePeers.isEmpty()) {
                    ""
                } else {
                    val shareFile = sameTypePeers.any { it.from.file == move.from.file }
                    val shareRank = sameTypePeers.any { it.from.rank == move.from.rank }
                    when {
                        !shareFile -> "${'a' + move.from.file}"
                        !shareRank -> "${'1' + move.from.rank}"
                        else -> move.from.toAlgebraic()
                    }
                }
                val captureChar = if (move.isCapture) "x" else ""
                "${movingPiece.type.sanLetter}$disambig$captureChar${move.to.toAlgebraic()}"
            }
        }

        val inCheck = isInCheck(after, after.turn)
        val hasLegalReplies = legalMoves(after).isNotEmpty()
        val checkSuffix = when {
            inCheck && !hasLegalReplies -> "#"
            inCheck -> "+"
            else -> ""
        }
        return "$baseSan$checkSuffix"
    }

    fun evaluateStatus(
        position: ChessPosition,
        repetitionKeys: List<String> = emptyList()
    ): GameStatus {
        val moves = legalMoves(position)
        val inCheck = isInCheck(position, position.turn)

        if (moves.isEmpty()) {
            return if (inCheck) {
                GameStatus.Finished(winner = position.turn.opposite(), termination = GameTermination.CHECKMATE)
            } else {
                GameStatus.Finished(winner = null, termination = GameTermination.STALEMATE)
            }
        }

        if (position.halfmoveClock >= 100) {
            return GameStatus.Finished(winner = null, termination = GameTermination.FIFTY_MOVE_RULE)
        }

        val currentKey = repetitionKey(position)
        val occurrences = repetitionKeys.count { it == currentKey }
        if (occurrences >= 3) {
            return GameStatus.Finished(winner = null, termination = GameTermination.THREEFOLD_REPETITION)
        }

        if (isInsufficientMaterial(position)) {
            return GameStatus.Finished(winner = null, termination = GameTermination.INSUFFICIENT_MATERIAL)
        }

        return GameStatus.Ongoing(inCheck = inCheck)
    }

    fun isInsufficientMaterial(position: ChessPosition): Boolean {
        val whitePieces = mutableListOf<Pair<PieceType, Square>>()
        val blackPieces = mutableListOf<Pair<PieceType, Square>>()

        for (i in 0 until 64) {
            val p = position.board[i] ?: continue
            if (p.type == PieceType.KING) continue
            val sq = Square.fromIndex(i)
            if (p.color == PieceColor.WHITE) whitePieces.add(p.type to sq)
            else blackPieces.add(p.type to sq)
        }

        if (whitePieces.isEmpty() && blackPieces.isEmpty()) return true
        if (whitePieces.isEmpty() && blackPieces.size == 1 &&
            (blackPieces[0].first == PieceType.BISHOP || blackPieces[0].first == PieceType.KNIGHT)
        ) return true
        if (blackPieces.isEmpty() && whitePieces.size == 1 &&
            (whitePieces[0].first == PieceType.BISHOP || whitePieces[0].first == PieceType.KNIGHT)
        ) return true
        if (whitePieces.size == 1 && blackPieces.size == 1 &&
            whitePieces[0].first == PieceType.BISHOP && blackPieces[0].first == PieceType.BISHOP
        ) {
            return whitePieces[0].second.isLightSquare() == blackPieces[0].second.isLightSquare()
        }
        return false
    }

    fun computeCapturedSummary(position: ChessPosition): CapturedSummary {
        val startingCounts = mapOf(
            PieceType.QUEEN to 1,
            PieceType.ROOK to 2,
            PieceType.BISHOP to 2,
            PieceType.KNIGHT to 2,
            PieceType.PAWN to 8
        )
        val remainingWhite = mutableMapOf<PieceType, Int>()
        val remainingBlack = mutableMapOf<PieceType, Int>()
        for (p in position.board) {
            if (p == null || p.type == PieceType.KING) continue
            val map = if (p.color == PieceColor.WHITE) remainingWhite else remainingBlack
            map[p.type] = (map[p.type] ?: 0) + 1
        }

        val capturedByWhite = mutableListOf<PieceType>()
        val capturedByBlack = mutableListOf<PieceType>()
        var whiteScore = 0
        var blackScore = 0

        for ((type, startCount) in startingCounts) {
            val wRem = remainingWhite[type] ?: 0
            val bRem = remainingBlack[type] ?: 0
            whiteScore += wRem * type.materialValue
            blackScore += bRem * type.materialValue

            repeat((startCount - bRem).coerceAtLeast(0)) { capturedByWhite.add(type) }
            repeat((startCount - wRem).coerceAtLeast(0)) { capturedByBlack.add(type) }
        }

        return CapturedSummary(
            capturedByWhite = capturedByWhite,
            capturedByBlack = capturedByBlack,
            whiteAdvantage = whiteScore - blackScore
        )
    }

    fun toPgn(
        whiteName: String,
        blackName: String,
        resultString: String,
        sanMoves: List<String>,
        eventName: String = "Chess Master - Made by Samir"
    ): String = buildString {
        appendLine("[Event \"$eventName\"]")
        appendLine("[White \"$whiteName\"]")
        appendLine("[Black \"$blackName\"]")
        appendLine("[Result \"$resultString\"]")
        appendLine()
        for (i in sanMoves.indices step 2) {
            val moveNum = (i / 2) + 1
            append("$moveNum. ${sanMoves[i]}")
            if (i + 1 < sanMoves.size) {
                append(" ${sanMoves[i + 1]} ")
            } else {
                append(" ")
            }
        }
        append(resultString)
    }
}
